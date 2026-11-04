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
package org.apache.tomcat.util.digester;

import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

import org.xml.sax.InputSource;

public class TestCallMethodRule {

    public static class TwoArgBean {

        private String first;
        private String second;
        private int calls;

        public void setTwo(String first, String second) {
            this.first = first;
            this.second = second;
            calls++;
        }

        public String getFirst() {
            return first;
        }

        public String getSecond() {
            return second;
        }

        public int getCalls() {
            return calls;
        }
    }


    private static Digester createDigester() {
        Digester digester = new Digester();
        digester.addObjectCreate("root", TwoArgBean.class.getName());
        digester.addCallMethod("root/call", "setTwo", 2);
        // Only the first parameter has a nested element. The second one is
        // legitimately missing and is passed to the method as null.
        digester.addCallParam("root/call/first", 0);
        return digester;
    }


    @Test
    public void testMissingParameterIsPassedAsNull() throws Exception {
        Digester digester = createDigester();

        TwoArgBean bean = (TwoArgBean) digester
                .parse(new InputSource(new StringReader("<root><call><first>x</first></call></root>")));

        Assert.assertEquals(1, bean.getCalls());
        Assert.assertEquals("x", bean.getFirst());
        Assert.assertNull(bean.getSecond());
    }


    /*
     * Code generation must render a missing (null) parameter as the null
     * literal rather than generating a variable name for it, which previously
     * failed with a NullPointerException.
     */
    @Test
    public void testGenerateCodeWithMissingParameter() throws Exception {
        Digester digester = createDigester();

        digester.startGeneratingCode();
        TwoArgBean bean = (TwoArgBean) digester
                .parse(new InputSource(new StringReader("<root><call><first>x</first></call></root>")));
        String code = digester.getGeneratedCode().toString();
        digester.endGeneratingCode();

        Assert.assertEquals(1, bean.getCalls());
        Assert.assertNull(bean.getSecond());
        Assert.assertTrue("Generated code was: " + System.lineSeparator() + code,
                code.contains(".setTwo(\"x\", null);"));
    }
}
