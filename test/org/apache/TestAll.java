package org.apache;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.catalina.connector.TestKeepAliveCount;
import org.apache.catalina.connector.TestRequest;
import org.apache.catalina.ha.session.TestSerializablePrincipal;
import org.apache.catalina.startup.TestTomcat;
import org.apache.el.TestELEvaluation;
import org.apache.el.lang.TestELSupport;
import org.apache.tomcat.util.http.TestCookies;
import org.apache.tomcat.util.res.TestStringManager;

public class TestAll {

    public static Test suite() {
        TestSuite suite = new TestSuite("Test for org.apache");
        // o.a.catalina
        // connector
        suite.addTestSuite(TestRequest.class);
        suite.addTestSuite(TestKeepAliveCount.class);
        // ha.session
        suite.addTestSuite(TestSerializablePrincipal.class);
        // startup
        suite.addTestSuite(TestTomcat.class);
        // tribes
        // suite.addTest(TribesTestSuite.suite());
        
        // o.a.el
        suite.addTestSuite(TestELSupport.class);
        suite.addTestSuite(TestELEvaluation.class);
        
        // o.a.tomcat.util
        // http
        suite.addTestSuite(TestCookies.class);
        // res
        suite.addTestSuite(TestStringManager.class);
        
        return suite;
    }

}
