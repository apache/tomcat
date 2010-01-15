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

package org.apache;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.catalina.connector.TestKeepAliveCount;
import org.apache.catalina.connector.TestRequest;
import org.apache.catalina.core.TestStandardContext;
import org.apache.catalina.deploy.TestWebXml;
import org.apache.catalina.ha.session.TestSerializablePrincipal;
import org.apache.catalina.startup.TestTomcat;
import org.apache.catalina.startup.TestTomcatSSL;
import org.apache.el.TestELEvaluation;
import org.apache.el.TestELInJsp;
import org.apache.el.lang.TestELSupport;
import org.apache.jasper.compiler.TestAttributeParser;
import org.apache.jasper.compiler.TestGenerator;
import org.apache.jasper.compiler.TestValidator;
import org.apache.tomcat.util.http.TestCookies;
import org.apache.tomcat.util.res.TestStringManager;

public class TestAll {

    public static Test suite() {
        TestSuite suite = new TestSuite("Test for org.apache");
        // o.a.catalina
        // connector
        suite.addTestSuite(TestKeepAliveCount.class);
        suite.addTestSuite(TestRequest.class);
        // core
        suite.addTestSuite(TestStandardContext.class);
        // ha.session
        suite.addTestSuite(TestSerializablePrincipal.class);
        // startup
        suite.addTestSuite(TestTomcat.class);
        suite.addTestSuite(TestTomcatSSL.class);
        suite.addTestSuite(TestWebXml.class);
        // tribes
        // suite.addTest(TribesTestSuite.suite());
        
        // o.a.el
        suite.addTestSuite(TestELSupport.class);
        suite.addTestSuite(TestELEvaluation.class);
        suite.addTestSuite(TestELInJsp.class);
        
        // o.a.jasper
        suite.addTestSuite(TestAttributeParser.class);
        suite.addTestSuite(TestGenerator.class);
        suite.addTestSuite(TestValidator.class);
        
        // o.a.tomcat.util
        // http
        suite.addTestSuite(TestCookies.class);
        // res
        suite.addTestSuite(TestStringManager.class);
        
        return suite;
    }

}
