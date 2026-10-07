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
package org.apache.catalina.mbeans;

import java.io.File;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.modeler.Registry;

public class TestContextMBean extends TomcatBaseTest {

    @Test
    public void testFindErrorPageByExceptionType() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getTemporaryDirectory(), "ctxmbean");
        Assert.assertTrue(appDir.mkdirs());
        Context context = tomcat.addContext("", appDir.getAbsolutePath());

        ErrorPage errorPage = new ErrorPage();
        errorPage.setExceptionType("java.lang.RuntimeException");
        errorPage.setLocation("/error");
        ((StandardContext) context).addErrorPage(errorPage);

        tomcat.start();

        MBeanServer server = Registry.getRegistry(null).getMBeanServer();

        Set<ObjectInstance> objectInstances =
                server.queryMBeans(new ObjectName("*:j2eeType=WebModule,*"), null);
        ObjectName oname = null;
        for (ObjectInstance objectInstance : objectInstances) {
            if ("//localhost/".equals(objectInstance.getObjectName().getKeyProperty("name"))) {
                oname = objectInstance.getObjectName();
            }
        }
        Assert.assertNotNull(oname);

        Object result = server.invoke(oname, "findErrorPage", new Object[] { "java.lang.RuntimeException" },
                new String[] { "java.lang.String" });

        Assert.assertNotNull(result);
        Assert.assertTrue(result.toString(), result.toString().contains("/error"));

        // Exception types are matched by exact name, not by assignability
        Assert.assertNull(server.invoke(oname, "findErrorPage", new Object[] { "java.lang.Error" },
                new String[] { "java.lang.String" }));
    }
}
