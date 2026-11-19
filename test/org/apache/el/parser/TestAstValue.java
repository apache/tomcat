/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.parser;

import javax.el.CompositeELResolver;
import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ELProcessor;
import javax.el.PropertyNotFoundException;
import javax.el.TesterELContext;

import org.junit.Assert;
import org.junit.Test;

public class TestAstValue {

    @Test
    public void testNull01() {
        ELProcessor processor = new ELProcessor();
        TesterBeanB beanB = new TesterBeanB();
        Assert.assertNull(beanB.getText());
        processor.defineBean("bean", beanB);
        Object result = processor.eval("bean.text.toLowerCase()");
        Assert.assertNull(result);
    }


    @Test
    public void testLiteralNullWithProperty01() {
        ELProcessor processor = new ELProcessor();
        Assert.assertNull(processor.eval("null.a"));
    }


    @Test
    public void testLiteralNullWithProperty02() {
        ELProcessor processor = new ELProcessor();
        Assert.assertNull(processor.eval("null.a.b"));
    }


    @Test
    public void testLiteralNullWithProperty03() {
        ELProcessor processor = new ELProcessor();
        Assert.assertNull(processor.eval("null['a']"));
    }


    @Test
    public void testLiteralNullWithProperty04() {
        ELProcessor processor = new ELProcessor();
        Assert.assertNull(processor.eval("null['a']['b']"));
    }


    @Test(expected = PropertyNotFoundException.class)
    public void testUnresolvedIntermediateProperty() {
        // The empty CompositeELResolver means unknown will not be resolved.
        ELContext context = new TesterELContext(new CompositeELResolver());
        ELManager.getExpressionFactory()
                .createValueExpression(context, "${'x'.unknown.next}", Object.class)
                .getValue(context);
    }


    @Test(expected = PropertyNotFoundException.class)
    public void testUnresolvedIntermediateMethod() {
        // The empty CompositeELResolver means unknown will not be resolved.
        ELContext context = new TesterELContext(new CompositeELResolver());
        ELManager.getExpressionFactory()
                .createValueExpression(context, "${'x'.unknown().next}", Object.class)
                .getValue(context);
    }


    @Test
    public void testLiteralNullWithMethod01() {
        ELProcessor processor = new ELProcessor();
        Assert.assertNull(processor.eval("null.method()"));
    }


    @Test
    public void testLiteralNullWithMethod02() {
        ELProcessor processor = new ELProcessor();
        Assert.assertNull(processor.eval("null.method().a"));
    }


    @Test
    public void testLiteralNullWithMethod03() {
        ELProcessor processor = new ELProcessor();
        Assert.assertNull(processor.eval("null.a.method()"));
    }


    @Test(expected = PropertyNotFoundException.class)
    public void testLiteralNullWithPropertyAsLvalue() {
        ELProcessor processor = new ELProcessor();
        processor.setValue("null.prop", "a");
    }
}
