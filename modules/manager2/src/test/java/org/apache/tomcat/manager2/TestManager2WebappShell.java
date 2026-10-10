/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.startup.SimpleHttpClient;

/**
 * The SPA shell, its static assets, the server-side localization and the public i18n endpoint of the manager2
 * webapp.
 */
public class TestManager2WebappShell extends Manager2WebappTestBase {

    @Test
    public void testStaticAssetsArePublic() throws Exception {
        setup(false);

        // Regression test: a security constraint with the pattern "/" matches
        // every request in the context. When the SPA shell was protected with
        // such a constraint, the login page's own CSS and JS requests were
        // sent through FORM authentication (leaving the login page unstyled)
        // and the saved request pointed at an asset file.
        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        requestRaw(client, "GET", MANAGER2 + "/css/manager2.css", 200);
        boolean css = false;
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("content-type:") && header.contains("text/css")) {
                css = true;
            }
        }
        Assert.assertTrue("Expected text/css for the stylesheet", css);

        requestRaw(client, "GET", MANAGER2 + "/js/main.js", 200);

        // The i18n endpoint is public too (the login page needs it).
        requestRaw(client, "GET", MANAGER2 + "/i18n", 200);

        client.disconnect();
    }


    @Test
    public void testI18nEndpointServesClientMessages() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Public endpoint: it carries only the manager2.ui.* messages of the
        // bundle, localized with the best bundle match for the request
        // (the bundle is English only, so the base bundle always wins).
        requestRaw(client, "GET", MANAGER2 + "/i18n", 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"locale\":\"en\""));
        Assert.assertTrue(body.contains("\"manager2.ui.brand.name\":\"Tomcat Manager\""));
        Assert.assertTrue(body.contains("\"manager2.ui.nav.dashboard\":\"Dashboard\""));
        Assert.assertTrue(body.contains("\"manager2.ui.nav.cluster\":\"Cluster\""));
        // The dynamic manager2.ui.type.* keys are served as well.
        Assert.assertTrue(body.contains("\"manager2.ui.type.connector\":\"connector\""));
        // Server-side-only keys are never exposed to the browser.
        Assert.assertFalse(body.contains("manager2.uploadNoFile"));
        Assert.assertFalse(body.contains("csrfFilter.invalid"));
        boolean vary = false;
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("vary:") && header.toLowerCase().contains("accept-language")) {
                vary = true;
            }
        }
        Assert.assertTrue("Expected a Vary: Accept-Language header", vary);

        // A locale without a bundle falls back to the base (English) bundle.
        client.setRequest(new String[] { "GET " + MANAGER2 + "/i18n HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Accept-Language: de-DE,de;q=0.9" + CRLF,
                "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(200, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("\"locale\":\"en\""));

        // Messages only: the endpoint answers GET.
        request(client, "POST", MANAGER2 + "/i18n", null, null, 405);

        client.disconnect();
    }


    @Test
    public void testLoginAndErrorPagesAreLocalizedServerSide() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Unauthenticated SPA entry point: the login template is rendered
        // with the bundle's messages, including the lang attribute, before
        // any script runs.
        client.setRequest(new String[] { "GET " + MANAGER2 + "/ HTTP/1.1" + CRLF, "Host: localhost:" + getPort() + CRLF,
                "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(200, client.getStatusCode());
        String login = client.getResponseBody();
        Assert.assertTrue(login.contains("j_security_check"));
        Assert.assertTrue(login.contains("lang=\"en\""));
        Assert.assertTrue(login.contains("Sign in"));
        Assert.assertTrue(login.contains("User name"));
        Assert.assertFalse("Unsubstituted token left in the login page", login.contains("#{"));
        Assert.assertFalse("Unsubstituted lang token left in the login page", login.contains("{{"));

        // 404 error page, rendered by the ErrorServlet.
        requestRaw(client, "GET", MANAGER2 + "/does-not-exist", 404);
        String error = client.getResponseBody();
        Assert.assertTrue(error.contains("Not found"));
        Assert.assertTrue(error.contains("Back to dashboard"));
        Assert.assertFalse("Unsubstituted token left in the error page", error.contains("#{"));

        client.disconnect();
    }


    @Test
    public void testAuthenticatedRootServesShell() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The context root serves the SPA shell to authenticated users.
        requestRaw(client, "GET", MANAGER2 + "/", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));
        Assert.assertTrue(client.getResponseBody().contains("js/main.js"));

        // The SPA deep-link routes serve the shell as well (deep links survive
        // a reload).
        requestRaw(client, "GET", MANAGER2 + "/apps", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));

        // A multi-segment deep link (e.g. an application detail page) must also
        // serve the shell, with a base element so the shell's relative asset URLs
        // resolve against the context instead of the deep path.
        requestRaw(client, "GET", MANAGER2 + "/apps/localhost/myapp", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));
        Assert.assertTrue(client.getResponseBody().contains("<base href=\"" + MANAGER2 + "/\">"));

        client.disconnect();
    }
}
