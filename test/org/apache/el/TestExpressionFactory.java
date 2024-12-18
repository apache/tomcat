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

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ExpressionFactory;

import org.junit.Assert;
import org.junit.Test;

import org.apache.jasper.el.ELContextImpl;

public class TestExpressionFactory {

    @Test(expected = NullPointerException.class)
    public void testCreateValueExpression2ParamNullExpectedType() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        Assert.assertNotNull(factory);

        factory.createValueExpression("foo", null);
    }


    @Test(expected = NullPointerException.class)
    public void testCreateValueExpression3ParamNullExpectedType() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        Assert.assertNotNull(factory);

        ELContext context = new ELContextImpl();
        Assert.assertNotNull(context);

        factory.createValueExpression(context, "foo", null);
    }


    @Test
    public void testCoerceToTypeString() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        TestObject testObjectA = new TestObject();
        String result = factory.coerceToType(testObjectA, String.class);
        Assert.assertEquals(TestObject.OK, result);
    }


    @Test(expected = ELException.class)
    public void testCoerceToTypeStringThrowsException() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        TestObjectException testObjectA = new TestObjectException();
        factory.coerceToType(testObjectA, String.class);
    }


    private static class TestObject{

        private static final String OK = "OK";
        @Override
        public String toString() {
            return OK;
        }
    }

    private static class TestObjectException{

        @Override
        public String toString() {
            throw new RuntimeException();
        }
    }
}
