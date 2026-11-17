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
package org.apache.tomcat.util.modeler;

import org.junit.Assert;
import org.junit.Test;

public class TestRegistryConvertValue {

    private final Registry registry = new Registry();

    @Test
    public void testConvertStringTypes() {
        Assert.assertEquals("value", registry.convertValue(null, "value"));
        Assert.assertEquals("value", registry.convertValue("java.lang.String", "value"));
    }

    @Test
    public void testConvertIntegerTypes() {
        Assert.assertEquals(Integer.valueOf(7), registry.convertValue("int", "7"));
        Assert.assertEquals(Integer.valueOf(7), registry.convertValue("java.lang.Integer", "7"));
        Assert.assertEquals(Long.valueOf(7), registry.convertValue("long", "7"));
        Assert.assertEquals(Long.valueOf(7), registry.convertValue("java.lang.Long", "7"));
        Assert.assertEquals(Short.valueOf((short) 7), registry.convertValue("short", "7"));
        Assert.assertEquals(Short.valueOf((short) 7), registry.convertValue("java.lang.Short", "7"));
        Assert.assertEquals(Byte.valueOf((byte) 7), registry.convertValue("byte", "7"));
        Assert.assertEquals(Byte.valueOf((byte) 7), registry.convertValue("java.lang.Byte", "7"));
    }

    @Test
    public void testConvertFloatingPointTypes() {
        Assert.assertEquals(Double.valueOf(1.5), registry.convertValue("double", "1.5"));
        Assert.assertEquals(Double.valueOf(1.5), registry.convertValue("java.lang.Double", "1.5"));
        Assert.assertEquals(Float.valueOf(1.5f), registry.convertValue("float", "1.5"));
        Assert.assertEquals(Float.valueOf(1.5f), registry.convertValue("java.lang.Float", "1.5"));
    }

    @Test
    public void testConvertBooleanType() {
        Assert.assertEquals(Boolean.TRUE, registry.convertValue("boolean", "true"));
        Assert.assertEquals(Boolean.TRUE, registry.convertValue("java.lang.Boolean", "true"));
    }

    @Test
    public void testConvertCharacterType() {
        Assert.assertEquals(Character.valueOf('x'), registry.convertValue("char", "x"));
        Assert.assertEquals(Character.valueOf('x'), registry.convertValue("java.lang.Character", "x"));
    }

    @Test
    public void testConvertCharacterTypeRejectsOtherLengths() {
        // Exactly one character is required, anything else is an error rather than being truncated or ignored
        Assert.assertThrows(NumberFormatException.class, () -> registry.convertValue("char", ""));
        Assert.assertThrows(NumberFormatException.class, () -> registry.convertValue("char", "xy"));
        Assert.assertThrows(NumberFormatException.class, () -> registry.convertValue("java.lang.Character", "xy"));
    }

    @Test
    public void testConvertNullValueThrowsForNumericAndCharacterTypes() {
        for (String type : new String[] { "int", "long", "short", "byte", "double", "float", "char",
                "java.lang.Double", "java.lang.Float", "java.lang.Character" }) {
            Assert.assertThrows(type, NumberFormatException.class, () -> registry.convertValue(type, null));
        }
    }

    @Test
    public void testConvertNumericBoundaries() {
        Assert.assertEquals(Short.valueOf(Short.MAX_VALUE),
                registry.convertValue("short", "32767"));
        Assert.assertEquals(Byte.valueOf(Byte.MAX_VALUE),
                registry.convertValue("byte", "127"));
    }

    @Test
    public void testConvertInvalidNumberThrows() {
        Assert.assertThrows(NumberFormatException.class, () -> registry.convertValue("int", "abc"));
        Assert.assertThrows(NumberFormatException.class, () -> registry.convertValue("double", "abc"));
        Assert.assertThrows(NumberFormatException.class, () -> registry.convertValue("float", "abc"));
        Assert.assertThrows(NumberFormatException.class, () -> registry.convertValue("short", "70000"));
        Assert.assertThrows(NumberFormatException.class, () -> registry.convertValue("byte", "300"));
    }
}
