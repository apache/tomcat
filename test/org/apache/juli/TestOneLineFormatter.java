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

public class TestOneLineFormatter {

    /*
     * A record with no message is legal. The formatter must render it, with the message as an empty string, rather
     * than throw a NullPointerException.
     */
    @Test
    public void testNullMessage() {
        OneLineFormatter formatter = new OneLineFormatter();
        LogRecord logRecord = new LogRecord(Level.FINE, null);
        logRecord.setLoggerName("test");

        String result = formatter.format(logRecord);

        // With an empty message the output ends with the separator between the source and the message followed by
        // the line separator for the next record
        Assert.assertTrue("Unexpected result [" + result + "]",
                result.endsWith(" " + System.lineSeparator()));
    }
}
