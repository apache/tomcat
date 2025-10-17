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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Base class for tests that (indirectly) use BeanSupport and want to test both implementations.
 */
@RunWith(Parameterized.class)
public abstract class ELBaseTest {

    @Parameters(name = "{index}: useStandalone[{0}]")
    public static Collection<Object[]> data() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { Boolean.FALSE });
        parameterSets.add(new Object[] { Boolean.TRUE });

        return parameterSets;
    }

    @Parameter(0)
    public boolean useStandalone;

    @Before
    public void setup() {
        // Disable caching so we can switch implementations within a JVM instance.
        System.setProperty("jakarta.el.BeanSupport.doNotCacheInstance", "true");
        // Set up the implementation for this test run
        System.setProperty("jakarta.el.BeanSupport.useStandalone", Boolean.toString(useStandalone));
    }

    /*
     * Double check test has been configured as expected
     */
    @Test
    public void testImplementation() {
        if (useStandalone) {
            Assert.assertEquals(BeanSupportStandalone.class, BeanSupport.getInstance().getClass());
        } else {
            Assert.assertEquals(BeanSupportFull.class, BeanSupport.getInstance().getClass());
        }
    }
}
