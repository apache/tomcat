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
package org.apache.catalina.tribes.group.interceptors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.tribes.Channel;

@RunWith(Parameterized.class)
public class TestEncryptInterceptorAlgorithms extends EncryptionInterceptorBaseTest {

    @Parameters(name = "{index} {0}/{1}/{2}")
    public static Collection<Object[]> inputs() {

        List<Object[]> result = new ArrayList<>();
        // Covers all cipher algorithm modes currently listed in Java Standard Names

        // Not supported - Insecure
        result.add(new Object[] { "AES", "NONE", "NoPadding", Boolean.FALSE});
        // Not supported - Insecure - Padding makes no sense if there is no encryption
        result.add(new Object[] { "AES", "NONE", "PKCS5Padding", Boolean.FALSE});

        // Not supported - NoPadding requires fixed block size and cluster messages are variable length
        result.add(new Object[] { "AES", "CBC", "NoPadding", Boolean.FALSE});
        // Supported but not recommended - possible security issues in some configurations - backwards compatibility
        result.add(new Object[] { "AES", "CBC", "PKCS5Padding", Boolean.TRUE});

        // Not supported - JCA provider doesn't included it
        result.add(new Object[] { "AES", "CCM", "NoPadding", Boolean.FALSE});
        // Not supported - JCA provider doesn't included it - CCM doesn't need (support?) padding
        result.add(new Object[] { "AES", "CCM", "PKCS5Padding", Boolean.FALSE});

        // Not supported - NoPadding requires fixed block size and cluster messages are variable length
        result.add(new Object[] { "AES", "CFB", "NoPadding", Boolean.FALSE});
        // Supported but not recommended - possible security issues in some configurations - backwards compatibility
        result.add(new Object[] { "AES", "CFB", "PKCS5Padding", Boolean.TRUE});

        // Not supported - Insecure and/or slow
        result.add(new Object[] { "AES", "CFB8", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "CFB8", "PKCS5Padding", Boolean.FALSE});
        result.add(new Object[] { "AES", "CFB16", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "CFB16", "PKCS5Padding", Boolean.FALSE});
        // large block sizes not tested but will be rejected as well

        // Not supported - Insecure
        result.add(new Object[] { "AES", "CTR", "NoPadding", Boolean.FALSE});
        // Not supported - Configuration not recommended
        result.add(new Object[] { "AES", "CTR", "PKCS5Padding", Boolean.FALSE});

        // Not supported - has minimum length
        result.add(new Object[] { "AES", "CTS", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "CTS", "PKCS5Padding", Boolean.FALSE});

        // Not supported - Insecure
        result.add(new Object[] { "AES", "ECB", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "ECB", "PKCS5Padding", Boolean.FALSE});

        // Default for Tomcat 12 onwards
        result.add(new Object[] { "AES", "GCM", "NoPadding", Boolean.TRUE});
        // Not supported - GCM doesn't need (support?) padding
        result.add(new Object[] { "AES", "GCM", "PKCS5Padding", Boolean.FALSE});

        // Not supported - KW not appropriate for encrypting cluster messages
        result.add(new Object[] { "AES", "KW", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "KW", "PKCS5Padding", Boolean.FALSE});

        // Not supported - KWP not appropriate for encrypting cluster messages
        result.add(new Object[] { "AES", "KWP", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "KWP", "PKCS5Padding", Boolean.FALSE});

        // Not supported - NoPadding requires fixed block size and cluster messages are variable length
        result.add(new Object[] { "AES", "OFB", "NoPadding", Boolean.FALSE});

        // Supported but not recommended - possible security issues in some configurations - backwards compatibility
        result.add(new Object[] { "AES", "OFB", "PKCS5Padding", Boolean.TRUE});

        // Not supported - Insecure and/or slow
        result.add(new Object[] { "AES", "OFB8", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "OFB8", "PKCS5Padding", Boolean.FALSE});
        result.add(new Object[] { "AES", "OFB16", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "OFB16", "PKCS5Padding", Boolean.FALSE});
        // large block sizes not tested but will be rejected as well

        // Not supported - Insecure
        result.add(new Object[] { "AES", "PCBC", "NoPadding", Boolean.FALSE});
        result.add(new Object[] { "AES", "PCBC", "PKCS5Padding", Boolean.FALSE});

        return result;
    }

    @Parameter(0)
    public String algorithm;

    @Parameter(1)
    public String mode;

    @Parameter(2)
    public String padding;

    @Parameter(3)
    public boolean shouldSucceed;

    @Test
    public void testAlgorithm() throws Exception {
        if (shouldSucceed) {
            doTestShouldSucceed();
        } else {
            doTestShouldNotSucceed();
        }
    }

    private void doTestShouldSucceed() throws Exception {
        String transformation = String.format("%s/%s/%s", algorithm, mode, padding);

        src.setEncryptionAlgorithm(transformation);
        src.start(Channel.SND_TX_SEQ);
        dest.setEncryptionAlgorithm(transformation);
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed in " + transformation + " mode",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    private void doTestShouldNotSucceed() throws Exception {
        try {
            String transformation = String.format("%s/%s/%s", algorithm, mode, padding);
            src.setEncryptionAlgorithm(transformation);
            src.start(Channel.SND_TX_SEQ);

            // start() should trigger IllegalArgumentException
            Assert.fail(transformation + " mode is not being refused");
        } catch (IllegalArgumentException iae) {
            // Expected
        }
    }
}
