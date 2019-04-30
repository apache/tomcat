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
package org.apache.tomcat.util.http.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.tomcat.util.buf.MessageBytes;

@RunWith(Parameterized.class)
public class TesterHostPerformance {

    @Parameters
    public static Collection<Object[]> inputs() {
        List<Object[]> result = new ArrayList<Object[]>();
        result.add(new Object[] { "localhost" });
        result.add(new Object[] { "tomcat.apache.org" });
        result.add(new Object[] { "tomcat.apache.org." });
        result.add(new Object[] { "127.0.0.1" });
        result.add(new Object[] { "255.255.255.255" });
        result.add(new Object[] { "[::1]" });
        result.add(new Object[] { "[0123:4567:89AB:CDEF:0123:4567:89AB:CDEF]" });
        return result;
    }

    @Parameter(0)
    public String hostname;

    private static final int ITERATIONS = 100000000;

    @Test
    public void testParseHost() throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            Host.parse(hostname);
        }
        long time = System.nanoTime() - start;

        System.out.println("St " + hostname + ": " + ITERATIONS + " iterations in " + time + "ns");
        System.out.println("St " + hostname + ": " + ITERATIONS * 1000000000.0/time + " iterations per second");

        MessageBytes mb = MessageBytes.newInstance();
        mb.setString(hostname);
        mb.toBytes();

        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            Host.parse(mb);
        }
        time = System.nanoTime() - start;

        System.out.println("MB " + hostname + ": " + ITERATIONS + " iterations in " + time + "ns");
        System.out.println("MB " + hostname + ": " + ITERATIONS * 1000000000.0/time + " iterations per second");
    }
}
