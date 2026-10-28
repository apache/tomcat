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

package org.apache.tomcat.util.net;

import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.tomcat.util.net.TesterKeystoreGenerator.KeystoreEntry;

/**
 * Tests {@link SSLUtilBase#getKeyManagers()} when a {@code certificateKeyAlias} is configured. The configured alias is
 * used even if its certificate is not valid for TLS server authentication, except in a SunMSCAPI Windows-MY key store:
 * there, if a numbered duplicate of the alias (e.g. {@code "alias (1)"}) carries a certificate that is valid, it is
 * used instead. Only numbered duplicates of the configured alias are considered, and only for that store type.
 */
@RunWith(Parameterized.class)
public class TestSSLUtilBaseConfiguredAliasSelection extends SSLUtilBaseCertificateSelectionTestBase {

    @Test
    public void testConfiguredAliasIsUsedUnchangedWhenNotServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key("localhost-server", SERVER_AUTH));
        assertEku(presented, CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasIsUsedWhenServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, SERVER_AUTH),
                key("localhost-client", CLIENT_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasUsesNumberedDuplicateWhenBaseNotServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key(HOST + " (1)", SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasUnchangedForNonSunMSCAPIStore() throws Exception {
        KeyStore keystore = nonSunMSCAPIKeystore(
                key(HOST, CLIENT_AUTH),
                key(HOST + " (1)", SERVER_AUTH));
        X509Certificate presented = present(HOST, keystore);
        Assert.assertEquals(keystore.getCertificate(HOST), presented);
    }


    @Test
    public void testConfiguredAliasKeepsBaseWhenAlreadyServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, SERVER_AUTH),
                key(HOST + " (1)", CLIENT_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasUsesSecondDuplicateWhenFirstIsNotServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key(HOST + " (1)", CLIENT_AUTH),
                key(HOST + " (2)", SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasSelectsFirstOfSeveralServerAuthDuplicates() throws Exception {
        KeyStore keystore = orderedKeystore(null,
                key("tomcat", CLIENT_AUTH),
                key("tomcat (1)", SERVER_AUTH),
                key("tomcat (2)", SERVER_AUTH));
        X509Certificate presented = present("tomcat", keystore);
        Assert.assertEquals(keystore.getCertificate("tomcat (1)"), presented);
    }


    @Test
    public void testConfiguredAliasSkipsTrustedDuplicateAndUsesNextKeyEntry() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                KeystoreEntry.trusted(HOST + " (1)", HOST, SERVER_AUTH),
                key(HOST + " (2)", SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasFallsBackToBaseWhenNoDuplicateIsServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key(HOST + " (1)", CLIENT_AUTH));
        assertEku(presented, CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasIgnoresAliasNotMatchingDuplicateFormat() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key(HOST + "(1)", SERVER_AUTH));
        assertEku(presented, CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasWithMalformedExtendedKeyUsageIsUsed() throws Exception {
        KeyStore keystore = orderedKeystore("malformed", key("malformed", CLIENT_AUTH), key("server", SERVER_AUTH));
        Assert.assertEquals(keystore.getCertificateChain("malformed")[0], present("malformed", keystore));
    }


    @Test
    public void testConfiguredAliasUsesNumberedDuplicateWhenBaseIsMalformed() throws Exception {
        KeyStore keystore =
                orderedKeystore("malformed", key("malformed", CLIENT_AUTH), key("malformed (1)", SERVER_AUTH));
        assertEku(present("malformed", keystore), Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredMissingAliasFails() {
        Assert.assertThrows(IOException.class, () -> present("missing", key("server", SERVER_AUTH)));
    }


    @Test
    public void testConfiguredTrustedAliasFails() {
        Assert.assertThrows(IOException.class, () -> present("trusted", KeystoreEntry.trusted("trusted", HOST, SERVER_AUTH),
                key("server", SERVER_AUTH)));
    }


    /*
     * In a SunMSCAPI Windows-MY store a certificate without a private key can take the bare friendly name while the
     * key-bearing certificate of the same name is exposed as a numbered duplicate. The configured alias then names a
     * non-key (trusted) entry, so the server-auth numbered duplicate must still be selected rather than failing
     * start-up.
     */
    @Test
    public void testConfiguredTrustedBaseAliasUsesServerAuthDuplicate() throws Exception {
        X509Certificate presented = present(HOST,
                KeystoreEntry.trusted(HOST, HOST, SERVER_AUTH),
                key(HOST + " (1)", SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    /*
     * The numbered duplicates of a Windows-MY friendly name are normally contiguous, but should a gap appear (for
     * example a lower-numbered duplicate was removed) the search must keep scanning past it rather than stop, so a valid
     * server-auth duplicate beyond the gap is still found.
     */
    @Test
    public void testConfiguredAliasSkipsGapInNumberedDuplicates() throws Exception {
        X509Certificate presented = present("tomcat",
                key("tomcat", CLIENT_AUTH),
                key("tomcat (2)", SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }
}
