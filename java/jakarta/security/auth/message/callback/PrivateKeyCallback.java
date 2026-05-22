/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jakarta.security.auth.message.callback;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import javax.security.auth.callback.Callback;
import javax.security.auth.x500.X500Principal;

/**
 * A callback that enables an authentication module to request a
 * certificate chain and private key from the runtime.  The request
 * identifies the desired key material using one of several {@link Request}
 * subtypes, such as keystore alias, certificate digest, subject key
 * identifier, or issuer and serial number.
 */
public class PrivateKeyCallback implements Callback {

    private final Request request;
    private Certificate[] chain;
    private PrivateKey key;

    /**
     * Creates a new {@code PrivateKeyCallback} with the specified request.
     *
     * @param request the {@code Request} object identifying the desired
     *        certificate chain and private key
     */
    public PrivateKeyCallback(Request request) {
        this.request = request;
    }

    /**
     * Returns the request associated with this callback.
     *
     * @return the {@code Request} object
     */
    public Request getRequest() {
        return request;
    }

    /**
     * Sets the private key and certificate chain resolved by the runtime
     * in response to the request.
     *
     * @param key the resolved {@code PrivateKey}
     * @param chain the corresponding certificate chain
     */
    public void setKey(PrivateKey key, Certificate[] chain) {
        this.key = key;
        this.chain = chain;
    }

    /**
     * Returns the private key resolved by the runtime.
     *
     * @return the {@code PrivateKey}, or {@code null} if not yet resolved
     */
    public PrivateKey getKey() {
        return key;
    }

    /**
     * Returns the certificate chain resolved by the runtime.
     *
     * @return the certificate chain, or {@code null} if not yet resolved
     */
    public Certificate[] getChain() {
        return chain;
    }

    /**
     * Base interface for private key requests.  Implementations specify
     * the criteria by which the runtime should locate the desired
     * certificate chain and private key.
     */
    public interface Request {
    }

    /**
     * A request to retrieve a certificate chain and private key by
     * keystore alias.
     */
    public static class AliasRequest implements Request {

        private final String alias;

        /**
         * Creates a new {@code AliasRequest} with the specified keystore alias.
         *
         * @param alias the keystore alias identifying the desired key entry
         */
        public AliasRequest(String alias) {
            this.alias = alias;
        }

        /**
         * Returns the keystore alias for this request.
         *
         * @return the keystore alias
         */
        public String getAlias() {
            return alias;
        }
    }

    /**
     * A request to retrieve a certificate chain and private key by
     * certificate digest value.
     */
    public static class DigestRequest implements Request {
        private final byte[] digest;
        private final String algorithm;

        /**
         * Creates a new {@code DigestRequest} with the specified digest
         * value and digest algorithm.
         *
         * @param digest the digest value of the desired certificate
         * @param algorithm the digest algorithm used to produce the digest
         */
        public DigestRequest(byte[] digest, String algorithm) {
            this.digest = digest;
            this.algorithm = algorithm;
        }

        /**
         * Returns the digest value for this request.
         *
         * @return the digest byte array
         */
        public byte[] getDigest() {
            return digest;
        }

        /**
         * Returns the digest algorithm used for this request.
         *
         * @return the algorithm name
         */
        public String getAlgorithm() {
            return algorithm;
        }
    }

    /**
     * A request to retrieve a certificate chain and private key by
     * the Subject Key Identifier extension value.
     */
    public static class SubjectKeyIDRequest implements Request {

        private final byte[] subjectKeyID;

        /**
         * Creates a new {@code SubjectKeyIDRequest} with the specified
         * subject key identifier.
         *
         * @param subjectKeyID the Subject Key Identifier extension value
         *        from the desired certificate
         */
        public SubjectKeyIDRequest(byte[] subjectKeyID) {
            this.subjectKeyID = subjectKeyID;
        }

        /**
         * Returns the subject key identifier for this request.
         *
         * @return the Subject Key Identifier byte array
         */
        public byte[] getSubjectKeyID() {
            return subjectKeyID;
        }
    }

    /**
     * A request to retrieve a certificate chain and private key by
     * the issuer distinguished name and certificate serial number.
     */
    public static class IssuerSerialNumRequest implements Request {
        private final X500Principal issuer;
        private final BigInteger serialNum;

        /**
         * Creates a new {@code IssuerSerialNumRequest} with the specified
         * issuer and serial number.
         *
         * @param issuer the issuer distinguished name of the desired certificate
         * @param serialNum the serial number of the desired certificate
         */
        public IssuerSerialNumRequest(X500Principal issuer, BigInteger serialNum) {
            this.issuer = issuer;
            this.serialNum = serialNum;
        }

        /**
         * Returns the issuer distinguished name for this request.
         *
         * @return the {@code X500Principal} representing the issuer
         */
        public X500Principal getIssuer() {
            return issuer;
        }

        /**
         * Returns the certificate serial number for this request.
         *
         * @return the serial number as a {@code BigInteger}
         */
        public BigInteger getSerialNum() {
            return serialNum;
        }
    }
}
