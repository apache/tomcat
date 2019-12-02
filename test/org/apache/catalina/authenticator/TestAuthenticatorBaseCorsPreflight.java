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
package org.apache.catalina.authenticator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Realm;
import org.apache.catalina.authenticator.AuthenticatorBase.AllowCorsPreflight;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.filters.AddDefaultCharsetFilter;
import org.apache.catalina.filters.CorsFilter;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestAuthenticatorBaseCorsPreflight extends TomcatBaseTest {

    private static final String ALLOWED_ORIGIN = "http://example.com";
    private static final String EMPTY_ORIGIN = "";
    private static final String INVALID_ORIGIN = "http://%20";
    private static final String SAME_ORIGIN = "http://localhost";
    private static final String ALLOWED_METHOD = "GET";
    private static final String BLOCKED_METHOD = "POST";
    private static final String EMPTY_METHOD = "";

    @Parameterized.Parameters(name = "{index}: input[{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<Object[]>();

        parameterSets.add(new Object[] { AllowCorsPreflight.NEVER,  "/*", "OPTIONS", null,           null,           Boolean.FALSE });
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "OPTIONS", null,           null,           Boolean.FALSE });
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "OPTIONS", ALLOWED_ORIGIN, ALLOWED_METHOD, Boolean.TRUE });
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "OPTIONS", EMPTY_ORIGIN,   ALLOWED_METHOD, Boolean.FALSE});
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "OPTIONS", INVALID_ORIGIN, ALLOWED_METHOD, Boolean.FALSE });
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "OPTIONS", SAME_ORIGIN,    ALLOWED_METHOD, Boolean.FALSE });
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "GET",     ALLOWED_ORIGIN, ALLOWED_METHOD, Boolean.FALSE });
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "OPTIONS", ALLOWED_ORIGIN, BLOCKED_METHOD, Boolean.FALSE });
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "OPTIONS", ALLOWED_ORIGIN, EMPTY_METHOD,   Boolean.FALSE});
        parameterSets.add(new Object[] { AllowCorsPreflight.ALWAYS, "/*", "OPTIONS", ALLOWED_ORIGIN, null,           Boolean.FALSE});
        parameterSets.add(new Object[] { AllowCorsPreflight.FILTER, "/*", "OPTIONS", ALLOWED_ORIGIN, ALLOWED_METHOD, Boolean.TRUE });
        parameterSets.add(new Object[] { AllowCorsPreflight.FILTER, "/x", "OPTIONS", ALLOWED_ORIGIN, ALLOWED_METHOD, Boolean.FALSE });

        return parameterSets;
    }

    @Parameter(0)
    public AllowCorsPreflight allowCorsPreflight;
    @Parameter(1)
    public String filterMapping;
    @Parameter(2)
    public String method;
    @Parameter(3)
    public String origin;
    @Parameter(4)
    public String accessControl;
    @Parameter(5)
    public boolean allow;


    @BeforeClass
    public static void init() {
        // So the test can set the origin header
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }


    @Test
    public void test() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctx = tomcat.addContext("", appDir.getAbsolutePath());

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        LoginConfig loginConfig  = new LoginConfig();
        loginConfig.setAuthMethod("BASIC");
        ctx.setLoginConfig(loginConfig);

        BasicAuthenticator basicAuth = new BasicAuthenticator();
        basicAuth.setAllowCorsPreflight(allowCorsPreflight.toString());
        ctx.getPipeline().addValve(basicAuth);

        Realm realm = new NullRealm();
        ctx.setRealm(realm);

        SecurityCollection securityCollection = new SecurityCollection();
        securityCollection.addPattern("/*");
        SecurityConstraint constraint = new SecurityConstraint();
        constraint.setAuthConstraint(true);
        constraint.addCollection(securityCollection);
        ctx.addConstraint(constraint);

        // For code coverage
        FilterDef otherFilter = new FilterDef();
        otherFilter.setFilterName("other");
        otherFilter.setFilterClass(AddDefaultCharsetFilter.class.getName());
        FilterMap otherMap = new FilterMap();
        otherMap.setFilterName("other");
        otherMap.addURLPattern("/other");
        ctx.addFilterDef(otherFilter);
        ctx.addFilterMap(otherMap);

        FilterDef corsFilter = new FilterDef();
        corsFilter.setFilterName("cors");
        corsFilter.setFilterClass(CorsFilter.class.getName());
        corsFilter.addInitParameter(CorsFilter.PARAM_CORS_ALLOWED_ORIGINS, ALLOWED_ORIGIN);
        corsFilter.addInitParameter(CorsFilter.PARAM_CORS_ALLOWED_METHODS, ALLOWED_METHOD);
        FilterMap corsFilterMap = new FilterMap();
        corsFilterMap.setFilterName("cors");
        corsFilterMap.addURLPattern(filterMapping);
        ctx.addFilterDef(corsFilter);
        ctx.addFilterMap(corsFilterMap);

        tomcat.start();

        Map<String,List<String>> reqHead = new HashMap<String,List<String>>();
        if (origin != null) {
            List<String> values = new ArrayList<String>();
            if (SAME_ORIGIN.equals(origin)) {
                values.add(origin + ":" + getPort());
            } else {
                values.add(origin);
            }
            reqHead.put(CorsFilter.REQUEST_HEADER_ORIGIN, values);
        }
        if (accessControl != null) {
            List<String> values = new ArrayList<String>();
            values.add(accessControl);
            reqHead.put(CorsFilter.REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD, values);
        }

        ByteChunk out = new ByteChunk();
        int rc = methodUrl("http://localhost:" + getPort() + "/target", out, 300000, reqHead, null, method, false);

        if (allow) {
            Assert.assertEquals(200, rc);
        } else {
            Assert.assertEquals(403, rc);
        }
    }
}
