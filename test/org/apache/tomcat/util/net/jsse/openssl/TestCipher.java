package org.apache.tomcat.util.net.jsse.openssl;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.junit.Assert;

public class TestCipher {

    /**
     * Checks that every cipher suite returned by OpenSSL is mapped to at least
     * one cipher suite that is recognised by JSSE or is a cipher suite known
     * not to be supported by JSSE.
     */
    //@Test
    public void testAllOpenSSlCiphersMapped() throws Exception {
        Set<String> openSSLCipherSuites = getOpenSSLCiphersAsSet("ALL");

        for (String openSSLCipherSuite : openSSLCipherSuites) {
            List<String> jsseCipherSuites =
                    OpenSSLCipherConfigurationParser.parseExpression(openSSLCipherSuite);
            Assert.assertTrue("The OpenSSL cipher suite " + openSSLCipherSuite +
                    " does not map to a JSSE cipher suite", jsseCipherSuites.size() > 0);
        }
    }


    private static Set<String> getOpenSSLCiphersAsSet(String specification) throws Exception {
        String[] ciphers = getOpenSSLCiphersAsExpression(specification).split(":");
        Set<String> result = new HashSet<>(ciphers.length);
        for (String cipher : ciphers) {
            result.add(cipher);
        }
        return result;

    }


    private static String getOpenSSLCiphersAsExpression(String specification) throws Exception {
        // TODO The path to OpenSSL needs to be made configurable
        StringBuilder cmd = new StringBuilder("/opt/local/bin/openssl ciphers");
        if (specification != null) {
            cmd.append(' ');
            cmd.append(specification);
        }
        Process process = Runtime.getRuntime().exec(cmd.toString());
        InputStream stderr = process.getErrorStream();
        InputStream stdout = process.getInputStream();

        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        IOTools.flow(stderr, stderrBytes);
        //String errorText = stderrBytes.toString();
        //Assert.assertTrue(errorText, errorText.length() == 0);

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        IOTools.flow(stdout, stdoutBytes);
        return stdoutBytes.toString();
    }
}
