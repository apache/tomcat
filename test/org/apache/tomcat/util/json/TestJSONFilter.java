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

    @Parameterized.Parameters(name = "{index}: input[{0}], output[{1}]")
    public static Collection<Object[]> parameters() {
        Collection<Object[]> parameterSets = new ArrayList<>();

        // Empty
        parameterSets.add(new String[] { "", "" });

        // Must escape
        parameterSets.add(new String[] { "\"", "\\u0022" });
        parameterSets.add(new String[] { "\\", "\\u005C" });
        // Sample of controls
        parameterSets.add(new String[] { "\t", "\\u0009" });
        parameterSets.add(new String[] { "\n", "\\u000A" });
        parameterSets.add(new String[] { "\r", "\\u000D" });

        // No escape
        parameterSets.add(new String[] { "aaa", "aaa" });

        // Start
        parameterSets.add(new String[] { "\naaa", "\\u000Aaaa" });
        parameterSets.add(new String[] { "\n\naaa", "\\u000A\\u000Aaaa" });

        // Middle
        parameterSets.add(new String[] { "aaa\naaa", "aaa\\u000Aaaa" });
        parameterSets.add(new String[] { "aaa\n\naaa", "aaa\\u000A\\u000Aaaa" });

        // End
        parameterSets.add(new String[] { "aaa\n", "aaa\\u000A" });
        parameterSets.add(new String[] { "aaa\n\n", "aaa\\u000A\\u000A" });

        // Start, middle and end
        parameterSets.add(new String[] { "\naaa\naaa\n", "\\u000Aaaa\\u000Aaaa\\u000A" });
        parameterSets.add(new String[] { "\n\naaa\n\naaa\n\n", "\\u000A\\u000Aaaa\\u000A\\u000Aaaa\\u000A\\u000A" });

        // Multiple
        parameterSets.add(new String[] { "\n\n", "\\u000A\\u000A" });

        return parameterSets;
    }

    @Parameter(0)
    public String input;

    @Parameter(1)
    public String output;

    @Test
    public void testStringEscaping() {
        Assert.assertEquals(output, JSONFilter.escape(input));;
    }
}
