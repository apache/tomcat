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
package org.apache.catalina.connector;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.LoggingBaseTest;
import org.apache.tomcat.unittest.TesterRequest;

public class TestResponsePerformance extends LoggingBaseTest {

    private final int ITERATIONS = 100000;

    @Test
    public void testToAbsolutePerformance() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response();
        resp.setRequest(req);

        // Warm up
        doHomebrew(resp);
        doUri();

        // Note: Java 9 on my OSX laptop consistently shows doUri() is faster
        //       than doHomebrew(). Worth a closer look for Tomcat 10 on the
        //       assumption it will require java 9

        // To allow for timing differences between runs, a "best of n" approach
        // is taken for this test
        final int bestOf = 5;
        final int winTarget = (bestOf + 1) / 2;
        int homebrewWin = 0;
        int count = 0;

        while (count < bestOf && homebrewWin < winTarget) {
            long homebrew = doHomebrew(resp);
            long uri = doUri();
            log.info("Current 'home-brew': " + homebrew + "ms, Using URI: " + uri + "ms");
            if (homebrew < uri) {
                homebrewWin++;
            }
            count++;
        }
        Assert.assertTrue(homebrewWin == winTarget);
    }


    private long doHomebrew(Response resp) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            resp.toAbsolute("bar.html");
        }
        return System.currentTimeMillis() - start;
    }


    private long doUri() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            URI base = URI.create(
                    "http://localhost:8080/level1/level2/foo.html");
            base.resolve(URI.create("bar.html")).toASCIIString();
        }
        return System.currentTimeMillis() - start;
    }
}
