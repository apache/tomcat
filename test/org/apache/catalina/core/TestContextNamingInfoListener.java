/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.catalina.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.ContextName;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;

@RunWith(Parameterized.class)
public class TestContextNamingInfoListener extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index}: contextPath[{0}], webappVersion[{1}], displayName[{2}], emptyOnRoot[{3}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { "", "", null,  Boolean.FALSE, "/", "/", "ROOT" });
        parameterSets.add(new Object[] { "", "42", null,  Boolean.FALSE, "/", "/", "ROOT##42" });
        parameterSets.add(new Object[] { "", "", null,  Boolean.TRUE, "", "", "" });
        parameterSets.add(new Object[] { "", "42", null,  Boolean.TRUE, "", "", "##42" });
        for (Boolean b: Arrays.asList(Boolean.FALSE, Boolean.TRUE)) {
            parameterSets.add(new Object[] { "/foo", "", null,  b, "/foo", "/foo", "/foo" });
            parameterSets.add(new Object[] { "/foo", "", "My Foo Webapp",  b, "/foo", "/foo", "/foo" });
            parameterSets.add(new Object[] { "/foo", "42", "My Foo Webapp",  b, "/foo", "/foo", "/foo##42" });
            parameterSets.add(new Object[] { "/foo/bar", "", null,  b, "/foo/bar", "/foo/bar", "/foo/bar" });
            parameterSets.add(new Object[] { "/foo/bar", "", "My Foobar Webapp",  b, "/foo/bar", "/foo/bar", "/foo/bar" });
            parameterSets.add(new Object[] { "/foo/bar", "42", "My Foobar Webapp",  b, "/foo/bar", "/foo/bar", "/foo/bar##42" });
            parameterSets.add(new Object[] { "/\u0444\u0443/\u0431\u0430\u0440", "", "\u041C\u043E\u0439 \u0424\u0443\u0431\u0430\u0440 \u0412\u0435\u0431\u0430\u043F\u043F",  b, "/\u0444\u0443/\u0431\u0430\u0440", "/%D1%84%D1%83/%D0%B1%D0%B0%D1%80", "/\u0444\u0443/\u0431\u0430\u0440" });
            parameterSets.add(new Object[] { "/\u0444\u0443/\u0431\u0430\u0440", "42", "\u041C\u043E\u0439 \u0424\u0443\u0431\u0430\u0440 \u0412\u0435\u0431\u0430\u043F\u043F",  b, "/\u0444\u0443/\u0431\u0430\u0440", "/%D1%84%D1%83/%D0%B1%D0%B0%D1%80", "/\u0444\u0443/\u0431\u0430\u0440##42" });
        }

        return parameterSets;
    }

    @Parameter(0)
    public String contextPath;
    @Parameter(1)
    public String webappVersion;
    @Parameter(2)
    public String displayName;
    @Parameter(3)
    public boolean emptyOnRoot;
    @Parameter(4)
    public String expectedContextPath;
    @Parameter(5)
    public String expectedEncodedContextPath;
    @Parameter(6)
    public String expectedName;

    @Test
    public void testListener() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        ContextName cn = new ContextName(contextPath, webappVersion);
        Context ctx = tomcat.addContext(cn.getPath(), null);
        ctx.setName(cn.getName());
        ctx.setWebappVersion(cn.getVersion());
        ctx.setDisplayName(displayName);

        // Enable JNDI - it is disabled by default
        tomcat.enableNaming();

        ContextNamingInfoListener listener = new ContextNamingInfoListener();
        listener.setEmptyOnRoot(emptyOnRoot);

        ctx.addLifecycleListener(listener);

        tomcat.start();

        Assert.assertEquals(LifecycleState.STARTED, ctx.getState());

        NamingResourcesImpl namingResources = ctx.getNamingResources();
        ContextEnvironment pathEnv = namingResources.findEnvironment("context/path");
        ContextEnvironment encodedPathEnv = namingResources.findEnvironment("context/encodedPath");
        ContextEnvironment webappVersionEnv = namingResources.findEnvironment("context/webappVersion");
        ContextEnvironment nameEnv = namingResources.findEnvironment("context/name");
        ContextEnvironment baseNameEnv = namingResources.findEnvironment("context/baseName");
        ContextEnvironment displayNameEnv = namingResources.findEnvironment("context/displayName");

        Assert.assertEquals(expectedContextPath, pathEnv.getValue());
        Assert.assertEquals(expectedEncodedContextPath, encodedPathEnv.getValue());
        Assert.assertEquals(ctx.getWebappVersion(), webappVersionEnv.getValue());
        Assert.assertEquals(expectedName, nameEnv.getValue());
        Assert.assertEquals(ctx.getBaseName(), baseNameEnv.getValue());
        Assert.assertEquals(ctx.getDisplayName(), displayNameEnv.getValue());

        tomcat.stop();
    }

}
