/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net.ocsp;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.ExpandWar;
import org.apache.catalina.startup.Tomcat;

public class TesterOcspResponder {

    private OcspResponse fixedResponse;

    private File catalinaBase;
    private Tomcat ocspResponder;

    public void setFixedResponse(OcspResponse fixedResponse) {
        this.fixedResponse = fixedResponse;
    }

    public void start() throws Exception {
        ocspResponder = new Tomcat();

        Connector connector = new Connector("HTTP/1.1");
        connector.setPort(8888);
        connector.setThrowOnFailure(true);
        connector.setEncodedSolidusHandling("passthrough");
        ocspResponder.getService().addConnector(connector);

        // Create a temporary directory structure for the OCSP responder
        File tempBase = new File(System.getProperty("tomcat.test.temp", "output/tmp"));
        if (!tempBase.mkdirs() && !tempBase.isDirectory()) {
            throw new IllegalStateException("Unable to create tempBase");
        }

        // Create and configure CATALINA_BASE
        Path tempBasePath = FileSystems.getDefault().getPath(tempBase.getAbsolutePath());
        catalinaBase = Files.createTempDirectory(tempBasePath, "ocsp").toFile();
        if (!catalinaBase.isDirectory()) {
            throw new IllegalStateException("Unable to create CATALINA_BASE for OCSP responder");
        }
        ocspResponder.setBaseDir(catalinaBase.getAbsolutePath());

        // Create and configure a web apps directory
        File appBase = new File(catalinaBase, "webapps");
        if (!appBase.exists() && !appBase.mkdir()) {
            throw new IllegalStateException("Unable to create appBase for OCSP responder");
        }
        ocspResponder.getHost().setAppBase(appBase.getAbsolutePath());

        // Configure the ROOT web application
        // No file system docBase required
        Context ctx = ocspResponder.addContext("", null);
        Wrapper w = Tomcat.addServlet(ctx, "responder", new TesterOcspResponderServlet());
        ctx.addServletMappingDecoded("/", "responder");
        if (fixedResponse != null) {
            w.addInitParameter(TesterOcspResponderServlet.INIT_FIXED_RESPONSE, fixedResponse.toString());
        }

        // Start the responder
        ocspResponder.start();
    }

    public void stop() {
        if (ocspResponder != null) {
            try {
                ocspResponder.stop();
            } catch (LifecycleException e) {
                // Good enough for testing
                e.printStackTrace();
            }
            try {
                ocspResponder.destroy();
            } catch (LifecycleException e) {
                // Good enough for testing
                e.printStackTrace();
            }
        }
        if (catalinaBase != null) {
            ExpandWar.deleteDir(catalinaBase);
        }
    }

    public enum OcspResponse {
        OK,
        REVOKED,
        UNKNOWN,
        TRY_LATER,
        INTERNAL_ERROR
    }
}
