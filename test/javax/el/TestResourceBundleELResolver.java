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

import java.util.Enumeration;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import org.junit.Assert;
import org.junit.Test;

import org.apache.jasper.el.ELContextImpl;

public class TestResourceBundleELResolver {

    @Test
    public void bug53001() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl();

        ResourceBundle rb = new TesterResourceBundle();

        ValueExpression var =
            factory.createValueExpression(rb, ResourceBundle.class);
        context.getVariableMapper().setVariable("rb", var);


        ValueExpression ve = factory.createValueExpression(
                context, "${rb.keys}", String.class);

        MethodExpression me = factory.createMethodExpression(
                context, "${rb.getKeys()}", Enumeration.class, null);

        // Ensure we are specification compliant
        String result1 = (String) ve.getValue(context);
        Assert.assertEquals("???keys???", result1);

        // Check that the method expression does return the keys
        Object result2 = me.invoke(context, null);
        Assert.assertTrue(result2 instanceof Enumeration);
        @SuppressWarnings("unchecked")
        Enumeration<String> e = (Enumeration<String>) result2;

        Assert.assertTrue(e.hasMoreElements());
        Assert.assertEquals("key2", e.nextElement());
        Assert.assertTrue(e.hasMoreElements());
        Assert.assertEquals("key1", e.nextElement());
        Assert.assertFalse(e.hasMoreElements());
    }


    private static class TesterResourceBundle extends ListResourceBundle {

        @Override
        protected Object[][] getContents() {
            return contents;
        }

        private static final Object[][] contents = {
            {"key1","value1"},
            {"key2","value2"}
        };
    }
}
