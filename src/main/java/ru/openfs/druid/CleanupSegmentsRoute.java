/**
 * Copyright 2020 openfs.ru
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.openfs.druid;

import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static org.apache.camel.builder.PredicateBuilder.and;

@ApplicationScoped
public class CleanupSegmentsRoute extends RouteBuilder {

    @ConfigProperty(name = "cleanup.segments.period")
    String period;

    @ConfigProperty(name = "cleanup.coordinator.url")
    String druid;

    @ConfigProperty(name = "cleanup.segments.enable", defaultValue = "true")
    boolean isEnable;

    @Override
    public void configure() throws Exception {

        // cleanup segments 
        fromF("timer:segments?period=%s", period).autoStartup(isEnable).id("CleanupSegments")
            .log("Starting cleanup unused segments")
            
            // select dropped segements from metastore
            .setBody(constant("select max(\"end\"),min(\"start\"),count(id),datasource"
                + " from druid_segments where used=false group by datasource"))
            .to("jdbc:datasource")

            // process payload
            .split(body())
                .filter(simple("${body.get('count')} > 0"))
                    .setBody()
                        .simple("{\"type\":\"kill\",\"dataSource\":\"${body.get('datasource')}\",\"interval\":\"${body.get('min')}/${body.get('max')}\"}")
                    
                    // post task
                    .log("try to post kill task:${body}")
                    .removeHeaders("(?:Camel.*)|(?:job.*)|(?:.*Job.*)")
                    .setHeader("Content-Type", constant("application/json"))
                    .toF("netty-http:%s/druid/indexer/v1/task?httpMethod=POST", druid)
                    
                    // parse id
                    .setHeader("task").jsonpath("$.task")
                    .log("commit task:${header.task}")
                .end()
            .end()
            .log("Cleanup unused segments finished");

    }
}
