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
package org.apache.jasper.compiler;

import org.junit.Assert;
import org.junit.Test;

public class TestSmapStratum {

    @Test
    public void test01() {
        // Formerly part of the main() method in SmapGenerator

        SmapStratum s = new SmapStratum();
        s.addFile("foo.jsp");
        s.addFile("bar.jsp", "/foo/foo/bar.jsp");
        s.addLineData(1, "foo.jsp", 1, 1, 1);
        s.addLineData(2, "foo.jsp", 1, 6, 1);
        s.addLineData(3, "foo.jsp", 2, 10, 5);
        s.addLineData(20, "/foo/foo/bar.jsp", 1, 30, 1);
        s.setOutputFileName("foo.java");

        Assert.assertEquals(
                "SMAP\n" +
                "foo.java\n" +
                "JSP\n" +
                "*S JSP\n" +
                "*F\n" +
                "+ 0 foo.jsp\n" +
                "foo.jsp\n" +
                "+ 1 bar.jsp\n" +
                "foo/foo/bar.jsp\n" +
                "*L\n" +
                "1:1\n" +
                "2:6\n" +
                "3,2:10,5\n" +
                "20#1:30\n" +
                "*E\n",
                s.getSmapString());
    }
}
