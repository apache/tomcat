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
package org.apache.catalina.servlets;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.tomcat.util.compat.JrePlatform;

@RunWith(Parameterized.class)
public class TestCGIServletCmdLineArguments {

    private static final Pattern defaultDecodedPatternWindows;

    static {
        /*
         * There are various ways to handle the platform specific behaviour
         * here. This was chosen as it is simple and the tests are run on
         * Windows as part of every release cycle.
         */
        defaultDecodedPatternWindows = Pattern.compile("[a-zA-Z0-9\\Q-_.\\/:\\E]+");

        if (JrePlatform.IS_WINDOWS) {
            Pattern p = null;
            try {
                CGIServlet cgiServlet = new CGIServlet();
                Field f = CGIServlet.class.getDeclaredField("cmdLineArgumentsDecodedPattern");
                f.setAccessible(true);
                p = (Pattern) f.get(cgiServlet);
            } catch (IllegalAccessException | NoSuchFieldException | SecurityException e) {
            }

            Assert.assertNotNull(p);
            Assert.assertEquals(defaultDecodedPatternWindows.toString(), p.toString());
        }
    }

    @Parameterized.Parameters(name = "{index}: argument[{0}], allowed[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> params = new ArrayList<>();
        params.add(new Object[] { "", Boolean.FALSE } );
        params.add(new Object[] { "<", Boolean.FALSE } );
        params.add(new Object[] { "\"", Boolean.FALSE } );
        params.add(new Object[] { "'", Boolean.FALSE } );
        params.add(new Object[] { "|", Boolean.FALSE } );
        params.add(new Object[] { ">", Boolean.FALSE } );

        params.add(new Object[] { ".", Boolean.TRUE } );
        params.add(new Object[] { "-p", Boolean.TRUE } );
        params.add(new Object[] { "--p", Boolean.TRUE } );
        params.add(new Object[] { "/p", Boolean.TRUE } );
        params.add(new Object[] { "abc", Boolean.TRUE } );
        params.add(new Object[] { "a_b_c", Boolean.TRUE } );
        params.add(new Object[] { "123", Boolean.TRUE } );
        params.add(new Object[] { "file.txt", Boolean.TRUE } );
        params.add(new Object[] { "C:\\some\\path\\file.txt", Boolean.TRUE } );

        params.add(new Object[] { "file.txt file2.txt", Boolean.FALSE } );
        params.add(new Object[] { "C:\\some\\path with space\\file.txt", Boolean.FALSE } );
        params.add(new Object[] { "\"C:\\some\\quoted path with space\\file.txt\"", Boolean.FALSE } );

        return params;
    }

    @Parameter(0)
    public String argument;

    @Parameter(1)
    public Boolean allowed;

    @Test
    public void test() {
        if (allowed.booleanValue()) {
            Assert.assertTrue(defaultDecodedPatternWindows.matcher(argument).matches());
        } else {
            Assert.assertFalse(defaultDecodedPatternWindows.matcher(argument).matches());
        }
    }
}