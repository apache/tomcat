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
}
