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
package org.apache.catalina.ssi;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link SSIConditionalState}.
 */
public class TestSSIConditionalState {

    @Test
    public void testDefaultValues() {
        SSIConditionalState state = new SSIConditionalState();

        Assert.assertFalse(state.branchTaken);
        Assert.assertEquals(0, state.nestingCount);
        Assert.assertFalse(state.processConditionalCommandsOnly);
    }


    @Test
    public void testSetBranchTaken() {
        SSIConditionalState state = new SSIConditionalState();

        state.branchTaken = true;
        Assert.assertTrue(state.branchTaken);
    }


    @Test
    public void testSetNestingCount() {
        SSIConditionalState state = new SSIConditionalState();

        state.nestingCount = 3;
        Assert.assertEquals(3, state.nestingCount);
    }


    @Test
    public void testSetProcessConditionalCommandsOnly() {
        SSIConditionalState state = new SSIConditionalState();

        state.processConditionalCommandsOnly = true;
        Assert.assertTrue(state.processConditionalCommandsOnly);
    }
}
