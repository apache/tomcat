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
package javax.el;

import org.junit.Test;

public class TesterImportHandlerPerformance {

    /*
     * This test is looking at the cost of looking up a class when the standard
     * JSP package imports are present:
     * - java.lang
     * - javax.servlet
     * - javax.servlet.http
     * - javax.servlet.jsp
     *
     * Before optimisation, this test took ~4.6s on markt's desktop
     * After optimisation, this test took ~0.05s on markt's desktop
     */
    @Test
    public void testBug62453() throws Exception {
        long totalTime = 0;
        for (int i = 0; i < 100000; i++) {
            ImportHandler ih = new ImportHandler();
            ih.importPackage("javax.servlet");
            ih.importPackage("javax.servlet.http");
            ih.importPackage("javax.servlet.jsp");
            long start = System.nanoTime();
            ih.resolveClass("unknown");
            long end = System.nanoTime();
            totalTime += (end -start);
        }
        System.out.println("Time taken: " + totalTime + "ns");
    }
}
