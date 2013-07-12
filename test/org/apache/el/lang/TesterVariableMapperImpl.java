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
package org.apache.el.lang;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ExpressionFactory;
import javax.el.TesterELContext;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import org.junit.Assert;
import org.junit.Test;

public class TesterVariableMapperImpl {

    @Test
    public void testSetVariable01() {
        ExpressionFactory factory = ELManager.getExpressionFactory();
        ELContext context = new TesterELContext();
        ValueExpression ve1 =
                factory.createValueExpression(context, "${1}", int.class);
        ValueExpression ve2 =
                factory.createValueExpression(context, "${2}", int.class);
        ValueExpression ve3 =
                factory.createValueExpression(context, "${3}", int.class);

        VariableMapper mapper = new VariableMapperImpl();

        mapper.setVariable("var1", ve1);
        mapper.setVariable("var2", ve2);
        mapper.setVariable("var3", ve3);


        mapper.setVariable("var2", null);

        Assert.assertEquals(ve1, mapper.resolveVariable("var1"));
        Assert.assertNull(mapper.resolveVariable("var2"));
        Assert.assertEquals(ve3, mapper.resolveVariable("var3"));
    }
}
