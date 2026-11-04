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
package org.apache.jasper.optimizations;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class TestStringInterpreterEnum {

    private static class TestInterpreter extends StringInterpreterEnum {

        public String evaluate(Class<?> enumType, String term) {
            return coerceToOtherType(enumType, term, false);
        }
    }


    /*
     * Enum nested in a class so the binary name differs from the canonical
     * name by a '$' separator.
     */
    public enum NestedEnum {
        VALUE
    }


    @Test
    public void testBadTerms() {
        String[] badTerms = new String[] { "", "mis format", "fudge", ";", ".", "\"", " " };
        for (String badTerm : badTerms) {
            new TestInterpreter().evaluate(TimeUnit.class, badTerm);
        }
    }


    @Test
    public void testNestedEnumUsesCanonicalName() {
        // The generated code must reference the canonical name (dot
        // separated). The binary name contains '$' and is not valid Java
        // source.
        String result = new TestInterpreter().evaluate(NestedEnum.class, "VALUE");
        Assert.assertEquals(NestedEnum.class.getCanonicalName() + ".VALUE", result);
    }
}