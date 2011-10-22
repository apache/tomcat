/*
 */
package org.apache.tomcat.lite.load;

import org.apache.tomcat.lite.http.LiveHttp1Test;

import junit.framework.TestSuite;

public class LiveHttp5Test extends LiveHttp1Test {

    /**
     * Want to run the same tests few times.
     */
    public static TestSuite suite() {
        TestSuite s = new TestSuite();
        for (int i = 0; i < 5; i++) {
            s.addTestSuite(LiveHttp1Test.class);
        }
        return s;
    }

    public void test100() throws Exception {
        for (int i = 0; i < 100; i++) {
            testSimpleRequest();
            tearDown();
            setUp();

            notFound();
            tearDown();
            setUp();

            testSimpleRequest();
            tearDown();
            setUp();
        }
    }

}
