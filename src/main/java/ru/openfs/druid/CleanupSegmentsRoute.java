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

    @ConfigProperty(name = "period")
    String period;

    @ConfigProperty(name = "coordinator.url")
    String druid;

    @Override
    public void configure() throws Exception {
        fromF("timer:cleanup?period=%s", period).id("CleanupSegments")
            .log("Starting cleanup dropped segmenets")
            
            .step("getTask")
                // select dropped segements from metastore
                .setBody(constant("select max(\"end\"),min(\"start\"),count(id),datasource"
                        + " from druid_segments"
                        + " where used=false "
                        + " group by datasource"))
                .to("jdbc:datasource")
            .end()

            // process payload
            .split(body())
                // format kill task 
                .setBody().body(Map.class, (b) -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> values = (Map<String, Object>) b;
                    if (!values.get("count").toString().equalsIgnoreCase("0")) {
                        return String.format("{\"type\":\"kill\",\"dataSource\":\"%s\",\"interval\":\"%s/%s\"}",
                                values.get("datasource"), values.get("min"), values.get("max"));
                    }
                    return null;
                })
                .filter(body().isNotNull())
                    .step("postTask")
                        .log("Posting kill task: ${body}")
                        .removeHeaders("(?:Camel.*)|(?:job.*)|(?:.*Job.*)")
                        .setHeader("Content-Type", constant("application/json"))
                        .toF("netty-http:%s/druid/indexer/v1/task?httpMethod=POST", druid)
                    .end()

                    .step("parseTask")
                        .setHeader("task").jsonpath("$.task")
                        .log("Task id: ${header.task}")
                    .end()

                    // monitor task status
                    .loopDoWhile(and(header("task").isNotNull(),header("status").isNotEqualTo("SUCCESS")))
                        .step("getStatus")
                            .setHeader("CamelHttpPath").simple("/druid/indexer/v1/task/${header.task}/status")
                            .setBody(constant(null))
                            .toF("netty-http:%s", druid)
                        .end()

                        .step("parseStatus")
                            .setHeader("status").jsonpath("$.status.status")
                            .setHeader("task").jsonpath("$.status.id")
                            .log("${header.status} -- ${header.task}")
                        .end()

                        // wait 3 seconds
                        .delay(3000)
                    .end()
                .end()
                
                .log("End cleanup dropped segmenets")
            .end();

    }
}
