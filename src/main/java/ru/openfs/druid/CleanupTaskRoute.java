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

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.exec.ExecBinding;
import org.eclipse.microprofile.config.inject.ConfigProperty;


@ApplicationScoped
public class CleanupTaskRoute extends RouteBuilder {

    @ConfigProperty(name = "cleanup.task.period")
    String period;

    @ConfigProperty(name = "cleanup.task.logs")
    String logs;
    
    @ConfigProperty(name = "cleanup.task.enable", defaultValue = "true")
    boolean isEnable;

    @Override
    public void configure() throws Exception {

        // cleanup task 
        fromF("timer:taks?period=%s", period).autoStartup(isEnable).id("CleanupTask")
            .log("Starting cleanup indexing task")
            
            .step("getTasks")
                // select inactive tasks segements from metastore
                .setBody().
                    constant("select id,"
                    +" encode(status_payload,'escape')::json->>'status' as status,"
                    +" encode(payload,'escape')::json->>'type' as type,"
                    +" encode(payload,'escape') as payload"
                    +" from druid_tasks where active=false")
                .to("jdbc:datasource")
            .end()

            .split(body())
                // parse body to headers
                .setHeader("id").simple("${body.get('id')}")
                .setHeader("status").simple("${body.get('status')}")
                .setHeader("type").simple("${body.get('type')}")
                .setHeader("payload").simple("${body.get('payload')}")
            
                // process success task
                .filter(header("status").isEqualTo("SUCCESS"))
                    .log("Starting cleanup task:${header.id}")
                    
                    // process ingested files 
                    .filter(header("type").isEqualTo("index"))
                        .setHeader("localDir").jsonpath("spec.ioConfig.firehose.baseDir",false,String.class,"payload")
                        .setHeader("localFile").jsonpath("spec.ioConfig.firehose.filter",false,String.class,"payload")
                        .log("try to remove files:${header.localDir}/${header.localFile}")
                        .toD("exec:rm?args=-f ${header.localDir}/${header.localFile}")
                    .end()
                    
                    // drop log files
                    .setHeader("CamelExecCommandArgs").simple("-f " + logs + "/${header.id}.*")
                    .log("try to remove log files:${header.CamelExecCommandArgs}")
                    .to("exec:rm")

                    // drop task from store 
                    .log("try to delete task:${header.id} from store")
                    .setBody(simple("delete from druid_tasks where id='${headers.id}'"))
                    .to("jdbc:datasource?outputType=SelectOne")
                .end()
            .end()
            .log("Cleanup indexing task finished");

    }
}
