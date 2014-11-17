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
package org.apache.catalina.realm;

import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.CredentialHandler;

public class NestedCredentialHandler implements CredentialHandler {

    private final List<CredentialHandler> credentialHandlers = new ArrayList<>();


    @Override
    public boolean matches(String inputCredentials, String storedCredentials) {
        for (CredentialHandler handler : credentialHandlers) {
            if (handler.matches(inputCredentials, storedCredentials)) {
                return true;
            }
        }
        return false;
    }


    /**
     * The input credentials will be passed to the first nested
     * {@link CredentialHandler}. If no nested {@link CredentialHandler} are
     * configured then <code>null</code> will be returned.
     *
     * {@inheritDoc}
     */
    @Override
    public String mutate(String inputCredentials) {
        if (credentialHandlers.isEmpty()) {
            return null;
        }

        return credentialHandlers.get(0).mutate(inputCredentials);
    }


    public void addCredentialHandler(CredentialHandler handler) {
        credentialHandlers.add(handler);
    }

    public CredentialHandler[] getCredentialHandlers() {
        return credentialHandlers.toArray(new CredentialHandler[0]);
    }

}
