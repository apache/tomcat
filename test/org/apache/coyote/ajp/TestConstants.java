/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.ajp;

import org.junit.Assert;
import org.junit.Test;

public class TestConstants {

    @Test
    public void testGetResponseHeaderForCodeInBounds() {
        Assert.assertEquals("Content-Type", Constants.getResponseHeaderForCode(0));
        Assert.assertEquals("WWW-Authenticate", Constants.getResponseHeaderForCode(10));
    }

    @Test
    public void testGetResponseHeaderForCodeOutOfBounds() {
        Assert.assertNull(Constants.getResponseHeaderForCode(-1));
        Assert.assertNull(Constants.getResponseHeaderForCode(Constants.SC_RESP_AJP13_MAX));
        Assert.assertNull(Constants.getResponseHeaderForCode(Integer.MAX_VALUE));
    }
}
