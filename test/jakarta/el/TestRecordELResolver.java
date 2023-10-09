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

import org.junit.Assert;
import org.junit.Test;

public class TestRecordELResolver {

    private static final String TEXT_DATA = "text";
    private static final long LONG_DATA = 1234;


    @Test
    public void testRecordTextField() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterRecordA recordA = new TesterRecordA(TEXT_DATA, LONG_DATA);

        ValueExpression varRecordA = factory.createValueExpression(recordA, TesterRecordA.class);
        context.getVariableMapper().setVariable("recordA", varRecordA);

        ValueExpression ve = factory.createValueExpression(context, "${recordA.text}", String.class);
        String result = ve.getValue(context);

        Assert.assertEquals(TEXT_DATA, result);
    }


    @Test(expected = ELException.class)
    public void testRecordUnknownField() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterRecordA recordA = new TesterRecordA(TEXT_DATA, LONG_DATA);

        ValueExpression varRecordA = factory.createValueExpression(recordA, TesterRecordA.class);
        context.getVariableMapper().setVariable("recordA", varRecordA);

        ValueExpression ve = factory.createValueExpression(context, "${recordA.unknown}", String.class);
        ve.getValue(context);
    }


    @Test
    public void testRecordNumericField() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);

        TesterRecordA recordA = new TesterRecordA(TEXT_DATA, LONG_DATA);

        ValueExpression varRecordA = factory.createValueExpression(recordA, TesterRecordA.class);
        context.getVariableMapper().setVariable("recordA", varRecordA);

        ValueExpression ve = factory.createValueExpression(context, "${recordA.number}", Long.class);
        Long result = ve.getValue(context);

        Assert.assertEquals(LONG_DATA, result.longValue());
    }
}
