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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.http.parser.ChunkExtension.State;

public class TestChunkExtension {

    @Test
    public void testEmpty() {
        doTest("\r\n", true);
    }

    @Test
    public void testInvalid() {
        doTest("x\r\n", false);
    }

    @Test
    public void testNoToken01() {
        doTest(";\r\n", false);
    }

    @Test
    public void testNoToken02() {
        doTest(" ;\r\n", false);
    }

    @Test
    public void testNoToken03() {
        doTest("; \r\n", false);
    }

    @Test
    public void testNoToken04() {
        doTest(";\t\r\n", false);
    }

    @Test
    public void testInvalidToken01() {
        doTest("; =\r\n", false);
    }

    @Test
    public void testTokenOnly01() {
        doTest("; abc\r\n", true);
    }

    @Test
    public void testTokenOnly02() {
        doTest("; abc \r\n", true);
    }

    @Test
    public void testTokenOnly03() {
        doTest("; abc  \r\n", true);
    }

    @Test
    public void testTokenToken01() {
        doTest(";abc=abc\r\n", true);
    }

    @Test
    public void testTokenToken02() {
        doTest("; abc = abc \r\n", true);
    }

    @Test
    public void testTokenQs01() {
        doTest("; abc =\"\"\r\n", true);
    }

    @Test
    public void testTokenQs02() {
        doTest("; abc =\"abc\"\r\n", true);
    }

    @Test
    public void testTokenQs03() {
        doTest("; abc =\"a\tbc\"\r\n", true);
    }

    @Test
    public void testTokenInvalidQs01() {
        doTest("; abc =\"a\rbc\"\r\n", false);
    }

    @Test
    public void testTokenInvalidQs02() {
        doTest("; abc =\"a\\bc\"\r\n", false);
    }

    @Test
    public void testTokenInvalidQs03() {
        doTest("; abc =\"a\u007f\"\r\n", false);
    }

    @Test
    public void testTokenInvalid01() {
        doTest("; abc =\r\n", false);
    }

    @Test
    public void testTokenInvalid02() {
        doTest("; abc ==\r\n", false);
    }

    @Test
    public void testTokenInvalid03() {
        doTest(";a=b=c\r\n", false);
    }

    @Test
    public void testTokenInvalid04() {
        doTest(";a\"r\n", false);
    }

    @Test
    public void testTokenInvalid05() {
        doTest(";a \"r\n", false);
    }

    @Test
    public void testValidValid() {
        doTest(";abc=def;ghi=jkl\r\n", true);
    }

    @Test
    public void testValidInvalid() {
        doTest(";abc=def;=\r\n", false);
    }

    private void doTest(String input, boolean valid) {
        byte[] bytes = input.getBytes(StandardCharsets.ISO_8859_1);

        try {
            // This state assumes either ';' or CRLF will follow, preceded by optional white space.
            State state = State.POST_VALUE;
            for (byte b : bytes) {
                state = ChunkExtension.parse(b, state);
                /*
                 * The test values all end in \r\n but ChunkExtension only looks for \r. In real usage the
                 * ChunkedInputFilter then parses the CRLF.
                 */
                if (state == State.CR) {
                    break;
                }
            }
            Assert.assertTrue("The input was invalid but no exception was thrown", valid);
            Assert.assertEquals("Parsing ended at state other than CR", State.CR, state);
        } catch (IOException ioe) {
            Assert.assertFalse("The input was valid but an exception was thrown", valid);
        }
    }
}
