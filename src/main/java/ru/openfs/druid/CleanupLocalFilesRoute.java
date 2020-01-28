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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static org.apache.camel.builder.PredicateBuilder.and;

@ApplicationScoped
public class CleanupLocalFilesRoute extends RouteBuilder {

    @ConfigProperty(name = "cleanup.logs.dir")
    String logsDir;

    @ConfigProperty(name = "cleanup.logs.delay", defaultValue = "5m30s")
    String logsDelay;

    @Override
    public void configure() throws Exception {

        fromF("file:%s?delete=true&sortBy=file:name&delay=%s", logsDir, logsDelay)
        .id("CleanupLocalFiles")
        .log("Setting next indexing-log to:${file:name}")
        // process log file
        .filter(header("CamelFileName").regex("^index.*.log"))
            // extract task id
			.setHeader("task",header("CamelFileName").regexReplaceAll(".log", ""))
            .log("process task:${header.task}")
            // get payload 
			.setBody(simple("select encode(payload,'escape') from druid_tasks where id='${headers.task}'"))
            .to("jdbc:datasource?outputType=SelectOne")
            // process payload
            .filter(and(body().isNotNull(),jsonpath("spec.ioConfig.firehose.type").isEqualTo("local")))
                .setHeader("localDir", jsonpath("spec.ioConfig.firehose.baseDir"))
                .setHeader("localFile", jsonpath("spec.ioConfig.firehose.filter"))
                .log("try to remove ${header.localFile} from ${header.localDir}")
                // remove dir
                .toD("exec:/usr/bin/rm?args=-f ${header.localDir}/${header.localFile}")
                // delete db task
                .setBody(simple("delete from druid_tasks where id='${headers.task}'"))
                .to("jdbc:datasource?outputType=SelectOne")
            .end()
        .end();
           
    }
}
