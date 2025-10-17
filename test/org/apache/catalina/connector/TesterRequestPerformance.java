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

import org.junit.Test;

import org.apache.tomcat.unittest.TesterRequest;

/*
 * This is an absolute performance test. There is no benefit it running it as part of a standard test run so it is
 * excluded due to the name starting Tester...
 */
public class TesterRequestPerformance {

    @Test
    public void localeParsePerformance() throws Exception {
        TesterRequest req = new TesterRequest();
        req.addHeader("accept-encoding", "en-gb,en");

        long start = System.nanoTime();

        // Takes about 0.3s on a quad core 2.7Ghz 2013 MacBook
        for (int i = 0; i < 10000000; i++) {
            req.parseLocales();
            req.localesParsed = false;
            req.locales.clear();
        }

        long time = System.nanoTime() - start;

        System.out.println(time);
    }
}
