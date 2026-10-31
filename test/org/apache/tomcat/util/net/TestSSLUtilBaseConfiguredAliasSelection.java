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
import java.util.Set;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
                key(HOST, CertType.CLIENT_AUTH),
                key("localhost-server", CertType.SERVER_AUTH));
        assertEku(presented, CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasIsUsedWhenServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CertType.SERVER_AUTH),
                key("localhost-client", CertType.CLIENT_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasUsesNumberedDuplicateWhenBaseNotServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CertType.CLIENT_AUTH),
                key(HOST + " (1)", CertType.SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasUnchangedForNonSunMSCAPIStore() throws Exception {
        KeyStore keystore = nonSunMSCAPIKeystore(
                key(HOST, CertType.CLIENT_AUTH),
                key(HOST + " (1)", CertType.SERVER_AUTH));
        X509Certificate presented = present(HOST, keystore);
        Assert.assertEquals(keystore.getCertificate(HOST), presented);
    }


    @Test
    public void testConfiguredAliasKeepsBaseWhenAlreadyServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CertType.SERVER_AUTH),
                key(HOST + " (1)", CertType.CLIENT_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasUsesSecondDuplicateWhenFirstIsNotServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CertType.CLIENT_AUTH),
                key(HOST + " (1)", CertType.CLIENT_AUTH),
                key(HOST + " (2)", CertType.SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasSelectsFirstOfSeveralServerAuthDuplicates() throws Exception {
        KeyStore keystore = orderedKeystore(null,
                key("tomcat", CertType.CLIENT_AUTH),
                key("tomcat (1)", CertType.SERVER_AUTH),
                key("tomcat (2)", CertType.SERVER_AUTH));
        X509Certificate presented = present("tomcat", keystore);
        Assert.assertEquals(keystore.getCertificate("tomcat (1)"), presented);
    }


    @Test
    public void testConfiguredAliasSkipsTrustedDuplicateAndUsesNextKeyEntry() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CertType.CLIENT_AUTH),
                KeystoreEntry.trusted(HOST + " (1)"),
                key(HOST + " (2)", CertType.SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasFallsBackToBaseWhenNoDuplicateIsServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CertType.CLIENT_AUTH),
                key(HOST + " (1)", CertType.CLIENT_AUTH));
        assertEku(presented, CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasIgnoresAliasNotMatchingDuplicateFormat() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CertType.CLIENT_AUTH),
                key(HOST + "(1)", CertType.SERVER_AUTH));
        assertEku(presented, CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasWithMalformedExtendedKeyUsageIsUsed() throws Exception {
        KeyStore keystore = orderedKeystore("malformed", key("malformed", CertType.CLIENT_AUTH), key("server", CertType.SERVER_AUTH));
        Assert.assertEquals(keystore.getCertificateChain("malformed")[0], present("malformed", keystore));
    }


    @Test
    public void testConfiguredAliasUsesNumberedDuplicateWhenBaseIsMalformed() throws Exception {
        KeyStore keystore =
                orderedKeystore("malformed", key("malformed", CertType.CLIENT_AUTH), key("malformed (1)", CertType.SERVER_AUTH));
        assertEku(present("malformed", keystore), Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredMissingAliasFails() {
        Assert.assertThrows(IOException.class, () -> present("missing", key("server", CertType.SERVER_AUTH)));
    }


    @Test
    public void testConfiguredTrustedAliasFails() {
        Assert.assertThrows(IOException.class, () -> present("trusted", KeystoreEntry.trusted("trusted"),
                key("server", CertType.SERVER_AUTH)));
    }


    /*
     * After the SunMSCAPI numbered-duplicate swap, the certificate served to clients is a numbered duplicate of the
     * configured alias. The TLS certificate reload listener (via SSLHostConfig.certificatesExpiringBefore()) must
     * monitor that served certificate, not the certificate at the configured alias. Non-exportable keys route
     * getKeyManagers() through the production ksUsed == ks path a real SunMSCAPI store uses. Scoped to JSSE: for
     * OpenSSL the served certificate is held in the native layer and resolved by a separate code path.
     */
    @Test
    public void testReloadListenerMonitorsTheServedCertificate() throws Exception {
        Assume.assumeTrue("JSSE".equals(connectorName));
        KeyStore keystore = nonExportableKeystore(key(HOST, CertType.CLIENT_AUTH), key(HOST + " (1)", CertType.SERVER_AUTH));

        Set<X509Certificate> monitored = certificatesMonitoredForReload(keystore);

        X509Certificate served = (X509Certificate) keystore.getCertificateChain(HOST + " (1)")[0];
        X509Certificate notServed = (X509Certificate) keystore.getCertificateChain(HOST)[0];
        Assert.assertTrue("The served certificate must be monitored for reload", monitored.contains(served));
        Assert.assertFalse("The unserved configured certificate must not be monitored", monitored.contains(notServed));
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
                KeystoreEntry.trusted(HOST),
                key(HOST + " (1)", CertType.SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasStopsAtGapAndKeepsBase() throws Exception {
        X509Certificate presented = present("tomcat",
                key("tomcat", CertType.CLIENT_AUTH),
                key("tomcat (2)", CertType.SERVER_AUTH));
        assertEku(presented, CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    /*
     * For an OpenSSL connector the served certificate is loaded into the native layer, but the reload check must still
     * report it. With exportable keys getKeyManagers() copies only the resolved key into an in-memory store, so
     * OpenSSLContext.getCertificateChain() finds nothing under the configured alias and already falls back to the served
     * alias - no OpenSSL-specific change is needed. (Non-exportable SunMSCAPI keys cannot be loaded by OpenSSL at all,
     * so this exportable case is the only one that runs.)
     */
    @Test
    public void testReloadResolvesServedCertificateWithOpenSSL() throws Exception {
        Assume.assumeTrue(useOpenSSL);
        KeyStore keystore = orderedKeystore(null, key(HOST, CertType.CLIENT_AUTH), key(HOST + " (1)", CertType.SERVER_AUTH));

        Set<X509Certificate> monitored = certificatesMonitoredForReload(keystore);

        X509Certificate served = (X509Certificate) keystore.getCertificateChain(HOST + " (1)")[0];
        X509Certificate notServed = (X509Certificate) keystore.getCertificateChain(HOST)[0];
        Assert.assertTrue("OpenSSL reload must resolve the served certificate", monitored.contains(served));
        Assert.assertFalse("OpenSSL reload must not report the unserved configured certificate",
                monitored.contains(notServed));
    }


    /*
     * After the SunMSCAPI numbered-duplicate swap the served alias is the numbered duplicate, not the configured alias.
     * This is the alias that logCertificate reports and the reload check resolves. Non-exportable keys route
     * getKeyManagers() through the production ksUsed == ks path.
     */
    @Test
    public void testServedAliasIsTheNumberedDuplicate() throws Exception {
        Assume.assumeTrue("JSSE".equals(connectorName));
        KeyStore keystore = nonExportableKeystore(key(HOST, CertType.CLIENT_AUTH), key(HOST + " (1)", CertType.SERVER_AUTH));
        Assert.assertEquals(HOST + " (1)", servedAlias(keystore));
    }


    /*
     * A SunMSCAPI key that reports a null format but is still exportable keeps the original key store in use
     * (ksUsed == ks) and wraps each key manager, like a real non-exportable key, while remaining loadable by OpenSSL.
     * In that wrapped path a lookup of the configured alias still resolves its own (clientAuth) chain, so the served
     * alias - not that lookup - must drive what OpenSSL installs.
     */
    @Test
    public void testServedAliasResolvesForOpenSSLWithOriginalKeystore() throws Exception {
        Assume.assumeTrue(useOpenSSL);
        KeyStore keystore = wrappedExportableKeystore(key(HOST, CertType.CLIENT_AUTH), key(HOST + " (1)", CertType.SERVER_AUTH));
        Assert.assertEquals(HOST + " (1)", servedAlias(keystore));
    }
}
