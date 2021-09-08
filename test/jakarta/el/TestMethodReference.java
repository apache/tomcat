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

import java.beans.BeanProperty;

import org.junit.Assert;
import org.junit.Test;

import org.apache.jasper.el.ELContextImpl;

public class TestMethodReference {

    @Test
    public void testGetAnnotationInfo01() {
        // None
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        TesterBean bean = new TesterBean("myBean");

        ValueExpression var = factory.createValueExpression(bean, TesterBean.class);
        context.getVariableMapper().setVariable("bean", var);

        MethodExpression me = factory.createMethodExpression(context, "${bean.getName()}", String.class, null);

        MethodReference result = me.getMethodReference(context);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getAnnotations());
        Assert.assertEquals(0, result.getAnnotations().length);
    }

    @Test
    public void testGetAnnotationInfo02() {
        // @BeanProperty with defaults
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        TesterBean bean = new TesterBean("myBean");

        ValueExpression var = factory.createValueExpression(bean, TesterBean.class);
        context.getVariableMapper().setVariable("bean", var);

        MethodExpression me = factory.createMethodExpression(context, "${bean.getValueD()}", String.class, null);

        MethodReference result = me.getMethodReference(context);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getAnnotations());
        Assert.assertEquals(1, result.getAnnotations().length);
        Assert.assertEquals(BeanProperty.class, result.getAnnotations()[0].annotationType());
    }
}
