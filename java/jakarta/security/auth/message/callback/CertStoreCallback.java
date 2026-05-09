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

import java.security.cert.CertStore;

import javax.security.auth.callback.Callback;

/**
 * Callback that enables a runtime to inform authentication modules of the {@link CertStore} to use for certificate
 * validation. The runtime populates the CertStore, and the authentication module retrieves it.
 */
public class CertStoreCallback implements Callback {

    private CertStore certStore;

    /**
     * Constructs an empty CertStoreCallback. The runtime will populate the CertStore via {@link #setCertStore}.
     */
    public CertStoreCallback() {
    }

    /**
     * Sets the CertStore to be used for certificate validation.
     *
     * @param certStore the CertStore
     */
    public void setCertStore(CertStore certStore) {
        this.certStore = certStore;
    }

    /**
     * Returns the CertStore set by the runtime.
     *
     * @return the CertStore, or {@code null} if not yet set
     */
    public CertStore getCertStore() {
        return certStore;
    }
}
