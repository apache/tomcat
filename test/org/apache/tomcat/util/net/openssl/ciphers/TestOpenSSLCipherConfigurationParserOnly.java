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
package org.apache.tomcat.util.net.openssl.ciphers;

import java.util.LinkedHashSet;

import org.junit.Assert;
import org.junit.Test;


/*
 * The unit test is independent of OpenSSL version and does not require OpenSSL
 * to be present.
 */
public class TestOpenSSLCipherConfigurationParserOnly {

    @Test
    public void testDefaultSort01() throws Exception {
        // Reproducing a failure observed on Gump with OpenSSL 1.1.x

        // Everything else being equal, AES is preferred
        LinkedHashSet<Cipher> input = new LinkedHashSet<>();
        input.add(Cipher.TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384);
        input.add(Cipher.TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384);
        input.add(Cipher.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384);
        input.add(Cipher.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384);
        LinkedHashSet<Cipher> result = OpenSSLCipherConfigurationParser.defaultSort(input);

        LinkedHashSet<Cipher> expected = new LinkedHashSet<>();
        expected.add(Cipher.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384);
        expected.add(Cipher.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384);
        expected.add(Cipher.TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384);
        expected.add(Cipher.TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384);

        Assert.assertEquals(expected.toString(), result.toString());
    }

    @Test
    public void testDefaultSort02() throws Exception {
        // Reproducing a failure observed on Gump with OpenSSL 1.1.x

        // ECHDE should beat AES
        LinkedHashSet<Cipher> input = new LinkedHashSet<>();
        input.add(Cipher.TLS_RSA_WITH_AES_256_CBC_SHA);
        input.add(Cipher.TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384);
        LinkedHashSet<Cipher> result = OpenSSLCipherConfigurationParser.defaultSort(input);

        LinkedHashSet<Cipher> expected = new LinkedHashSet<>();
        expected.add(Cipher.TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384);
        expected.add(Cipher.TLS_RSA_WITH_AES_256_CBC_SHA);

        Assert.assertEquals(expected.toString(), result.toString());
    }

    @Test
    public void testRename01() throws Exception {
        // EDH -> DHE
        LinkedHashSet<Cipher> result =
                OpenSSLCipherConfigurationParser.parse("EXP-EDH-DSS-DES-CBC-SHA");
        LinkedHashSet<Cipher> expected = new LinkedHashSet<>();
        expected.add(Cipher.TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA);

        Assert.assertEquals(expected, result);
    }
}
