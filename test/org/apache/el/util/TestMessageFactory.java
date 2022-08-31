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
package org.apache.el.util;

import java.math.BigDecimal;
import java.util.ResourceBundle;

import org.junit.Assert;
import org.junit.Test;

public class TestMessageFactory {

    MessageFactory messageFactory = new MessageFactory(ResourceBundle.getBundle("org.apache.el.util.TestStrings"));

    @Test
    public void testFormatNone() {
        String input = "1E+2";
        String result = messageFactory.getInternal("messageFactory.formatNone", new BigDecimal(input));
        // Should be unchanged
        Assert.assertEquals(input, result);
    }

    @Test
    public void testFormatNumeric() {
        String input = "1E+2";
        String result = messageFactory.getInternal("messageFactory.formatNumeric", new BigDecimal(input));
        // Should be formatted as an integer
        Assert.assertEquals("100", result);
    }
}
