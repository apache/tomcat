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

package org.apache.tomcat.util.scan;

import java.util.StringTokenizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;

public class TestJarScanner extends TomcatBaseTest {

    @Test
    public void testJarsToSkipFormat() {

        String jarList = System.getProperty(Constants.SKIP_JARS_PROPERTY);
        assertNotNull("Jar skip list is null", jarList);
        assertFalse("Jar skip list is empty", jarList.isEmpty());
        StringTokenizer tokenizer = new StringTokenizer(jarList, ",");
        String token;
        while (tokenizer.hasMoreElements()) {
            token = tokenizer.nextToken();
            assertTrue("Token \"" + token + "\" does not end with \".jar\"",
                       token.endsWith(".jar"));
            assertEquals("Token \"" + token + "\" contains sub string \".jar\"" +
                         " or separator \",\" is missing",
                         token.length() - ".jar".length(),
                         token.indexOf(".jar"));
        }
    }
}
