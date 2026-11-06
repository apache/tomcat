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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.Principal;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;

class OpenWebBeansSecurityValveTest {

    @Test
    void principalIsVisibleDuringNextValveAndClearedAfterwards() throws Exception {
        Principal principal = () -> "testuser";
        OpenWebBeansSecurityValve valve = new OpenWebBeansSecurityValve();
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) {
                assertEquals(principal, OpenWebBeansSecurityValve.getPrincipal());
            }
        });

        valve.invoke(request(principal), new Response(new org.apache.coyote.Response()));

        assertNull(OpenWebBeansSecurityValve.getPrincipal());
    }

    @Test
    void nullPrincipalIsClearedAfterwards() throws Exception {
        OpenWebBeansSecurityValve valve = new OpenWebBeansSecurityValve();
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) {
                assertNull(OpenWebBeansSecurityValve.getPrincipal());
            }
        });

        valve.invoke(request(null), new Response(new org.apache.coyote.Response()));

        assertNull(OpenWebBeansSecurityValve.getPrincipal());
    }

    private static Request request(Principal principal) {
        Request request = new Request(null, new org.apache.coyote.Request());
        request.setUserPrincipal(principal);
        return request;
    }
}
