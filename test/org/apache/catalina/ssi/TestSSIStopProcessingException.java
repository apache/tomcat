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
 * Tests for {@link SSIStopProcessingException}.
 */
public class TestSSIStopProcessingException {

    @Test
    public void testDefaultConstructor() {
        SSIStopProcessingException ex = new SSIStopProcessingException();
        Assert.assertNull(ex.getMessage());
        Assert.assertNull(ex.getCause());
    }


    @Test
    public void testCauseConstructor() {
        RuntimeException cause = new RuntimeException("root cause");
        SSIStopProcessingException ex = new SSIStopProcessingException(cause);
        Assert.assertSame(cause, ex.getCause());
    }


    @Test
    public void testIsException() {
        SSIStopProcessingException ex = new SSIStopProcessingException();
        Assert.assertTrue(ex instanceof Exception);
    }
}
