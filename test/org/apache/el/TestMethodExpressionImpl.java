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

package org.apache.el;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;

import org.apache.jasper.el.ELContextImpl;

import junit.framework.TestCase;

public class TestMethodExpressionImpl extends TestCase {

    public void testIsParametersProvided() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl();

        TesterBeanB beanB = new TesterBeanB();
        beanB.setName("Tomcat");
        ValueExpression var =
            factory.createValueExpression(beanB, TesterBeanB.class);
        context.getVariableMapper().setVariable("beanB", var);

        MethodExpression me1 = factory.createMethodExpression(
                context, "${beanB.getName}", String.class, new Class<?>[] {});
        MethodExpression me2 = factory.createMethodExpression(
                context, "${beanB.sayHello('JUnit')}", String.class,
                new Class<?>[] { String.class });

        assertFalse(me1.isParmetersProvided());
        assertTrue(me2.isParmetersProvided());
    }

    public void testInvoke() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl();

        TesterBeanB beanB = new TesterBeanB();
        beanB.setName("Tomcat");
        ValueExpression var =
            factory.createValueExpression(beanB, TesterBeanB.class);
        context.getVariableMapper().setVariable("beanB", var);

        MethodExpression me1 = factory.createMethodExpression(
                context, "${beanB.getName}", String.class, new Class<?>[] {});
        MethodExpression me2 = factory.createMethodExpression(
                context, "${beanB.sayHello('JUnit')}", String.class,
                new Class<?>[] { String.class });
        MethodExpression me3 = factory.createMethodExpression(
                context, "${beanB.sayHello}", String.class,
                new Class<?>[] { String.class });

        assertEquals("Tomcat", me1.invoke(context, null));
        assertEquals("Hello JUnit from Tomcat", me2.invoke(context, null));
        assertEquals("Hello JUnit from Tomcat",
                me2.invoke(context, new Object[] { "JUnit2" }));
        assertEquals("Hello JUnit2 from Tomcat",
                me3.invoke(context, new Object[] { "JUnit2" }));
        assertEquals("Hello JUnit from Tomcat",
                me2.invoke(context, new Object[] { null }));
        assertEquals("Hello null from Tomcat",
                me3.invoke(context, new Object[] { null }));
    }

    public void testInvokeWithSuper() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl();

        TesterBeanA beanA = new TesterBeanA();
        ValueExpression varA =
            factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", varA);
        
        TesterBeanC beanC = new TesterBeanC();        
        beanC.setName("Tomcat");
        ValueExpression varC =
            factory.createValueExpression(beanC, TesterBeanC.class);
        context.getVariableMapper().setVariable("beanC", varC);
        
        MethodExpression me1 = factory.createMethodExpression(context,
                "${beanA.setBean(beanC)}", null ,
                new Class<?>[] { TesterBeanB.class });
        
        me1.invoke(context, null);
        
        assertEquals(beanA.getBean(), beanC);
    }
}
