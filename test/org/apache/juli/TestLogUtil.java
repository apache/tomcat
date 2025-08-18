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
package org.apache.juli;

import org.junit.Assert;
import org.junit.Test;

public class TestLogUtil {

    @Test
    public void testEscapeForLoggingEmptyString() {
        doTestEscapeForLogging("");
    }


    @Test
    public void testEscapeForLoggingNone() {
        doTestEscapeForLogging("No escaping");
    }


    @Test
    public void testEscapeForLoggingControlStart() {
        doTestEscapeForLogging("\u0006Text", "\\u0006Text");
    }


    @Test
    public void testEscapeForLoggingControlMiddle() {
        doTestEscapeForLogging("Text\u0006Text", "Text\\u0006Text");
    }


    @Test
    public void testEscapeForLoggingControlEnd() {
        doTestEscapeForLogging("Text\u0006", "Text\\u0006");
    }


    @Test
    public void testEscapeForLoggingControlOnly() {
        doTestEscapeForLogging("\u0006", "\\u0006");
    }


    @Test
    public void testEscapeForLoggingControlsStart() {
        doTestEscapeForLogging("\u0006\u0007Text", "\\u0006\\u0007Text");
    }


    @Test
    public void testEscapeForLoggingControlsMiddle() {
        doTestEscapeForLogging("Text\u0006\u0007Text", "Text\\u0006\\u0007Text");
    }


    @Test
    public void testEscapeForLoggingControlsEnd() {
        doTestEscapeForLogging("Text\u0006\u0007", "Text\\u0006\\u0007");
    }


    @Test
    public void testEscapeForLoggingControlsOnly() {
        doTestEscapeForLogging("\u0006\u0007", "\\u0006\\u0007");
    }


    private void doTestEscapeForLogging(String input) {
        doTestEscapeForLogging(input, input);
    }


    private void doTestEscapeForLogging(String input, String expected) {
        String result = LogUtil.escape(input);
        Assert.assertEquals(expected, result);
    }
}