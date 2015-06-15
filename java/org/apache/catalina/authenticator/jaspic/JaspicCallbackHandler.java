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
package org.apache.catalina.authenticator.jaspic;

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.security.auth.message.callback.PasswordValidationCallback;

import org.apache.catalina.Realm;
import org.apache.catalina.realm.GenericPrincipal;

public class JaspicCallbackHandler implements CallbackHandler {

    private Realm realm;

    private ThreadLocal<PrincipalGroupCallback> principalGroupCallback = new ThreadLocal<>();

    public JaspicCallbackHandler(Realm realm) {
        this.realm = realm;
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        principalGroupCallback.set(new PrincipalGroupCallback());
        for (Callback callback : callbacks) {
            handleCallback(callback);
        }
    }

    public Principal getPrincipal() {
        return principalGroupCallback.get().getPrincipal();
    }

    private void handleCallback(Callback callback) {
        if (callback instanceof CallerPrincipalCallback) {
            principalGroupCallback.get().addCallback(callback);
        } else if (callback instanceof GroupPrincipalCallback) {
            principalGroupCallback.get().addCallback(callback);
        } else if (callback instanceof PasswordValidationCallback) {
            handlePasswordValidationCallback((PasswordValidationCallback) callback);
        } else {
            throw new IllegalStateException("Unknown callback!");
        }
    }

    private void handlePasswordValidationCallback(
            PasswordValidationCallback passwordValidationCallback) {
        Subject subject = passwordValidationCallback.getSubject();

        passwordValidationCallback.setResult(true);
        subject.getPrincipals().add(
                new GenericPrincipal("user", "password", Collections.singletonList("user")));
    }
}
