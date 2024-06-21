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
package jakarta.el;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

public class TestOptionalELResolver {

    @Test(expected = PropertyNotFoundException.class)
    public void testIssue176WithoutOptionalResolverOptionalEmpty() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterBeanA beanA = new TesterBeanA();

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.name}", String.class);
        ve.getValue(context);
    }


    @Test(expected = PropertyNotFoundException.class)
    public void testIssue176WithoutOptionalResolverOptionalPresent() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterBeanA beanA = new TesterBeanA();
        TesterBeanB beanB = new TesterBeanB("test");
        beanA.setBeanB(beanB);

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.name}", String.class);
        ve.getValue(context);
    }


    @Test
    public void testIssue176WithOptionalResolverOptionalEmptyWithProperty() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new OptionalELResolver());

        TesterBeanA beanA = new TesterBeanA();

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.name}", String.class);
        Object result = ve.getValue(context);

        Assert.assertEquals("", result);
    }


    @Test
    public void testIssue176WithOptionalResolverOptionalPresentWithProperty() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new OptionalELResolver());

        TesterBeanA beanA = new TesterBeanA();
        TesterBeanB beanB = new TesterBeanB("test");
        beanA.setBeanB(beanB);

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.name}", String.class);
        Object result = ve.getValue(context);

        Assert.assertEquals("test", result);
    }


    @Test
    public void testIssue176WithOptionalResolverOptionalEmptyWithoutProperty() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new OptionalELResolver());

        TesterBeanA beanA = new TesterBeanA();

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt}", TesterBeanB.class);
        Object result = ve.getValue(context);

        Assert.assertNull(result);
    }


    @Test
    public void testIssue176WithOptionalResolverOptionalPresentWithoutProperty() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new OptionalELResolver());

        TesterBeanA beanA = new TesterBeanA();
        TesterBeanB beanB = new TesterBeanB("test");
        beanA.setBeanB(beanB);

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt}", TesterBeanB.class);
        Object result = ve.getValue(context);

        Assert.assertEquals(beanB, result);
    }


    @Test
    public void testIssue176WithoutOptionalResolverOptionalEmptyWithMap() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterBeanA beanA = new TesterBeanA();

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.map(b -> b.name)}", Optional.class);
        Object result = ve.getValue(context);

        Assert.assertNotNull(result);
        Assert.assertEquals(Optional.class, result.getClass());
        Assert.assertTrue(((Optional<?>) result).isEmpty());
    }


    @Test
    public void testIssue176WithoutOptionalResolverOptionalPresentWithMap() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterBeanA beanA = new TesterBeanA();
        TesterBeanB beanB = new TesterBeanB("test");
        beanA.setBeanB(beanB);

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.map(b -> b.name)}", Optional.class);
        Object result = ve.getValue(context);

        Assert.assertNotNull(result);
        Assert.assertEquals(Optional.class, result.getClass());
        Assert.assertEquals("test", ((Optional<?>) result).get());
    }


    @Test
    public void testIssue176WithOptionalResolverOptionalEmptyWithMap() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new OptionalELResolver());

        TesterBeanA beanA = new TesterBeanA();

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.map(b -> b.name)}", String.class);
        Object result = ve.getValue(context);

        Assert.assertEquals("", result);
    }


    @Test(expected = MethodNotFoundException.class)
    public void testIssue176WithOptionalResolverOptionalPresentWithMap() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new OptionalELResolver());

        TesterBeanA beanA = new TesterBeanA();
        TesterBeanB beanB = new TesterBeanB("test");
        beanA.setBeanB(beanB);

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.map(b -> b.name)}", String.class);
        ve.getValue(context);
    }


    @Test(expected = MethodNotFoundException.class)
    public void testWithoutOptionalResolverInvokeOnEmpty() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterBeanA beanA = new TesterBeanA();

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.doSomething()}", String.class);
        ve.getValue(context);
    }


    @Test
    public void testWithOptionalResolverInvokeOnEmpty() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new OptionalELResolver());

        TesterBeanA beanA = new TesterBeanA();

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.doSomething()}", String.class);
        Object result = ve.getValue(context);

        Assert.assertEquals("", result);
    }


    @Test(expected = MethodNotFoundException.class)
    public void testWithoutOptionalResolverInvokeOnPresent() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterBeanA beanA = new TesterBeanA();
        TesterBeanB beanB = new TesterBeanB("test");
        beanA.setBeanB(beanB);

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.doSomething()}", String.class);
        ve.getValue(context);
    }


    @Test
    public void testWithOptionalResolverInvokeOnPresent() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new OptionalELResolver());

        TesterBeanA beanA = new TesterBeanA();
        TesterBeanB beanB = new TesterBeanB("test");
        beanA.setBeanB(beanB);

        ValueExpression varBeanA = factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varBeanA);

        ValueExpression ve = factory.createValueExpression(context, "${beanA.beanBOpt.doSomething()}", String.class);
        Object result = ve.getValue(context);

        Assert.assertEquals(beanB.doSomething(), result);
    }
}
