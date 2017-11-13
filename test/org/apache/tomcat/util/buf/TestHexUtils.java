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

package org.apache.tomcat.util.buf;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link HexUtils}.
 */
public class TestHexUtils {

    @Test
    public void testGetDec() {
        Assert.assertEquals(0, HexUtils.getDec('0'));
        Assert.assertEquals(9, HexUtils.getDec('9'));
        Assert.assertEquals(10, HexUtils.getDec('a'));
        Assert.assertEquals(15, HexUtils.getDec('f'));
        Assert.assertEquals(10, HexUtils.getDec('A'));
        Assert.assertEquals(15, HexUtils.getDec('F'));
        Assert.assertEquals(-1, HexUtils.getDec(0));
        Assert.assertEquals(-1, HexUtils.getDec('Z'));
        Assert.assertEquals(-1, HexUtils.getDec(255));
        Assert.assertEquals(-1, HexUtils.getDec(-60));
    }
}
