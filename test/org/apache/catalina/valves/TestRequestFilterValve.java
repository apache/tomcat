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

package org.apache.catalina.valves;

import java.io.IOException;

import javax.servlet.ServletException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

/**
 * {@link RequestFilterValve} Tests
 */
public class TestRequestFilterValve {

    private static final int OK        = 200;
    private static final int FORBIDDEN = 403;
    private static final int CUSTOM    = 499;

    private static final String ADDR_ALLOW_PAT        = "127\\.\\d*\\.\\d*\\.\\d*";
    private static final String ADDR_DENY_PAT         = "\\d*\\.\\d*\\.\\d*\\.1";
    private static final String ADDR_ONLY_ALLOW       = "127.0.0.2";
    private static final String ADDR_ONLY_DENY        = "192.168.0.1";
    private static final String ADDR_ALLOW_AND_DENY   = "127.0.0.1";
    private static final String ADDR_NO_ALLOW_NO_DENY = "192.168.0.2";

    private static final String HOST_ALLOW_PAT        = "www\\.example\\.[a-zA-Z0-9-]*";
    private static final String HOST_DENY_PAT         = ".*\\.org";
    private static final String HOST_ONLY_ALLOW       = "www.example.com";
    private static final String HOST_ONLY_DENY        = "host.example.org";
    private static final String HOST_ALLOW_AND_DENY   = "www.example.org";
    private static final String HOST_NO_ALLOW_NO_DENY = "host.example.com";

    private static final int PORT = 8080;
    private static final String PORT_MATCH_PATTERN    = ",\\d*";
    private static final String PORT_NO_MATCH_PATTERN = ",8081";


