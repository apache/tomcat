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
package org.apache.catalina.authenticator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Test the BasicAuthenticator's BasicCredentials inner class and the
 * associated Base64 decoder.
 */
public class TestBasicAuthParser {

    private static final String NICE_METHOD = "Basic";
    private static final String USER_NAME = "userid";
    private static final String PASSWORD = "secret";

    /*
     * test cases with good BASIC Auth credentials - Base64 strings
     * can have zero, one or two trailing pad characters
     */
    @Test
    public void testGoodCredentials() throws Exception {
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, PASSWORD);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD, credentials.getPassword());
    }

    @Test
    public void testGoodCredentialsNoPassword() throws Exception {
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, null);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertNull(credentials.getPassword());
    }

    @Test
    public void testGoodCrib() throws Exception {
        final String BASE64_CRIB = "dXNlcmlkOnNlY3JldA==";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD, credentials.getPassword());
    }

    @Test
    public void testGoodCribUserOnly() throws Exception {
        final String BASE64_CRIB = "dXNlcmlk";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertNull(credentials.getPassword());
    }

    @Test
    public void testGoodCribOnePad() throws Exception {
        final String PASSWORD1 = "secrets";
        final String BASE64_CRIB = "dXNlcmlkOnNlY3JldHM=";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD1, credentials.getPassword());
    }

    /*
     * Line breaks are not permitted inside the base64 encoded value.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testLineWrap() throws Exception {
        final String BASE64_CRIB = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldY"
                + "WVphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ejAxMjM0"
                + "\n" + "NTY3ODkrL0FBQUFCQkJCQ0NDQ0REREQ=";
        final BasicAuthHeader AUTH_HEADER = new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        @SuppressWarnings("unused")
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
    }

    /*
     * RFC 2045 says the Base64 encoded string should be represented
     * as lines of no more than 76 characters. However, RFC 2617
     * says a base64-user-pass token is not limited to 76 char/line.
     */
    @Test
    public void testGoodCribBase64Big() throws Exception {
        // Our decoder accepts a long token without complaint.
        final String USER_LONG = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "abcdefghijklmnopqrstuvwxyz0123456789+/AAAABBBBCCCC"
                + "DDDD";                   // 80 characters
        final String BASE64_CRIB = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldY"
                + "WVphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ejAxMjM0"
                + "NTY3ODkrL0FBQUFCQkJCQ0NDQ0REREQ="; // no new line
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_LONG, credentials.getUsername());
    }


    /*
     * verify the parser follows RFC2617 by treating the auth-scheme
     * token as case-insensitive.
     */
    @Test
    public void testAuthMethodCaseBasic() throws Exception {
        final String METHOD = "bAsIc";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(METHOD, USER_NAME, PASSWORD);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD, credentials.getPassword());
    }

    /*
     * Confirm the Basic parser rejects an invalid authentication method.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAuthMethodBadMethod() throws Exception {
        final String METHOD = "BadMethod";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(METHOD, USER_NAME, PASSWORD);
        @SuppressWarnings("unused")
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
    }

    /*
     * Confirm the Basic parser allows exactly one space after the authentication method.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testAuthMethodExtraLeadingSpace() throws Exception {
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD + " ", USER_NAME, PASSWORD);
        @SuppressWarnings("unused")
        final BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
    }


    /*
     * invalid decoded credentials cases
     */
    @Test
    public void testWrongPassword() throws Exception {
        final String PWD_WRONG = "wrong";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, PWD_WRONG);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertNotSame(PASSWORD, credentials.getPassword());
    }

    @Test
    public void testMissingUsername() throws Exception {
        final String EMPTY_USER_NAME = "";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, EMPTY_USER_NAME, PASSWORD);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(EMPTY_USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD, credentials.getPassword());
    }

    @Test
    public void testShortUsername() throws Exception {
        final String SHORT_USER_NAME = "a";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, SHORT_USER_NAME, PASSWORD);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(SHORT_USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD, credentials.getPassword());
    }

    @Test
    public void testShortPassword() throws Exception {
        final String SHORT_PASSWORD = "a";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, SHORT_PASSWORD);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(SHORT_PASSWORD, credentials.getPassword());
    }

    @Test
    public void testPasswordHasSpaceEmbedded() throws Exception {
        final String PASSWORD_SPACE = "abc def";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, PASSWORD_SPACE);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD_SPACE, credentials.getPassword());
    }

    @Test
    public void testPasswordHasColonEmbedded() throws Exception {
        final String PASSWORD_COLON = "abc:def";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, PASSWORD_COLON);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD_COLON, credentials.getPassword());
    }

    @Test
    public void testPasswordHasColonLeading() throws Exception {
        final String PASSWORD_COLON = ":abcdef";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, PASSWORD_COLON);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD_COLON, credentials.getPassword());
    }

    @Test
    public void testPasswordHasColonTrailing() throws Exception {
        final String PASSWORD_COLON = "abcdef:";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, PASSWORD_COLON);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD_COLON, credentials.getPassword());
    }

    /*
     * Confirm the Basic parser does not tolerate excess white space after the base64 blob.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testAuthMethodExtraTrailingSpace() throws Exception {
        final BasicAuthHeader AUTH_HEADER = new BasicAuthHeader(NICE_METHOD, USER_NAME, PASSWORD, "    ");
        @SuppressWarnings("unused")
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
    }

    /*
     * Confirm the Basic parser does not tolerate excess white space around the username inside the base64 blob.
     */
    @Test
    public void testUserExtraSpace() throws Exception {
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, " " + USER_NAME + " ", PASSWORD);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertNotEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(USER_NAME, credentials.getUsername().trim());
        Assert.assertEquals(PASSWORD, credentials.getPassword());
    }

    /*
     * Confirm the Basic parser does not tolerate excess white space around the password within the base64 blob.
     */
    @Test
    public void testPasswordExtraSpace() throws Exception {
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, USER_NAME, " " + PASSWORD + " ");
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                    AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertNotEquals(PASSWORD, credentials.getPassword());
        Assert.assertEquals(PASSWORD, credentials.getPassword().trim());
    }


    /*
     * invalid base64 string tests
     *
     * Refer to
     *  - RFC 7617 (Basic Auth)
     *  - RFC 4648 (base 64)
     */

    /*
     * non-trailing "=" is illegal and will be rejected by the parser
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBadBase64InlineEquals() throws Exception {
        final String BASE64_CRIB = "dXNlcmlkOnNlY3J=dAo=";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        @SuppressWarnings("unused") // Exception will be thrown.
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                    AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
    }

    /*
     * "-" is not a legal base64 character. The RFC says it must be
     * ignored by the decoder. This will scramble the decoded string
     * and eventually result in an IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBadBase64Char() throws Exception {
        final String BASE64_CRIB = "dXNlcmlkOnNl-3JldHM=";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        @SuppressWarnings("unused")
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                    AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
    }

    /*
     * "-" is not a legal base64 character.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testBadBase64LastChar() throws Exception {
        final String BASE64_CRIB = "dXNlcmlkOnNlY3JldA-=";
        final BasicAuthHeader AUTH_HEADER = new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        @SuppressWarnings("unused")
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
    }

    /*
     * The trailing third "=" is illegal.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testBadBase64TooManyEquals() throws Exception {
        final String BASE64_CRIB = "dXNlcmlkOnNlY3JldA===";
        final BasicAuthHeader AUTH_HEADER = new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        @SuppressWarnings("unused")
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
    }

    /*
     * there should be a multiple of 4 encoded characters. However,
     * the RFC says the decoder should pad the input string with
     * zero bits out to the next boundary. An error will not be detected
     * unless the payload has been damaged in some way - this
     * particular crib has no damage.
     */
    @Test
    public void testBadBase64BadLength() throws Exception {
        final String BASE64_CRIB = "dXNlcmlkOnNlY3JldA";
        final BasicAuthHeader AUTH_HEADER =
                new BasicAuthHeader(NICE_METHOD, BASE64_CRIB);
        BasicAuthenticator.BasicCredentials credentials =
                new BasicAuthenticator.BasicCredentials(
                    AUTH_HEADER.getHeader(), StandardCharsets.UTF_8);
        Assert.assertEquals(USER_NAME, credentials.getUsername());
        Assert.assertEquals(PASSWORD, credentials.getPassword());
    }


    /*
     * Encapsulate the logic to generate an HTTP header
     * for BASIC Authentication.
     * Note: only used internally, so no need to validate arguments.
     */
    public static final class BasicAuthHeader {

        private static final byte[] HEADER =
                "authorization: ".getBytes(StandardCharsets.ISO_8859_1);
        private ByteChunk authHeader;
        private int initialOffset = 0;

        /*
         * This method creates a valid base64 blob
         */
        public BasicAuthHeader(String method, String username,
                String password) {
            this(method, username, password, null);
        }

        /*
         * This method creates valid base64 blobs with optional trailing data
         */
        private BasicAuthHeader(String method, String username,
                String password, String extraBlob) {
            prefix(method);

            String userCredentials =
                    ((password == null) || (password.length() < 1))
                    ? username
                    : username + ":" + password;
            byte[] credentialsBytes =
                    userCredentials.getBytes(StandardCharsets.ISO_8859_1);
            String base64auth = Base64.getEncoder().encodeToString(credentialsBytes);
            byte[] base64Bytes =
                    base64auth.getBytes(StandardCharsets.ISO_8859_1);

            byte[] extraBytes =
                    ((extraBlob == null) || (extraBlob.length() < 1))
                    ? null :
                    extraBlob.getBytes(StandardCharsets.ISO_8859_1);

            try {
                authHeader.append(base64Bytes, 0, base64Bytes.length);
                if (extraBytes != null) {
                    authHeader.append(extraBytes, 0, extraBytes.length);
                }
            }
            catch (IOException ioe) {
                throw new IllegalStateException("unable to extend ByteChunk:"
                        + ioe.getMessage());
            }
            // emulate tomcat server - offset points to method in header
            authHeader.setStart(initialOffset);
        }

        /*
         * This method allows injection of cribbed base64 blobs,
         * without any validation of the contents
         */
        private BasicAuthHeader(String method, String fakeBase64) {
            prefix(method);

            byte[] fakeBytes = fakeBase64.getBytes(StandardCharsets.ISO_8859_1);

            try {
                authHeader.append(fakeBytes, 0, fakeBytes.length);
            }
            catch (IOException ioe) {
                throw new IllegalStateException("unable to extend ByteChunk:"
                        + ioe.getMessage());
            }
            // emulate tomcat server - offset points to method in header
            authHeader.setStart(initialOffset);
        }

        /*
         * construct the common authorization header
         */
        private void prefix(String method) {
            authHeader = new ByteChunk();
            authHeader.setBytes(HEADER, 0, HEADER.length);
            initialOffset = HEADER.length;

            String methodX = method + " ";
            byte[] methodBytes = methodX.getBytes(StandardCharsets.ISO_8859_1);

            try {
                authHeader.append(methodBytes, 0, methodBytes.length);
            }
            catch (IOException ioe) {
                throw new IllegalStateException("unable to extend ByteChunk:"
                        + ioe.getMessage());
            }
        }

        public ByteChunk getHeader() {
            return authHeader;
        }
    }
}
