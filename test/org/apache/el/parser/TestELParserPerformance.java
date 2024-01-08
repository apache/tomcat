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
package org.apache.el.parser;

import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.collections.SynchronizedStack;

/*
 * This is a relative performance test so it remains part of the standard test run.
 */
public class TestELParserPerformance {

    /*
     * Test to explore if re-using Parser instances is faster.
     *
     * Tests on my laptop show:
     * - overhead by introducing the stack is in the noise for parsing even the
     *   simplest expression
     * - efficiency from re-using the ELParser is measurable for even a single
     *   reuse of the parser
     * - with large numbers of parses (~10k) performance for a trivial parse is
     *   three times faster
     * - around the 100 iterations mark GC overhead adds significant noise to
     *   the results - for consistent results you either need fewer parses to
     *   avoid triggering GC or more parses so the GC effects are evenly
     *   distributed between the runs
     *
     * Note that the test is single threaded.
     */
    @Test
    public void testParserInstanceReuse() throws ParseException {
        final int runs = 20;
        final int parseIterations = 10000;

        long reinitTotalTime = 0;
        long newTotalTime = 0;

        for (int j = 0; j < runs; j ++) {
            long start = System.nanoTime();
            SynchronizedStack<ELParser> stack = new SynchronizedStack<>();

            for (int i = 0; i < parseIterations; i ++) {
                ELParser parser = stack.pop();
                if (parser == null) {
                    parser = new ELParser(new StringReader("${'foo'}"));
                } else {
                    parser.ReInit(new StringReader("${'foo'}"));
                }
                parser.CompositeExpression();
                stack.push(parser);
            }
            long end = System.nanoTime();
            reinitTotalTime +=  (end - start);

            System.out.println(parseIterations +
                    " iterations using ELParser.ReInit(...) took " + (end - start) + "ns");
        }

        for (int j = 0; j < runs; j ++) {
            long start = System.nanoTime();
            for (int i = 0; i < parseIterations; i ++) {
                ELParser parser = new ELParser(new StringReader("${'foo'}"));
                parser.CompositeExpression();
            }
            long end = System.nanoTime();
            newTotalTime +=  (end - start);

            System.out.println(parseIterations +
                    " iterations using    new ELParser(...) took " + (end - start) + "ns");
        }

        Assert.assertTrue("Using new ElParser() was faster then using ELParser.ReInit", reinitTotalTime < newTotalTime);
    }
}
