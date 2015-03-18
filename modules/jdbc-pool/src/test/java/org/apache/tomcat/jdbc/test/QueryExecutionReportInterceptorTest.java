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
package org.apache.tomcat.jdbc.test;

import org.apache.tomcat.jdbc.pool.interceptor.QueryExecutionReportInterceptor;
import org.junit.Test;

import java.util.Comparator;

import static org.junit.Assert.assertTrue;

/**
 * @author Tadaya Tsuyukubo
 */
public class QueryExecutionReportInterceptorTest {

    @Test
    public void testComparator() {
        Comparator<String> comparator = new QueryExecutionReportInterceptor.StringAsIntegerComparator();

        verifyRightIsGreater(comparator.compare("1", "2"));
        verifyRightIsGreater(comparator.compare("1", "10"));
        verifyRightIsGreater(comparator.compare("2", "10"));
        verifyRightIsGreater(comparator.compare("10", "100"));
        verifyRightIsGreater(comparator.compare("20", "100"));
        verifyRightIsGreater(comparator.compare("a", "b"));

        verifyLeftIsGreater(comparator.compare("2", "1"));
        verifyLeftIsGreater(comparator.compare("10", "1"));
        verifyLeftIsGreater(comparator.compare("10", "2"));
        verifyLeftIsGreater(comparator.compare("100", "10"));
        verifyLeftIsGreater(comparator.compare("100", "20"));
        verifyLeftIsGreater(comparator.compare("b", "a"));

        verifyEqual(comparator.compare("1", "1"));
        verifyEqual(comparator.compare("a", "a"));
    }

    private void verifyLeftIsGreater(int comparisonResult) {
        assertTrue("left is greater", comparisonResult > 0);
    }

    private void verifyRightIsGreater(int comparisonResult) {
        assertTrue("left is greater", comparisonResult < 0);
    }

    private void verifyEqual(int comparisonResult) {
        assertTrue("equal", comparisonResult == 0);
    }

}