    static class TerminatingValve extends ValveBase {
        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
        }
    }

    public static class MockResponse extends Response {
        private int status = OK;

        @Override
        public void sendError(int status) throws IOException {
            this.status = status;
        }

        @Override
        public int getStatus() {
            return status;
        }
    }

    private void oneTest(String allow, String deny, boolean denyStatus,
                         boolean addLocalPort,
                         String property, String type, boolean allowed) {
        // PREPARE
        RequestFilterValve valve = null;
        Connector connector = new Connector();
        Request request = new Request();
        Response response = new MockResponse();
        StringBuilder msg = new StringBuilder();
        int expected = allowed ? OK : FORBIDDEN;

        connector.setPort(PORT);
        request.setConnector(connector);

        if (type == null) {
            fail("Invalid test with null type");
        }
        if (property != null) {
            if (type.equals("Addr")) {
                valve = new RemoteAddrValve();
                request.setRemoteAddr(property);
                msg.append(" ip='" + property + "'");
            } else if (type.equals("Host")) {
                valve = new RemoteHostValve();
                request.setRemoteHost(property);
                msg.append(" host='" + property + "'");
            } else {
                fail("Invalid test type" + type);
            }
        }
        valve.setNext(new TerminatingValve());

        if (allow != null) {
            valve.setAllow(allow);
            msg.append(" allow='" + allow + "'");
        }
        if (deny != null) {
            valve.setDeny(deny);
            msg.append(" deny='" + deny + "'");
        }
        if (denyStatus) {
            valve.setDenyStatus(CUSTOM);
            msg.append(" denyStatus='" + CUSTOM + "'");
            if (!allowed) {
                expected = CUSTOM;
            }
        }
        if (addLocalPort) {
            if (valve instanceof RemoteAddrValve) {
                ((RemoteAddrValve)valve).setAddLocalPort(true);
            } else if (valve instanceof RemoteHostValve) {
                ((RemoteHostValve)valve).setAddLocalPort(true);
            } else {
                fail("Can only set 'addLocalPort' for RemoteAddrValve and RemoteHostValve");
            }
            msg.append(" addLocalPort='true'");
        }

        // TEST
        try {
            valve.invoke(request, response);
        } catch (IOException ex) {
            //Ignore
        } catch (ServletException ex) {
            //Ignore
        }

        // VERIFY
        assertEquals(msg.toString(), expected, response.getStatus());
    }

    private void standardTests(String allow_pat, String deny_pat,
                               String OnlyAllow, String OnlyDeny,
                               String AllowAndDeny, String NoAllowNoDeny,
                               String type) {
        String apat;
        String dpat;

        // Test without ports
        apat = allow_pat;
        dpat = deny_pat;
        oneTest(null, null, false, false, AllowAndDeny,  type, false);
        oneTest(null, null, true,  false, AllowAndDeny,  type, false);
        oneTest(apat, null, false, false, AllowAndDeny,  type, true);
        oneTest(apat, null, false, false, NoAllowNoDeny, type, false);
        oneTest(apat, null, true,  false, AllowAndDeny,  type, true);
        oneTest(apat, null, true,  false, NoAllowNoDeny, type, false);
        oneTest(null, dpat, false, false, AllowAndDeny,  type, false);
        oneTest(null, dpat, false, false, NoAllowNoDeny, type, true);
        oneTest(null, dpat, true,  false, AllowAndDeny,  type, false);
        oneTest(null, dpat, true,  false, NoAllowNoDeny, type, true);
        oneTest(apat, dpat, false, false, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, false, false, OnlyAllow,     type, true);
        oneTest(apat, dpat, false, false, OnlyDeny,      type, false);
        oneTest(apat, dpat, false, false, AllowAndDeny,  type, false);
        oneTest(apat, dpat, true,  false, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, true,  false, OnlyAllow,     type, true);
        oneTest(apat, dpat, true,  false, OnlyDeny,      type, false);
        oneTest(apat, dpat, true,  false, AllowAndDeny,  type, false);

        // Test with port in pattern but forgotten "addLocalPort"
        apat = allow_pat + PORT_MATCH_PATTERN;
        dpat = deny_pat + PORT_MATCH_PATTERN;
        oneTest(null, null, false, false, AllowAndDeny,  type, false);
        oneTest(null, null, true,  false, AllowAndDeny,  type, false);
        oneTest(apat, null, false, false, AllowAndDeny,  type, false);
        oneTest(apat, null, false, false, NoAllowNoDeny, type, false);
        oneTest(apat, null, true,  false, AllowAndDeny,  type, false);
        oneTest(apat, null, true,  false, NoAllowNoDeny, type, false);
        oneTest(null, dpat, false, false, AllowAndDeny,  type, true);
        oneTest(null, dpat, false, false, NoAllowNoDeny, type, true);
        oneTest(null, dpat, true,  false, AllowAndDeny,  type, true);
        oneTest(null, dpat, true,  false, NoAllowNoDeny, type, true);
        oneTest(apat, dpat, false, false, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, false, false, OnlyAllow,     type, false);
        oneTest(apat, dpat, false, false, OnlyDeny,      type, false);
        oneTest(apat, dpat, false, false, AllowAndDeny,  type, false);
        oneTest(apat, dpat, true,  false, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, true,  false, OnlyAllow,     type, false);
        oneTest(apat, dpat, true,  false, OnlyDeny,      type, false);
        oneTest(apat, dpat, true,  false, AllowAndDeny,  type, false);

        // Test with "addLocalPort" but port not in pattern
        apat = allow_pat;
        dpat = deny_pat;
        oneTest(null, null, false, true, AllowAndDeny,  type, false);
        oneTest(null, null, true,  true, AllowAndDeny,  type, false);
        oneTest(apat, null, false, true, AllowAndDeny,  type, false);
        oneTest(apat, null, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, null, true,  true, AllowAndDeny,  type, false);
        oneTest(apat, null, true,  true, NoAllowNoDeny, type, false);
        oneTest(null, dpat, false, true, AllowAndDeny,  type, true);
        oneTest(null, dpat, false, true, NoAllowNoDeny, type, true);
        oneTest(null, dpat, true,  true, AllowAndDeny,  type, true);
        oneTest(null, dpat, true,  true, NoAllowNoDeny, type, true);
        oneTest(apat, dpat, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, false, true, OnlyAllow,     type, false);
        oneTest(apat, dpat, false, true, OnlyDeny,      type, false);
        oneTest(apat, dpat, false, true, AllowAndDeny,  type, false);
        oneTest(apat, dpat, true,  true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, true,  true, OnlyAllow,     type, false);
        oneTest(apat, dpat, true,  true, OnlyDeny,      type, false);
        oneTest(apat, dpat, true,  true, AllowAndDeny,  type, false);

        // Test "addLocalPort" and with port matching in both patterns
        apat = allow_pat + PORT_MATCH_PATTERN;
        dpat = deny_pat + PORT_MATCH_PATTERN;
        oneTest(null, null, false, true, AllowAndDeny,  type, false);
        oneTest(null, null, true,  true, AllowAndDeny,  type, false);
        oneTest(apat, null, false, true, AllowAndDeny,  type, true);
        oneTest(apat, null, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, null, true,  true, AllowAndDeny,  type, true);
        oneTest(apat, null, true,  true, NoAllowNoDeny, type, false);
        oneTest(null, dpat, false, true, AllowAndDeny,  type, false);
        oneTest(null, dpat, false, true, NoAllowNoDeny, type, true);
        oneTest(null, dpat, true,  true, AllowAndDeny,  type, false);
        oneTest(null, dpat, true,  true, NoAllowNoDeny, type, true);
        oneTest(apat, dpat, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, false, true, OnlyAllow,     type, true);
        oneTest(apat, dpat, false, true, OnlyDeny,      type, false);
        oneTest(apat, dpat, false, true, AllowAndDeny,  type, false);
        oneTest(apat, dpat, true,  true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, true,  true, OnlyAllow,     type, true);
        oneTest(apat, dpat, true,  true, OnlyDeny,      type, false);
        oneTest(apat, dpat, true,  true, AllowAndDeny,  type, false);

        // Test "addLocalPort" and with port not matching in both patterns
        apat = allow_pat + PORT_NO_MATCH_PATTERN;
        dpat = deny_pat + PORT_NO_MATCH_PATTERN;
        oneTest(null, null, false, true, AllowAndDeny,  type, false);
        oneTest(null, null, true,  true, AllowAndDeny,  type, false);
        oneTest(apat, null, false, true, AllowAndDeny,  type, false);
        oneTest(apat, null, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, null, true,  true, AllowAndDeny,  type, false);
        oneTest(apat, null, true,  true, NoAllowNoDeny, type, false);
        oneTest(null, dpat, false, true, AllowAndDeny,  type, true);
        oneTest(null, dpat, false, true, NoAllowNoDeny, type, true);
        oneTest(null, dpat, true,  true, AllowAndDeny,  type, true);
        oneTest(null, dpat, true,  true, NoAllowNoDeny, type, true);
        oneTest(apat, dpat, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, false, true, OnlyAllow,     type, false);
        oneTest(apat, dpat, false, true, OnlyDeny,      type, false);
        oneTest(apat, dpat, false, true, AllowAndDeny,  type, false);
        oneTest(apat, dpat, true,  true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, true,  true, OnlyAllow,     type, false);
        oneTest(apat, dpat, true,  true, OnlyDeny,      type, false);
        oneTest(apat, dpat, true,  true, AllowAndDeny,  type, false);

        // Test "addLocalPort" and with port matching only in allow
        apat = allow_pat + PORT_MATCH_PATTERN;
        dpat = deny_pat + PORT_NO_MATCH_PATTERN;
        oneTest(null, null, false, true, AllowAndDeny,  type, false);
        oneTest(null, null, true,  true, AllowAndDeny,  type, false);
        oneTest(apat, null, false, true, AllowAndDeny,  type, true);
        oneTest(apat, null, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, null, true,  true, AllowAndDeny,  type, true);
        oneTest(apat, null, true,  true, NoAllowNoDeny, type, false);
        oneTest(null, dpat, false, true, AllowAndDeny,  type, true);
        oneTest(null, dpat, false, true, NoAllowNoDeny, type, true);
        oneTest(null, dpat, true,  true, AllowAndDeny,  type, true);
        oneTest(null, dpat, true,  true, NoAllowNoDeny, type, true);
        oneTest(apat, dpat, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, false, true, OnlyAllow,     type, true);
        oneTest(apat, dpat, false, true, OnlyDeny,      type, false);
        oneTest(apat, dpat, false, true, AllowAndDeny,  type, true);
        oneTest(apat, dpat, true,  true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, true,  true, OnlyAllow,     type, true);
        oneTest(apat, dpat, true,  true, OnlyDeny,      type, false);
        oneTest(apat, dpat, true,  true, AllowAndDeny,  type, true);

        // Test "addLocalPort" and with port matching only in deny
        apat = allow_pat + PORT_NO_MATCH_PATTERN;
        dpat = deny_pat + PORT_MATCH_PATTERN;
        oneTest(null, null, false, true, AllowAndDeny,  type, false);
        oneTest(null, null, true,  true, AllowAndDeny,  type, false);
        oneTest(apat, null, false, true, AllowAndDeny,  type, false);
        oneTest(apat, null, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, null, true,  true, AllowAndDeny,  type, false);
        oneTest(apat, null, true,  true, NoAllowNoDeny, type, false);
        oneTest(null, dpat, false, true, AllowAndDeny,  type, false);
        oneTest(null, dpat, false, true, NoAllowNoDeny, type, true);
        oneTest(null, dpat, true,  true, AllowAndDeny,  type, false);
        oneTest(null, dpat, true,  true, NoAllowNoDeny, type, true);
        oneTest(apat, dpat, false, true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, false, true, OnlyAllow,     type, false);
        oneTest(apat, dpat, false, true, OnlyDeny,      type, false);
        oneTest(apat, dpat, false, true, AllowAndDeny,  type, false);
        oneTest(apat, dpat, true,  true, NoAllowNoDeny, type, false);
        oneTest(apat, dpat, true,  true, OnlyAllow,     type, false);
        oneTest(apat, dpat, true,  true, OnlyDeny,      type, false);
        oneTest(apat, dpat, true,  true, AllowAndDeny,  type, false);
    }

    @Test
    public void testRemoteAddrValveIPv4() {
        standardTests(ADDR_ALLOW_PAT, ADDR_DENY_PAT,
                      ADDR_ONLY_ALLOW, ADDR_ONLY_DENY,
                      ADDR_ALLOW_AND_DENY, ADDR_NO_ALLOW_NO_DENY,
                      "Addr");
    }

    @Test
    public void testRemoteHostValve() {
        standardTests(HOST_ALLOW_PAT, HOST_DENY_PAT,
                      HOST_ONLY_ALLOW, HOST_ONLY_DENY,
                      HOST_ALLOW_AND_DENY, HOST_NO_ALLOW_NO_DENY,
                      "Host");
    }
}
