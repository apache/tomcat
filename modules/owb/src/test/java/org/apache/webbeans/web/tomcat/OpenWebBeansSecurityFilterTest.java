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

import java.lang.reflect.Proxy;
import java.security.Principal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class OpenWebBeansSecurityFilterTest {

    @Test
    void principalIsVisibleDuringChainAndClearedAfterwards() throws Exception {
        Principal principal = () -> "testuser";
        HttpServletRequest request = request(principal);

        FilterChain chain = (servletRequest, servletResponse) ->
                assertEquals(principal, OpenWebBeansSecurityFilter.getPrincipal());

        new OpenWebBeansSecurityFilter().doFilter(request, null, chain);

        assertNull(OpenWebBeansSecurityFilter.getPrincipal());
    }

    @Test
    void nullPrincipalIsClearedAfterwards() throws Exception {
        HttpServletRequest request = request(null);

        FilterChain chain = (servletRequest, servletResponse) ->
                assertNull(OpenWebBeansSecurityFilter.getPrincipal());

        new OpenWebBeansSecurityFilter().doFilter(request, null, chain);

        assertNull(OpenWebBeansSecurityFilter.getPrincipal());
    }

    private static HttpServletRequest request(Principal principal) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                OpenWebBeansSecurityFilterTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> {
                    if ("getUserPrincipal".equals(method.getName())) {
                        return principal;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
