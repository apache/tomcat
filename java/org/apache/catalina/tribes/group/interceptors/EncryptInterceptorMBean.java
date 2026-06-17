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

/**
 * MBean interface for managing the {@link EncryptInterceptor} configuration and status.
 */
public interface EncryptInterceptorMBean {

    /**
     * Returns the option flag for the encryption interceptor.
     *
     * @return the option flag value
     */
    int getOptionFlag();

    /**
     * Sets the option flag for the encryption interceptor.
     *
     * @param optionFlag the option flag value
     */
    void setOptionFlag(int optionFlag);

    /**
     * Sets the encryption algorithm to use.
     *
     * @param algorithm the encryption algorithm in algorithm/mode/padding format
     */
    void setEncryptionAlgorithm(String algorithm);

    /**
     * Returns the encryption algorithm currently in use.
     *
     * @return the encryption algorithm string
     */
    String getEncryptionAlgorithm();

    /**
     * Sets the encryption key as raw bytes.
     *
     * @param key the encryption key bytes
     */
    void setEncryptionKey(byte[] key);

    /**
     * Returns the encryption key as raw bytes.
     *
     * @return the encryption key bytes
     */
    byte[] getEncryptionKey();

    /**
     * Sets the JCA provider name for cryptographic operations.
     *
     * @param provider the JCA provider name
     */
    void setProviderName(String provider);

    /**
     * Returns the JCA provider name for cryptographic operations.
     *
     * @return the JCA provider name, or {@code null} for default
     */
    String getProviderName();
}
