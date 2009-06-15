package org.apache;

import org.apache.catalina.ha.session.TestSerializablePrincipal;
import org.apache.catalina.startup.TestTomcat;
import org.apache.catalina.tribes.test.TribesTestSuite;
import org.apache.el.lang.TestELSupport;
import org.apache.el.parser.TestELParser;
import org.apache.tomcat.util.http.TestCookies;
import org.apache.tomcat.util.res.TestStringManager;

import junit.framework.Test;
import junit.framework.TestSuite;

public class TestAll {

    public static Test suite() {
        TestSuite suite = new TestSuite("Test for org.apache");
        // o.a.catalina.ha.session
        suite.addTestSuite(TestSerializablePrincipal.class);
        // o.a.catalina.startup
        suite.addTestSuite(TestTomcat.class);
        // o.a.tomcat.util.http
        suite.addTestSuite(TestCookies.class);
        // Tribes
        // suite.addTest(TribesTestSuite.suite());
        // o.a.el
        suite.addTestSuite(TestELSupport.class);
        suite.addTestSuite(TestELParser.class);
        // o.a.tomcat.util
        suite.addTestSuite(TestStringManager.class);
        
        return suite;
    }

}
