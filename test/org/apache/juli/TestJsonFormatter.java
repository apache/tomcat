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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.json.JSONParser;

public class TestJsonFormatter {

    @Test
    public void testFormat() throws Exception {

        Formatter formatter = new JsonFormatter();
        LogRecord logRecord = new LogRecord(Level.FINE, "Test log message");
        logRecord.setSourceClassName("org.apache.juli.TestJsonFormatter");
        logRecord.setSourceMethodName("testFormat");
        try {
            throw new IllegalStateException("Bad state");
        } catch (IllegalStateException e) {
            logRecord.setThrown(e);
        }

        String result = formatter.format(logRecord);

        // Verify JSON content
        Assert.assertTrue(result.startsWith("{"));
        JSONParser parser = new JSONParser(new StringReader(result));
        LinkedHashMap<String,Object> json = parser.object();
        Assert.assertEquals(json.get("method"), "testFormat");
        @SuppressWarnings("unchecked")
        ArrayList<Object> trace = (ArrayList<Object>) json.get("throwable");
        Assert.assertEquals(trace.get(0), "java.lang.IllegalStateException: Bad state");

    }

}
