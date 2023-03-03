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
package org.apache.tomcat.util.json;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;


@RunWith(Parameterized.class)
public class TestJSONFilter {

    // Use something ...
    private static final char SUB = 0x1A;
    private static final char STX = 0x02;

    @Parameterized.Parameters(name = "{index}: input[{0}], output[{1}]")
    public static Collection<Object[]> parameters() {
        Collection<Object[]> parameterSets = new ArrayList<>();

        // Empty
        parameterSets.add(new String[] { "", "" });

        // Must escape
        parameterSets.add(new String[] { "\"", "\\\"" });
        parameterSets.add(new String[] { "\\", "\\\\" });
        // Sample of controls
        parameterSets.add(new String[] { "\t", "\\t" });
        parameterSets.add(new String[] { "\n", "\\n" });
        parameterSets.add(new String[] { "\r", "\\r" });

        // No escape
        parameterSets.add(new String[] { "aaa", "aaa" });

        // Start
        parameterSets.add(new String[] { "\naaa", "\\naaa" });
        parameterSets.add(new String[] { "\n\naaa", "\\n\\naaa" });
        parameterSets.add(new String[] { "/aaa", "/aaa" });

        // Middle
        parameterSets.add(new String[] { "aaa\naaa", "aaa\\naaa" });
        parameterSets.add(new String[] { "aaa\n\naaa", "aaa\\n\\naaa" });

        // End
        parameterSets.add(new String[] { "aaa\n", "aaa\\n" });
        parameterSets.add(new String[] { "aaa\n\n", "aaa\\n\\n" });

        // Start, middle and end
        parameterSets.add(new String[] { "\naaa\naaa\n", "\\naaa\\naaa\\n" });
        parameterSets.add(new String[] { "\n\naaa\n\naaa\n\n", "\\n\\naaa\\n\\naaa\\n\\n" });

        // Multiple
        parameterSets.add(new String[] { "\n\n", "\\n\\n" });
        parameterSets.add(new String[] { "\n" + STX + "\n", "\\n\\u0002\\n" });
        parameterSets.add(new String[] { "\n" + STX + "\n" + SUB, "\\n\\u0002\\n\\u001A" });
        parameterSets.add(new String[] { SUB + "\n" + STX + "\n" + SUB, "\\u001A\\n\\u0002\\n\\u001A" });

        return parameterSets;
    }

    @Parameter(0)
    public String input;

    @Parameter(1)
    public String output;

    @Test
    public void testStringEscaping() {
        Assert.assertEquals(output, JSONFilter.escape(input));
    }
}
