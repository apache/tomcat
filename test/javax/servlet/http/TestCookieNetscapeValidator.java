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
package javax.servlet.http;

import java.util.BitSet;

import org.junit.Test;

/**
 * Basic tests for Cookie in default configuration.
 */
public class TestCookieNetscapeValidator {

    private NetscapeValidator validator = new NetscapeValidator();

    @Test
    public void actualCharactersAllowedInName() {
        // "any character except comma, semicolon and whitespace"
        // also disallow '=' as that is interpreted as a delimiter by browsers
        BitSet allowed = new BitSet(256);
        allowed.or(TestCookie.CHAR);
        allowed.andNot(TestCookie.CTL);
        allowed.clear(';');
        allowed.clear(',');
        allowed.clear(' ');
        allowed.clear('=');
        TestCookie.checkCharInName(validator, allowed);
    }
}
