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

@ApplicationScoped
public class CleanupSegmentsRoute extends RouteBuilder {

    @ConfigProperty(name = "cleanup.period", defaultValue = "1h")
    String period;

    @ConfigProperty(name = "cleanup.druidUrl", defaultValue = "http://localhost:8081")
    String druid;

    @Override
    public void configure() throws Exception {
        fromF("timer:cleanup?period=%s", period).id("CleanupSegments")
            .log("Starting cleanup segmenets")
            
            // select not used segements from meta store
            .setBody(constant("select max(\"end\"),min(\"start\"),count(id),datasource from druid_segments"
                        + " where used=false group by datasource"))
            .to("jdbc:datasource")
                
            // process payload
            .split(body())
                // process values to json string task
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
                    // post task 
                    .log("Posting task: ${body}")
                    .removeHeaders("(?:Camel.*)|(?:job.*)|(?:.*Job.*)")
                    .setHeader("Content-Type", constant("application/json"))
                    .toF("netty-http:%s/druid/indexer/v1/task?httpMethod=POST&copyHeaders=false&mapHttpMessageHeaders=false", druid)
                    .log("Register task: ${body}")
                
                    // get status
                    .setHeader("taskid").jsonpath("$.task")
                    .setHeader("CamelHttpPath").simple("/druid/indexer/v1/task/${header.taskid}/status")
                    .log("path:${header.CamelHttpPath}")
                    .toF("netty-http:%s?httpMethod=GET", druid)
                    .setBody().jsonpath("$.status.status")
                    .log("task status: ${body}")
                .end()
            .end();
    }
}
