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

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.Assert;
import org.junit.Test;

public class TestJdkLoggerFormatter {

    /*
     * A record with no message is legal. The formatter must render it, with the message as an empty string, rather
     * than throw a NullPointerException.
     */
    @Test
    public void testNullMessage() {
        JdkLoggerFormatter formatter = new JdkLoggerFormatter();
        LogRecord logRecord = new LogRecord(Level.FINE, null);
        logRecord.setLoggerName("test");

        String result = formatter.format(logRecord);

        // With an empty message the line has nothing after the padding of the logger name, in particular the
        // string "null" must not appear in place of the message
        Assert.assertTrue("Unexpected result [" + result + "]",
                result.endsWith(System.lineSeparator()));
        Assert.assertFalse("Unexpected result [" + result + "]", result.contains("null"));
    }
}
