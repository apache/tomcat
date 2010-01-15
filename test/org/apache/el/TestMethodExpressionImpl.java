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

        String result1 = (String) me1.invoke(context, null);
        assertFalse(me1.isParmetersProvided());
        String result2 = (String) me2.invoke(context, new Object[] { "JUnit2" });
        assertTrue(me2.isParmetersProvided());
        
        assertNotNull(result1);
        assertNotNull(result2);
    }

}
