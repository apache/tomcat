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
package org.apache.catalina.startup;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.session.ManagerBase;

/**
 * Tests STRICT_SERVLET_COMPLIANCE sets the attributes it is documented to set.
 */
public class TestStrictServletComplianceAttributes extends TomcatBaseTest {
    private static final String STRICT_SERVLET_COMPLIANCE = "org.apache.catalina.STRICT_SERVLET_COMPLIANCE";
    private static String originalPropertyValue;

    @BeforeClass
    public static void setup() {
        originalPropertyValue = System.getProperty(STRICT_SERVLET_COMPLIANCE);
        System.setProperty(STRICT_SERVLET_COMPLIANCE, "true");

        // If Globals was already initialised in the same JVM (during the tests run through IDE),
        // before the test sets the value to true, skip.
        boolean globalsStrict = Globals.STRICT_SERVLET_COMPLIANCE;
        Assume.assumeTrue("Globals was initialised before setting the property", globalsStrict);
    }

    @AfterClass
    public static void restoreStrictServletCompliance() {
        if (originalPropertyValue == null) {
            System.clearProperty(STRICT_SERVLET_COMPLIANCE);
        } else {
            System.setProperty(STRICT_SERVLET_COMPLIANCE, originalPropertyValue);
        }
    }

    @Test
    public void contextFlagsSetWhenStrictComplianceIsEnabled() {
        Context ctx = getProgrammaticRootContextWithManager();
        Assert.assertTrue("xmlValidation should be true under STRICT_SERVLET_COMPLIANCE.", ctx.getXmlValidation());
        Assert.assertTrue("xmlNamespaceAware should be true under STRICT_SERVLET_COMPLIANCE.", ctx.getXmlNamespaceAware());
        Assert.assertTrue("tldValidation should be true under STRICT_SERVLET_COMPLIANCE.", ctx.getTldValidation());
        Assert.assertFalse("useRelativeRedirects should be false under STRICT_SERVLET_COMPLIANCE.", ctx.getUseRelativeRedirects());
        Assert.assertTrue("alwaysAccessSession should be true under STRICT_SERVLET_COMPLIANCE.", ctx.getAlwaysAccessSession());
        Assert.assertTrue("contextGetResourceRequiresSlash should be true under STRICT_SERVLET_COMPLIANCE.", ctx.getContextGetResourceRequiresSlash());
        Assert.assertTrue("dispatcherWrapsSameObject should be true under STRICT_SERVLET_COMPLIANCE.", ctx.getDispatcherWrapsSameObject());
        Assert.assertFalse("All extension mapped servlets should be checked against welcome files under STRICT_SERVLET_COMPLIANCE.", ctx.isResourceOnlyServlet("jsp"));

        Manager manager = ctx.getManager();
        if (manager instanceof ManagerBase managerBase) {
            Assert.assertTrue("ManagerBase.sessionActivityCheck should be true under STRICT", managerBase.getSessionActivityCheck());
            Assert.assertTrue("ManagerBase.sessionLastAccessAtStart should be true under STRICT", managerBase.getSessionLastAccessAtStart());
        }
    }

}
