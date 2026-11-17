/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.webbeans.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.webbeans.web.tomcat.OwbTestServer.ListenerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansInstanceManagerIT {

    @TempDir
    Path tempDir;

    @Test
    void allInstanceManagerPathsInject() throws Exception {
        Path markerDirectory = tempDir.resolve("markers");
        Files.createDirectories(markerDirectory);
        String previousMarkerDirectory = System.getProperty("owb.test.marker.dir");
        System.setProperty("owb.test.marker.dir", markerDirectory.toString());
        try {
            Path webapp = new WebAppBuilder(tempDir.resolve("instance"), "instance")
                    .build(OwbTestSupport.shadedJar());
            try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, true)) {
                HttpClient client = OwbTestSupport.newClient();
                assertEquals("class=injected,string=injected,stringLoader=injected,object=injected,context=restored",
                        OwbTestSupport.get(client, server.baseUri().resolve("instance-probe")));
                OwbTestSupport.assertMarker(markerDirectory, "probePostConstruct.marker");
                OwbTestSupport.assertMarker(markerDirectory, "probePreDestroy.marker");
            }
        } finally {
            if (previousMarkerDirectory == null) {
                System.clearProperty("owb.test.marker.dir");
            } else {
                System.setProperty("owb.test.marker.dir", previousMarkerDirectory);
            }
        }
    }
}
