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

package org.apache.catalina.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestParameterMap {

    private static final String[] TEST_PARAM_VALUES_1 = { "value1" };
    private static final String[] TEST_PARAM_VALUES_2 = { "value2" };
    private static final String[] TEST_PARAM_VALUES_2_UPDATED = { "value2-updated" };
    private static final String[] TEST_PARAM_VALUES_3 = { "value3" };

    private Map<String, String[]> paramMap;

    @Before
    public void setUp() {
        paramMap = new ParameterMap<>();

        paramMap.put("param1", TEST_PARAM_VALUES_1);
        paramMap.put("param2", TEST_PARAM_VALUES_2);
        paramMap.put("param3", TEST_PARAM_VALUES_3);

        assertTrue(paramMap.containsKey("param1"));
        assertArrayEquals(TEST_PARAM_VALUES_1, (String[]) paramMap.get("param1"));
        assertTrue(paramMap.containsKey("param2"));
        assertArrayEquals(TEST_PARAM_VALUES_2, (String[]) paramMap.get("param2"));
        assertTrue(paramMap.containsKey("param3"));
        assertArrayEquals(TEST_PARAM_VALUES_3, (String[]) paramMap.get("param3"));

        final Set<String> keySet = paramMap.keySet();
        assertTrue(keySet.contains("param1"));
        assertTrue(keySet.contains("param2"));
        assertTrue(keySet.contains("param3"));

        paramMap.put("param2", TEST_PARAM_VALUES_2_UPDATED);
        paramMap.remove("param3");

        assertTrue(paramMap.containsKey("param1"));
        assertArrayEquals(TEST_PARAM_VALUES_1, (String[]) paramMap.get("param1"));
        assertTrue(paramMap.containsKey("param2"));
        assertArrayEquals(TEST_PARAM_VALUES_2_UPDATED, (String[]) paramMap.get("param2"));
        assertFalse(paramMap.containsKey("param3"));
        assertNull(paramMap.get("param3"));

        assertTrue(keySet.contains("param1"));
        assertTrue(keySet.contains("param2"));
        assertFalse(keySet.contains("param3"));
    }

    @After
    public void tearDown() {
        assertTrue(paramMap.containsKey("param1"));
        assertArrayEquals(TEST_PARAM_VALUES_1, (String[]) paramMap.get("param1"));
        assertTrue(paramMap.containsKey("param2"));
        assertArrayEquals(TEST_PARAM_VALUES_2_UPDATED, (String[]) paramMap.get("param2"));
        assertFalse(paramMap.containsKey("param3"));
        assertNull(paramMap.get("param3"));
    }

    @Test
    public void testMapImmutabilityAfterLocked() {
        ((ParameterMap<String, String[]>) paramMap).setLocked(true);

        try {
            String[] updatedParamValues22 = new String[] { "value2-updated-2" };
            paramMap.put("param2", updatedParamValues22);
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            String[] updatedParamValues22 = new String[] { "value2-updated-2" };
            paramMap.putIfAbsent("param2", updatedParamValues22);
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            final Map<String, String[]> additionalParams = new HashMap<>();
            additionalParams.put("param4", new String[] { "value4" });
            paramMap.putAll(additionalParams);
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            paramMap.merge("param2", new String[] { "value2-merged" }, (a, b) -> (b));
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            paramMap.remove("param2");
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            paramMap.remove("param2", new String[] { "value2-updated" });
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            paramMap.replace("param2", new String[] { "value2-replaced" });
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            paramMap.replace("param2", new String[] { "value2-updated" }, new String[] { "value2-replaced" });
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            paramMap.replaceAll((a, b) -> b);
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }

        try {
            paramMap.clear();
            fail("ParameterMap is not locked.");
        } catch (IllegalStateException expectedException) {
        }
    }

    @Test
    public void testKeySetImmutabilityAfterLocked() {
        ((ParameterMap<String, String[]>) paramMap).setLocked(true);

        final Set<String> keySet = paramMap.keySet();

        try {
            keySet.add("param4");
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            keySet.remove("param2");
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            keySet.removeIf((a) -> "param2".equals(a));
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            keySet.removeAll(Arrays.asList("param1", "param2"));
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            keySet.retainAll(Collections.emptyList());
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            keySet.clear();
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }
    }

    @Test
    public void testValuesImmutabilityAfterLocked() {
        ((ParameterMap<String, String[]>) paramMap).setLocked(true);

        final Collection<String[]> valuesCol = paramMap.values();

        try {
            valuesCol.add(new String[] { "value4" });
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            List<String[]> list = new ArrayList<>();
            list.add(new String[] { "value4" });
            valuesCol.addAll(list);
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            valuesCol.remove(TEST_PARAM_VALUES_1);
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            valuesCol.removeIf((a) -> true);
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            List<String[]> list = new ArrayList<>();
            list.add(TEST_PARAM_VALUES_1);
            valuesCol.removeAll(list);
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            valuesCol.retainAll(Collections.emptyList());
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            valuesCol.clear();
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }
    }

    @Test
    public void testEntrySetImmutabilityAfterLocked() {
        ((ParameterMap<String, String[]>) paramMap).setLocked(true);

        final Set<Map.Entry<String, String[]>> entrySet = paramMap.entrySet();

        try {
            final Map<String, String[]> anotherParamsMap = new HashMap<>();
            anotherParamsMap.put("param4", new String[] { "value4" });
            Map.Entry<String, String[]> anotherEntry = anotherParamsMap.entrySet().iterator().next();
            entrySet.add(anotherEntry);
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            final Map<String, String[]> anotherParamsMap = new HashMap<>();
            anotherParamsMap.put("param4", new String[] { "value4" });
            anotherParamsMap.put("param5", new String[] { "value5" });
            entrySet.addAll(anotherParamsMap.entrySet());
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            final Map.Entry<String, String[]> entry = entrySet.iterator().next();
            entrySet.remove(entry);
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            final Map.Entry<String, String[]> entry = entrySet.iterator().next();
            entrySet.removeIf((a) -> true);
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            Set<Map.Entry<String, String[]>> anotherEntrySet = new HashSet<>(entrySet);
            entrySet.removeAll(anotherEntrySet);
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            entrySet.retainAll(Collections.emptySet());
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }

        try {
            entrySet.clear();
            fail("ParameterMap is not locked.");
        } catch (UnsupportedOperationException expectedException) {
        }
    }
}
