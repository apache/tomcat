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
import javax.el.ValueExpression;
import javax.el.ValueReference;

import org.apache.jasper.el.ELContextImpl;

import junit.framework.TestCase;

public class TestValueExpressionImpl extends TestCase {

    public void testGetValueReference() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl();
        
        TesterBeanB beanB = new TesterBeanB();
        beanB.setName("Tomcat");
        ValueExpression var =
            factory.createValueExpression(beanB, TesterBeanB.class);
        context.getVariableMapper().setVariable("beanB", var);

        ValueExpression ve = factory.createValueExpression(
                context, "${beanB.name}", String.class);

        // First check the basics work
        String result = (String) ve.getValue(context);
        assertEquals("Tomcat", result);
        
        // Now check the value reference
        ValueReference vr = ve.getValueReference(context);
        assertNotNull(vr);
        
        assertEquals(beanB, vr.getBase());
        assertEquals("name", vr.getProperty());
    }

 
    public void testBug49345() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl();
        
        TesterBeanA beanA = new TesterBeanA();
        TesterBeanB beanB = new TesterBeanB();
        beanB.setName("Tomcat");
        beanA.setBean(beanB);
        
        ValueExpression var =
            factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", var);

        ValueExpression ve = factory.createValueExpression(
                context, "${beanA.bean.name}", String.class);

        // First check the basics work
        String result = (String) ve.getValue(context);
        assertEquals("Tomcat", result);
        
        // Now check the value reference
        ValueReference vr = ve.getValueReference(context);
        assertNotNull(vr);
        
        assertEquals(beanB, vr.getBase());
        assertEquals("name", vr.getProperty());
    }
}
