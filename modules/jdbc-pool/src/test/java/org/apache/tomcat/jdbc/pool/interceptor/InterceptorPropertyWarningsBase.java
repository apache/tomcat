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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

/**
 * The statement cache and the statement finalizer read their boolean and integer properties with parsers that keep
 * the current value for an unrecognized value. Without a report such a typo is silent; every configuration mistake in
 * these interceptors must leave a warning naming the property and the value that was kept.
 */
abstract class InterceptorPropertyWarningsBase {

    private static final class LogCapture extends org.apache.tomcat.jdbc.test.support.LogCapture {
        LogCapture() {
            super("interceptor-property-warning-canary", StatementCache.class, StatementCache.class, StatementFinalizer.class);
        clear();
        }




        void assertWarned(String property, String value) {
            for (LogRecord record : records.toArray(new LogRecord[0])) {
                String message = record.getMessage();
                if (message != null && message.contains("[" + property + "]") && message.contains(value)) {
                    return;
                }
            }
            Assertions.fail("no warning named the [" + property + "] property and its value [" + value +
                    "]; records: " + records);
        }


    }

    private static Map<String, InterceptorProperty> properties(String... namesAndValues) {
        Map<String, InterceptorProperty> properties = new HashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            properties.put(namesAndValues[i], new InterceptorProperty(namesAndValues[i], namesAndValues[i + 1]));
        }
        return properties;
    }

    @Test
    void statementCacheWarnsAboutInvalidBooleanAndIntegerValues() {
        try (LogCapture log = new LogCapture()) {
            StatementCache cache = new StatementCache();
            cache.setProperties(properties("prepared", "yes", "callable", "1", "max", "many"));

            log.assertWarned("prepared", "yes");
            log.assertWarned("callable", "1");
            log.assertWarned("max", "many");
        }
    }

    @Test
    void statementFinalizerWarnsAboutAnInvalidTraceValue() {
        try (LogCapture log = new LogCapture()) {
            StatementFinalizer finalizer = new StatementFinalizer();
            finalizer.setProperties(properties("trace", "perhaps"));

            log.assertWarned("trace", "perhaps");
        }
    }
}
