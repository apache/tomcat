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

import org.apache.jasper.el.ELContextImpl;

public class TestBeanELResolver {

    @Test
    public void testBug53421() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl();

        Bean bean = new Bean();

        ValueExpression varBean =
            factory.createValueExpression(bean, Bean.class);
        context.getVariableMapper().setVariable("bean", varBean);


        ValueExpression ve = factory.createValueExpression(
                context, "${bean.valueA}", String.class);
        Exception e = null;
        try {
            ve.getValue(context);
        } catch (PropertyNotFoundException pnfe) {
            e = pnfe;
        }
        Assert.assertTrue("Wrong exception type",
                e instanceof PropertyNotFoundException);
        String type = Bean.class.getName();
        @SuppressWarnings("null") // Assert above prevents msg being null
        String msg = e.getMessage();
        Assert.assertTrue("No reference to type [" + type +
                "] where property cannot be found in [" + msg + "]",
                msg.contains(type));
    }

    private static class Bean {

        @SuppressWarnings("unused")
        public void setValueA(String valueA) {
            // NOOP
        }
    }
}
