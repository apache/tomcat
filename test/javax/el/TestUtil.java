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

public class TestUtil {

    @Test
    public void testBug56425a() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("string", "a-b-c-d");
        Assert.assertEquals("a_b_c_d", processor.eval("string.replace(\"-\",\"_\")"));
    }

    @Test
    public void testBug56425b() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("string", "Not used. Any value is fine here");
        Assert.assertEquals("5", processor.eval("string.valueOf(5)"));
    }


    private static class ELProcessor {
        private final ExpressionFactory factory = ExpressionFactory.newInstance();
        private final ELContext context = new ELContextImpl();

        public void defineBean(String name, Object bean) {
            ValueExpression varBean = factory.createValueExpression(bean, bean.getClass());
            context.getVariableMapper().setVariable(name, varBean);
        }

        public Object eval(String expression) {
            ValueExpression ve = factory.createValueExpression(context, "${" + expression + "}", Object.class);
            return ve.getValue(context);
        }
    }
}
