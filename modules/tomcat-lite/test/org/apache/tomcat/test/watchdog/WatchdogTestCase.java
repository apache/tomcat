/*
 */
package org.apache.tomcat.test.watchdog;

import java.util.Properties;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.apache.tomcat.integration.DynamicObject;
import org.apache.tomcat.integration.simple.AntProperties;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class WatchdogTestCase implements Test {
    String testName;

    Element watchE;

    private Properties props;

    private WatchdogTestCase delegate;
    private WatchdogClient wc;
    
    public WatchdogTestCase(String s) throws Throwable {
        String[] comp = s.split(";");
        if (comp.length < 2) {
            return;
        }
        Class c = Class.forName(comp[1]);
        wc = (WatchdogClient) c.newInstance();
        TestSuite suite = (TestSuite) wc.getSuite();
        // need to encode the base, file, etc in the test name

        System.err.println(s);

        for (int i = 0; i < suite.testCount(); i++) {
            WatchdogTestCase t = (WatchdogTestCase) suite.testAt(i);
            if (s.equals(t.getName())) {
                delegate = t;
                return;
            }
        }
    }

    public WatchdogTestCase(Element watchE, Properties props, String testName) {
        this.testName = testName;
        this.watchE = watchE;
        this.props = props;
    }

    public int countTestCases() {
        return 1;
    }

    public String getName() {
        return testName;
    }

    public void testDummy() {
    }
    
    public void run(TestResult res) {
        if (delegate != null) {
            // Single method run
            wc.beforeSuite();
            delegate.run(res);
            wc.afterSuite(res);
            return;
        }
        if (watchE == null) {
            res.endTest(this);
            return;
        }
        WatchdogTestImpl test = new WatchdogTestImpl();
        NamedNodeMap attrs = watchE.getAttributes();

        for (int i = 0; i < attrs.getLength(); i++) {
            Node n = attrs.item(i);
            String name = n.getNodeName();
            String value = n.getNodeValue();
            value = AntProperties.replaceProperties(value, props, null);
            try {
                new DynamicObject(test.getClass()).setProperty(test, 
                        name, value);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        try {
            res.startTest(this);
            new DynamicObject(test.getClass()).invoke(test, "execute");
        } catch (Throwable e) {
            res.addError(this, e);
            // res.stop();
        }

        if (test.passCount == 1) {
            res.endTest(this);
            return;
        } else {
            if (test.lastError == null) {
                res.addFailure(this, new AssertionFailedError(test.request
                        + " " + test.description + "\n" + test.resultOut));
            } else {
                res.addError(this, test.lastError);
            }
        }
        res.endTest(this);
    }

}
