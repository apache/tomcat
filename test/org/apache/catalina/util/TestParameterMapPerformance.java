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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestParameterMapPerformance {

    private static final int NUM_TESTS = 10;
    private static final int NUM_SKIP = 4;
    private static final int NUM_TEST_ITERATIONS = 1000000;

    private ParameterMap<Integer,Object> baseParams;
    private ParameterMap<Integer,Object> testParams;

    @Test
    public void testCompareStandardAndOptimizedMapConstructor() {
        Map<Integer,Object> values = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            Integer integer = Integer.valueOf(i);
            values.put(integer, integer);
        }

        baseParams = new ParameterMap<>(values);
        baseParams.setLocked(true);

        // warmup
        for (int i = 0; i < NUM_TEST_ITERATIONS; i++) {
            useStandardConstructor();
            useOptimizedMapConstructor();
        }

        List<Long> standardDurations = new ArrayList<>();
        for (int i = 0; i < NUM_TESTS; i++) {
            System.gc();
            long startStandard = System.currentTimeMillis();
            for (int j = 0; j < NUM_TEST_ITERATIONS; j++) {
                useStandardConstructor();
            }
            long duration = System.currentTimeMillis() - startStandard;
            standardDurations.add(Long.valueOf(duration));
            System.out.println("Done with standard in " + duration + "ms");
        }
        /*
         * The CI systems tend to produce outliers that lead to false failures so skip the longest two runs.
         */
        long standardTotalDuration =
                standardDurations.stream().sorted().limit(NUM_TESTS - 2).reduce(Long::sum).get().longValue();

        List<Long> optimizedDurations = new ArrayList<>();
        for (int i = 0; i < NUM_TESTS; i++) {
            System.gc();
            long startOptimized = System.currentTimeMillis();
            for (int j = 0; j < NUM_TEST_ITERATIONS; j++) {
                useOptimizedMapConstructor();
            }
            long duration = System.currentTimeMillis() - startOptimized;
            optimizedDurations.add(Long.valueOf(duration));
            System.out.println("Done with optimized in " + duration + "ms");
        }
        /*
         * The CI systems tend to produce outliers that lead to false failures so skip the longest two runs.
         */
        long optimizedTotalDuration =
                optimizedDurations.stream().sorted().limit(NUM_TESTS - NUM_SKIP).reduce(Long::sum).get().longValue();

        Assert.assertTrue("Standard: " + standardTotalDuration + "ms, Optimized: " + optimizedTotalDuration + "ms",
                optimizedTotalDuration < standardTotalDuration);
    }


    private void useOptimizedMapConstructor() {
        testParams = new ParameterMap<>(baseParams);
        testParams.entrySet();
    }


    private void useStandardConstructor() {
        testParams = new ParameterMap<>();
        testParams.putAll(baseParams);
        testParams.entrySet();
    }
}
