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

import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.ELNode.Nodes;
import org.apache.jasper.compiler.ELParser.TextBuilder;

public class TestELParser {

    @Test
    public void testText() throws JasperException {
        doTestParser("foo");
    }


    @Test
    public void testLiteral() throws JasperException {
        doTestParser("${'foo'}");
    }


    @Test
    public void testVariable() throws JasperException {
        doTestParser("${test}");
    }


    @Test
    public void testFunction01() throws JasperException {
        doTestParser("${do(x)}");
    }


    @Test
    public void testFunction02() throws JasperException {
        doTestParser("${do:it(x)}");
    }


    @Test
    public void testFunction03() throws JasperException {
        doTestParser("${do:it(x,y)}");
    }


    @Test
    public void testFunction04() throws JasperException {
        doTestParser("${do:it(x,y,z)}");
    }


    @Test
    public void testCompound01() throws JasperException {
        doTestParser("1${'foo'}1");
    }


    @Test
    public void testCompound02() throws JasperException {
        doTestParser("1${test}1");
    }


    @Test
    public void testCompound03() throws JasperException {
        doTestParser("${foo}${bar}");
    }


    @Test
    public void testTernary01() throws JasperException {
        doTestParser("${true?true:false}");
    }


    @Test
    public void testTernary02() throws JasperException {
        doTestParser("${a==1?true:false}");
    }


    @Test
    public void testTernary03() throws JasperException {
        doTestParser("${a eq1?true:false}");
    }


    @Test
    public void testTernary04() throws JasperException {
        doTestParser(" ${ a eq 1 ? true : false } ");
    }


    @Test
    public void testTernary05() throws JasperException {
        // Note this is invalid EL
        doTestParser("${aeq1?true:false}");
    }


    @Test
    public void testTernary06() throws JasperException {
        doTestParser("${do:it(a eq1?true:false,y)}");
    }


    @Test
    public void testTernary07() throws JasperException {
        doTestParser(" ${ do:it( a eq 1 ? true : false, y ) } ");
    }

    
    @Test
    public void testTernary08() throws JasperException {
        doTestParser(" ${ do:it ( a eq 1 ? true : false, y ) } ");
    }


    @Test
    public void testTernary09() throws JasperException {
        doTestParser(" ${ do : it ( a eq 1 ? true : false, y ) } ");
    }


    @Test
    public void testTernary10() throws JasperException {
        doTestParser(" ${!empty my:link(foo)} ");
    }


    @Test
    public void testTernaryBug56031() throws JasperException {
        doTestParser("${my:link(!empty registration ? registration : '/test/registration')}");
    }


    private void doTestParser(String input) throws JasperException {
        Nodes nodes = ELParser.parse(input, false);

        TextBuilder textBuilder = new TextBuilder();

        nodes.visit(textBuilder);

        Assert.assertEquals(input, textBuilder.getText());
    }
}
