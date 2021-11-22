/* Licensed to the Apache Software Foundation (ASF) under one or more
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.Cookie;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardPipeline;
import org.apache.tomcat.util.descriptor.web.Constants;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;

@RunWith(Parameterized.class)
public class TestLoadBalancerDrainingValve {

    @Parameters(name = "{index}: activation[{0}], validSessionID[{1}], expectInvokeNext[{2}], enableIgnore[{3}], " +
            "queryString[{4}]")
    public static Collection<Object[]> parameters() {

        String[] jkActivations = new String[] { "ACT", "DIS" };
        Boolean[] booleans = new Boolean[] { Boolean.TRUE, Boolean.FALSE };
        String[] queryStrings = new String[] { null, "foo=bar" };

        List<Object[]> parameterSets = new ArrayList<>();
            for (String jkActivation : jkActivations) {
                for (Boolean validSessionId : booleans) {
                    for (Boolean enableIgnore : booleans) {
                        Boolean expectInvokeNext = Boolean.valueOf("ACT".equals(jkActivation) || enableIgnore.booleanValue() ||
                                validSessionId.booleanValue());
                        for (String queryString : queryStrings) {
                            for (Boolean secureRequest : booleans) {
                                for (Boolean secureSessionConfig : booleans) {
                                    parameterSets.add(new Object[] { jkActivation, validSessionId, expectInvokeNext,
                                            enableIgnore, queryString, secureRequest, secureSessionConfig});
                                }
                            }
                        }
                    }
                }
            }
        return parameterSets;
    }


    @Parameter(0)
    public String jkActivation;

    @Parameter(1)
    public boolean validSessionId;

    @Parameter(2)
    public boolean expectInvokeNext;

    @Parameter(3)
    public boolean enableIgnore;

    @Parameter(4)
    public String queryString;

    @Parameter(5)
    public Boolean secureRequest;

    @Parameter(6)
    public boolean secureSessionConfig;


    @Test
    public void runValve() throws Exception {
        IMocksControl control = EasyMock.createControl();
        ServletContext servletContext = control.createMock(ServletContext.class);
        Context ctx = control.createMock(Context.class);
        Request request = control.createMock(Request.class);
        Response response = control.createMock(Response.class);

        String sessionCookieName = "JSESSIONID";
        String sessionId = "cafebabe";
        String requestURI = "/test/path";
        SessionCookieConfig cookieConfig = new CookieConfig();
        cookieConfig.setDomain("example.com");
        cookieConfig.setName(sessionCookieName);
        cookieConfig.setPath("/");
        cookieConfig.setSecure(secureSessionConfig);

        // Valve.init requires all of this stuff
        EasyMock.expect(ctx.getMBeanKeyProperties()).andStubReturn("");
        EasyMock.expect(ctx.getName()).andStubReturn("");
        EasyMock.expect(ctx.getPipeline()).andStubReturn(new StandardPipeline());
        EasyMock.expect(ctx.getDomain()).andStubReturn("foo");
        EasyMock.expect(ctx.getLogger()).andStubReturn(org.apache.juli.logging.LogFactory.getLog(LoadBalancerDrainingValve.class));
        EasyMock.expect(ctx.getServletContext()).andStubReturn(servletContext);

        // Set up the actual test
        EasyMock.expect(request.getAttribute(LoadBalancerDrainingValve.ATTRIBUTE_KEY_JK_LB_ACTIVATION)).andStubReturn(jkActivation);
        EasyMock.expect(Boolean.valueOf(request.isRequestedSessionIdValid())).andStubReturn(Boolean.valueOf(validSessionId));

        ArrayList<Cookie> cookies = new ArrayList<>();
        if(enableIgnore) {
            cookies.add(new Cookie("ignore", "true"));
        }

        if(!validSessionId && jkActivation.equals("DIS")) {
            MyCookie cookie = new MyCookie(cookieConfig.getName(), sessionId);
            cookie.setPath(cookieConfig.getPath());
            cookie.setValue(sessionId);

            cookies.add(cookie);

            EasyMock.expect(request.getRequestedSessionId()).andStubReturn(sessionId);
            EasyMock.expect(request.getRequestURI()).andStubReturn(requestURI);
            EasyMock.expect(request.getCookies()).andStubReturn(cookies.toArray(new Cookie[0]));
            EasyMock.expect(request.getContext()).andStubReturn(ctx);
            EasyMock.expect(ctx.getSessionCookieName()).andStubReturn(sessionCookieName);
            EasyMock.expect(servletContext.getSessionCookieConfig()).andStubReturn(cookieConfig);
            EasyMock.expect(request.getQueryString()).andStubReturn(queryString);
            EasyMock.expect(ctx.getSessionCookiePath()).andStubReturn("/");

            if (!enableIgnore) {
                EasyMock.expect(Boolean.valueOf(ctx.getSessionCookiePathUsesTrailingSlash())).andStubReturn(Boolean.TRUE);
                EasyMock.expect(request.getQueryString()).andStubReturn(queryString);
                // Response will have cookie deleted
                MyCookie expectedCookie = new MyCookie(cookieConfig.getName(), "");
                expectedCookie.setPath(cookieConfig.getPath());
                expectedCookie.setMaxAge(0);

                EasyMock.expect(Boolean.valueOf(request.isSecure())).andReturn(secureRequest);

                // These two lines just mean EasyMock.expect(response.addCookie) but for a void method
                response.addCookie(expectedCookie);
                EasyMock.expect(ctx.getSessionCookieName()).andReturn(sessionCookieName); // Indirect call
                String expectedRequestURI = requestURI;
                if(null != queryString) {
                  expectedRequestURI = expectedRequestURI + '?' + queryString;
                }
                response.setHeader("Location", expectedRequestURI);
                response.setStatus(307);
            }
        }

        Valve next = control.createMock(Valve.class);

        if(expectInvokeNext) {
            // Expect the "next" Valve to fire
            // Next 2 lines are basically EasyMock.expect(next.invoke(req,res)) but for a void method
            next.invoke(request, response);
            EasyMock.expectLastCall();
        }

        // Get set to actually test
        control.replay();

        LoadBalancerDrainingValve valve = new LoadBalancerDrainingValve();
        valve.setContainer(ctx);
        valve.init();
        valve.setNext(next);
        valve.setIgnoreCookieName("ignore");
        valve.setIgnoreCookieValue("true");

        valve.invoke(request, response);

        control.verify();
    }


    private static class CookieConfig implements SessionCookieConfig {

        private String name;

        private final Map<String,String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        @Override
        public String getName() {
            return name;
        }
        @Override
        public void setName(String name) {
            this.name = name;
        }
        @Override
        public String getDomain() {
            return attributes.get(Constants.COOKIE_DOMAIN_ATTR);
        }
        @Override
        public void setDomain(String domain) {
            attributes.put(Constants.COOKIE_DOMAIN_ATTR, domain);
        }
        @Override
        public String getPath() {
            return attributes.get(Constants.COOKIE_PATH_ATTR);
        }
        @Override
        public void setPath(String path) {
            attributes.put(Constants.COOKIE_PATH_ATTR, path);
        }
        @Override
        public String getComment() {
            return null;
        }
        @Override
        public void setComment(String comment) {
            // NO-OP
        }
        @Override
        public boolean isHttpOnly() {
            String httpOnly = getAttribute(Constants.COOKIE_HTTP_ONLY_ATTR);
            if (httpOnly == null) {
                return false;
            }
            return Boolean.parseBoolean(httpOnly);
        }
        @Override
        public void setHttpOnly(boolean httpOnly) {
            setAttribute(Constants.COOKIE_HTTP_ONLY_ATTR, Boolean.toString(httpOnly));
        }
        @Override
        public boolean isSecure() {
            String secure = getAttribute(Constants.COOKIE_SECURE_ATTR);
            if (secure == null) {
                return false;
            }
            return Boolean.parseBoolean(secure);
        }
        @Override
        public void setSecure(boolean secure) {
            setAttribute(Constants.COOKIE_SECURE_ATTR, Boolean.toString(secure));
        }
        @Override
        public int getMaxAge() {
            String maxAge = getAttribute(Constants.COOKIE_MAX_AGE_ATTR);
            if (maxAge == null) {
                return -1;
            }
            return Integer.parseInt(maxAge);
        }
        @Override
        public void setMaxAge(int maxAge) {
            setAttribute(Constants.COOKIE_MAX_AGE_ATTR, Integer.toString(maxAge));
        }
        @Override
        public void setAttribute(String name, String value) {
            attributes.put(name, value);
        }
        @Override
        public String getAttribute(String name) {
            return attributes.get(name);
        }
        @Override
        public Map<String, String> getAttributes() {
            return Collections.unmodifiableMap(attributes);
        }
    }


    // A Cookie subclass that knows how to compare itself to other Cookie objects
    private static class MyCookie extends Cookie {
        private static final long serialVersionUID = 1L;

        public MyCookie(String name, String value) { super(name, value); }

        @Override
        public boolean equals(Object o) {
            if(!(o instanceof MyCookie)) {
                return false;
            }

            MyCookie mc = (MyCookie)o;
            return mc.getName().equals(this.getName())
                && mc.getPath().equals(this.getPath())
                && mc.getValue().equals(this.getValue())
                && mc.getMaxAge() == this.getMaxAge();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getMaxAge();
            result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
            result = prime * result + ((getPath() == null) ? 0 : getPath().hashCode());
            result = prime * result + ((getValue() == null) ? 0 : getValue().hashCode());
            return result;
        }


        @Override
        public String toString() {
            return "Cookie { name=" + getName() + ", value=" + getValue() + ", path=" + getPath() + ", maxAge=" + getMaxAge() + " }";
        }
    }
}
