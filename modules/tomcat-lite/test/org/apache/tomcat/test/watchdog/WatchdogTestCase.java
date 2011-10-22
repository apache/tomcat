/*
 */
package org.apache.tomcat.test.watchdog;

import java.util.Properties;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestResult;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class WatchdogTestCase implements Test {
    String testName;

    Element watchE;

    private Properties props;

    private WatchdogClient wc;

    public WatchdogTestCase() {

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
        return testName == null ? "WatchdogTest" : testName;
    }

    public String toString() {
        return getName();
    }

    public void testDummy() {
    }

    public void run(TestResult res) {
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
