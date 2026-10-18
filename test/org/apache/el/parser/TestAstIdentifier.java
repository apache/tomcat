/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.parser;

import java.beans.FeatureDescriptor;
import java.util.Iterator;

import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELProcessor;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.PropertyNotFoundException;
import javax.el.ValueExpression;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.jasper.el.ELContextImpl;
import org.apache.tomcat.util.compat.JreCompat;

public class TestAstIdentifier {

    @Test
    public void testImport01() {
        ELProcessor processor = new ELProcessor();
        Object result =
                processor.getValue("Integer.MAX_VALUE",
                        Integer.class);
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), result);
    }


    @Test
    public void testImport02() {
        ELProcessor processor = new ELProcessor();
        processor.getELManager().getELContext().getImportHandler().importStatic(
                "java.lang.Integer.MAX_VALUE");
        Object result =
                processor.getValue("MAX_VALUE",
                        Integer.class);
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), result);
    }


    @Test
    public void testImport03() {
        // getType() must resolve an imported class, just as getValue() does
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ValueExpression ve = processor.getELManager().getExpressionFactory().createValueExpression(context,
                "${Integer}", Object.class);
        Assert.assertNotNull(ve.getValue(context));
        Assert.assertEquals(ELClass.class, ve.getType(context));
    }


    @Test
    public void testImport04() {
        // getType() must resolve a statically imported field, just as
        // getValue() does. The declared type of the field is reported, in the
        // same way as for a bean property.
        ELProcessor processor = new ELProcessor();
        processor.getELManager().getELContext().getImportHandler().importStatic(
                "java.lang.Integer.MAX_VALUE");
        ELContext context = processor.getELManager().getELContext();
        ValueExpression ve = processor.getELManager().getExpressionFactory().createValueExpression(context,
                "${MAX_VALUE}", Object.class);
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), ve.getValue(context));
        Assert.assertEquals(Integer.TYPE, ve.getType(context));
    }


    @Test
    public void testImport05() {
        // The resolver chain must take precedence over the import fallback in
        // getType(), just as it does in getValue(). "Integer" is resolvable
        // both ways: by the custom resolver and via the default class import.
        ELProcessor processor = new ELProcessor();
        processor.getELManager().addELResolver(new ELResolver() {
            @Override
            public Object getValue(ELContext context, Object base, Object property) {
                if (base == null && "Integer".equals(property)) {
                    context.setPropertyResolved(null, property);
                    return "resolved";
                }
                return null;
            }

            @Override
            public Class<?> getType(ELContext context, Object base, Object property) {
                if (base == null && "Integer".equals(property)) {
                    context.setPropertyResolved(null, property);
                    return String.class;
                }
                return null;
            }

            @Override
            public void setValue(ELContext context, Object base, Object property, Object value) {
            }

            @Override
            public boolean isReadOnly(ELContext context, Object base, Object property) {
                return false;
            }

            @Override
            public Class<?> getCommonPropertyType(ELContext context, Object base) {
                return null;
            }

            @Override
            public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
                return null;
            }
        });
        ELContext context = processor.getELManager().getELContext();
        ValueExpression ve = processor.getELManager().getExpressionFactory().createValueExpression(context,
                "${Integer}", Object.class);
        Assert.assertEquals("resolved", ve.getValue(context));
        Assert.assertEquals(String.class, ve.getType(context));
    }


    @Test
    public void testUnresolvedGetType() {
        // A name that getValue() cannot resolve must continue to report a
        // PropertyNotFoundException from getType()
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ValueExpression ve = processor.getELManager().getExpressionFactory().createValueExpression(context,
                "${unresolvedName}", Object.class);
        try {
            ve.getType(context);
            Assert.fail("A PropertyNotFoundException was expected");
        } catch (PropertyNotFoundException e) {
            // Expected
        }
    }


    @Test
    public void testIdentifierStart() {
        /*
         * This test only works on Java 8.
         *
         * Java 8 is the minimum Java version for Tomcat 9.
         *
         * In Java 9, the definition of Java Letter and/or Java Digit has changed.
         */
        Assume.assumeFalse("Test only works on Java 8", JreCompat.isJre9Available());
        for (int i = 0; i < 0xFFFF; i++) {
            if (Character.isJavaIdentifierStart(i)) {
                testIdentifier((char) i, 'b');
            } else {
                try {
                    testIdentifier((char) i, 'b');
                } catch (ELException e) {
                    continue;
                }
                Assert.fail("Expected EL exception for [" + i + "], [" + (char) i + "]");
            }
        }
    }


    @Test
    public void testIdentifierPart() {
        /*
         * This test only works on Java 8.
         *
         * Java 8 is the minimum Java version for Tomcat 9.
         *
         * In Java 9, the definition of Java Letter and/or Java Digit has changed.
         */
        Assume.assumeFalse("Test only works on Java 8", JreCompat.isJre9Available());
        for (int i = 0; i < 0xFFFF; i++) {
            if (Character.isJavaIdentifierPart(i)) {
                testIdentifier('b', (char) i);
            } else {
                try {
                    testIdentifier((char) i, 'b');
                } catch (ELException e) {
                    continue;
                }
                Assert.fail("Expected EL exception for [" + i + "], [" + (char) i + "]");
            }
        }
    }


    private void testIdentifier(char one, char two) {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        String s = "OK";
        ValueExpression var = factory.createValueExpression(s, String.class);

        String identifier = new String(new char[] { one , two });
        context.getVariableMapper().setVariable(identifier, var);

        ValueExpression ve = null;
        try {
            ve = factory.createValueExpression(context, "${" + identifier + "}", String.class);
        } catch (Exception e) {
            throw e;
        }

        Assert.assertEquals(s, ve.getValue(context));
    }
}
