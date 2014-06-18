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

import org.junit.Assert;
import org.junit.Test;

public class TestELProcessor {

    @Test
    public void testDefineBean01() {
        ELProcessor elp = new ELProcessor();
        elp.defineBean("bean01", new TesterBean("name01"));
        Assert.assertEquals("name01", elp.eval("bean01.name"));
    }


    @Test(expected=ELException.class)
    public void testEval01() {
        ELProcessor elp = new ELProcessor();
        elp.eval("${1+1}");
    }


    @Test(expected=ELException.class)
    public void testEval02() {
        ELProcessor elp = new ELProcessor();
        elp.eval("#{1+1}");
    }


    @Test
    public void testEval03() {
        ELProcessor elp = new ELProcessor();
        // Note \ is escaped as \\ in Java source code
        String result = (String) elp.eval("'\\\\'");
        Assert.assertEquals("\\", result);
    }


    @Test
    public void testDefineFunctionMethod01() throws Exception {
        ELProcessor elp = new ELProcessor();
        elp.defineFunction("fn", "toBoolean",
                Boolean.class.getMethod("valueOf", String.class));
        Assert.assertEquals(Boolean.valueOf(true),
                elp.eval("fn:toBoolean(true)"));
    }


    @Test
    public void testDefineFunctionName01() throws Exception {
        ELProcessor elp = new ELProcessor();
        // java.lang should be automatically imported so no need for full class
        // name
        elp.defineFunction("fn", "toBoolean", "Boolean", "valueOf");
        Assert.assertEquals(Boolean.valueOf(true),
                elp.eval("fn:toBoolean(true)"));
    }


    @Test
    public void testDefineFunctionName02() throws Exception {
        ELProcessor elp = new ELProcessor();
        elp.defineFunction("fn", "test", "java.lang.Integer", "Integer valueOf(int)");
        Assert.assertEquals(Integer.valueOf(1), elp.eval("fn:test(1)"));
    }


    @Test
    public void testDefineFunctionName03() throws Exception {
        TesterFunctions.resetCallList();
        ELProcessor elp = new ELProcessor();
        elp.defineFunction("fn", "test", "javax.el.TesterFunctions", "void doIt()");
        elp.eval("fn:test()");
        Assert.assertEquals("A", TesterFunctions.getCallList());
    }


    @Test
    public void testDefineFunctionName04() throws Exception {
        TesterFunctions.resetCallList();
        ELProcessor elp = new ELProcessor();
        elp.defineFunction("fn", "test", "javax.el.TesterFunctions", "void doIt(int)");
        elp.eval("fn:test(5)");
        Assert.assertEquals("B", TesterFunctions.getCallList());
    }


    @Test
    public void testDefineFunctionName05() throws Exception {
        TesterFunctions.resetCallList();
        ELProcessor elp = new ELProcessor();
        elp.defineFunction("fn", "test", "javax.el.TesterFunctions", "void doIt(Integer)");
        elp.eval("fn:test(null)");
        Assert.assertEquals("C", TesterFunctions.getCallList());
    }


    @Test
    public void testDefineFunctionName06() throws Exception {
        TesterFunctions.resetCallList();
        ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "javax.el.TesterFunctions", "void doIt(int)");
        elp.eval("doIt(5)");
        Assert.assertEquals("B", TesterFunctions.getCallList());
    }


    @Test
    public void testDefineFunctionName07() throws Exception {
        TesterFunctions.resetCallList();
        ELProcessor elp = new ELProcessor();
        elp.defineFunction("fn", "", "javax.el.TesterFunctions", "void doIt(int)");
        elp.eval("fn:doIt(5)");
        Assert.assertEquals("B", TesterFunctions.getCallList());
    }
}
