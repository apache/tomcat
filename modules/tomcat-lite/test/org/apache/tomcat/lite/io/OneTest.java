/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;

import org.apache.tomcat.lite.TestMain;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.io.MemoryIOConnector;
import org.apache.tomcat.lite.io.MemoryIOConnector.MemoryIOChannel;

import junit.framework.TestCase;

public class OneTest extends TestCase {

    public void setUp() throws Exception {
        TestMain.getTestServer();
    }

    public void tearDown() throws IOException {
    }


    public void testOne() throws Exception {

    }
}
