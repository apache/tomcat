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

import java.lang.reflect.Method;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

public class TestUtil {
    public static class TestBean {
        public int get1() {
            return 1;
        }

        public int getN(Integer n) {
            return n;
        }

        private int privateMethodNoArgs() {
            return 0;
        }
    }

    @Test
    public void testFindMethodWithNoArgs() throws Exception {
        Method method = Util.findMethod(null, TestBean.class, new TestBean(), "get1", new Class[0], new Object[0]);
        Assert.assertEquals(TestBean.class.getMethod("get1"), method);
    }

    @Test
    public void testFindMethodWithOneArg() throws Exception {
        Method method = Util.findMethod(null, TestBean.class, new TestBean(), "getN", new Class[] { Integer.class },
                new Object[] { 2 });
        Assert.assertEquals(TestBean.class.getMethod("getN", new Class[] { Integer.class }), method);
    }

    @Test
    public void testFindPrivateMethod() throws Exception {
        // verifies that private methods are not located
        try {
            Util.findMethod(null, TestBean.class, new TestBean(), "privateMethodNoArgs",
                    new Class[0], new Object[0]);
            Assert.fail();
        } catch (MethodNotFoundException mnfe) {
            // success, sort of
        }
    }

    @Test
    public void testFindMethodNoSuchMethod() throws Exception {
        try {
            Util.findMethod(null, TestBean.class, new TestBean(), "getNonExistentMethod",
                    new Class[] { Integer.class }, new Object[] { 2 });
            Assert.fail();
        } catch (MethodNotFoundException mnfe) {
            // as expected
        }
    }

    @Test
    public void test01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("sb", new StringBuilder());
        Assert.assertEquals("a", processor.eval("sb.append('a'); sb.toString()"));
    }


    @Test
    public void test02() {
        ELProcessor processor = new ELProcessor();
        processor.getELManager().importClass("java.util.Date");
        Date result = (Date) processor.eval("Date(86400)");
        Assert.assertEquals(86400, result.getTime());
    }


    @Test
    public void testBug56425a() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("string", "a-b-c-d");
        Assert.assertEquals("a_b_c_d", processor.eval("string.replace(\"-\",\"_\")"));
    }

    @Test
    public void testBug56425b() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("string", "Not used. Any value is fine here");
        Assert.assertEquals("5", processor.eval("string.valueOf(5)"));
    }
    
    
}
