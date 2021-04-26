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
package org.apache.jasper.optimizations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.jasper.compiler.StringInterpreter;
import org.apache.jasper.compiler.StringInterpreterFactory;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestStringInterpreterTagSetters extends TomcatBaseTest {

    @Parameters(name="{index}: {0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        StringInterpreter[] stringInterpreters = new StringInterpreter[] {
                // Warm-up
                // First call will trigger compilation (and therefore be slower)
                // Call both to ensure both are warmed up
                new StringInterpreterWrapper(true, "Enum"),
                new StringInterpreterWrapper(false, "Default"),
                // Compare times of these test runs
                new StringInterpreterWrapper(true, "Enum"),
                new StringInterpreterWrapper(false, "Default"),
                };

        for (StringInterpreter stringInterpreter : stringInterpreters) {
            parameterSets.add(new Object[] { stringInterpreter });
        }
        return parameterSets;
    }


    @Parameter(0)
    public StringInterpreter stringInterpreter;

    @Test
    public void testTag() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, true);
        Context ctxt = (Context) tomcat.getHost().findChild("/test");
        ctxt.getServletContext().setAttribute(StringInterpreter.class.getCanonicalName(), stringInterpreter);

        ByteChunk bc = getUrl("http://localhost:" + getPort() + "/test/bug6nnnn/bug64872b-timeunit.jsp");

        String actual = bc.toString();

        Assert.assertTrue(actual, actual.contains("01 The value of foo is [SECONDS]"));
    }


    /*
     * Wrapper so we can use sensible names in the test labels
     */
    private static class StringInterpreterWrapper implements StringInterpreter {

        private final boolean optimised;
        private final String name;
        private volatile StringInterpreter stringInterpreter = null;

        public StringInterpreterWrapper(boolean optimised, String name) {
            this.optimised = optimised;
            this.name = name;
        }

        @Override
        public String convertString(Class<?> c, String s, String attrName, Class<?> propEditorClass,
                boolean isNamedAttribute) {
            return getStringInterpreter().convertString(c, s, attrName, propEditorClass, isNamedAttribute) ;
        }

        @Override
        public String toString() {
            return name;
        }

        // Lazy init to avoid LogManager init issues when running parameterized tests
        private StringInterpreter getStringInterpreter() {
            if (stringInterpreter == null) {
                synchronized (this) {
                    if (stringInterpreter == null) {
                        if (optimised) {
                            stringInterpreter = new StringInterpreterEnum();
                        } else {
                            stringInterpreter = new StringInterpreterFactory.DefaultStringInterpreter();
                        }
                    }
                }
            }
            return stringInterpreter;
        }
    }
}
