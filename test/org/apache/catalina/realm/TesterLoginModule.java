/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.realm;

import java.io.IOException;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

/**
 * Login module that simply matches name and password to perform authentication. If successful, set principal to name
 * and credential to "role1".
 */
public class TesterLoginModule implements LoginModule {

    /** Callback handler to store between initialization and authentication. */
    private CallbackHandler handler;

    /** Subject to store. */
    private Subject subject;

    /** Login name. */
    private String login;

    /**
     * This implementation always return <code>false</code>.
     *
     * @see javax.security.auth.spi.LoginModule#abort()
     */
    @Override
    public boolean abort() throws LoginException {

        return false;
    }

    /**
     * This is where, should the entire authentication process succeeds, principal would be set.
     *
     * @see javax.security.auth.spi.LoginModule#commit()
     */
    @Override
    public boolean commit() throws LoginException {

        try {

            TesterPrincipal user = new TesterPrincipal(login);
            TesterRolePrincipal role = new TesterRolePrincipal("role1");

            subject.getPrincipals().add(user);
            subject.getPrincipals().add(role);

            return true;

        } catch (Exception e) {

            throw new LoginException(e.getMessage());
        }
    }

    /**
     * This implementation ignores both state and options.
     *
     * @see javax.security.auth.spi.LoginModule#initialize(javax.security.auth.Subject,
     *          javax.security.auth.callback.CallbackHandler, java.util.Map, java.util.Map)
     */
    @Override
    public void initialize(Subject aSubject, CallbackHandler aCallbackHandler, Map<String, ?> aSharedState,
            Map<String, ?> aOptions) {

        handler = aCallbackHandler;
        subject = aSubject;
    }

    /**
     * This method checks whether the name and the password are the same.
     *
     * @see javax.security.auth.spi.LoginModule#login()
     */
    @Override
    public boolean login() throws LoginException {

        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("login");
        callbacks[1] = new PasswordCallback("password", true);

        try {

            handler.handle(callbacks);

            String name = ((NameCallback) callbacks[0]).getName();
            String password = String.valueOf(((PasswordCallback) callbacks[1]).getPassword());
            if (!(name.equals("tomcatuser") && password.equals("pass"))) {
                throw new FailedLoginException("Authentication failed");
            }

            login = name;

            return true;

        } catch (IOException e) {
            throw new LoginException(e.getMessage());
        } catch (UnsupportedCallbackException e) {
            throw new LoginException(e.getMessage());
        }
    }

    /**
     * Clears subject from principal and credentials.
     *
     * @see javax.security.auth.spi.LoginModule#logout()
     */
    @Override
    public boolean logout() throws LoginException {

        try {

            TesterPrincipal user = new TesterPrincipal(login);
            TesterRolePrincipal role = new TesterRolePrincipal("role1");

            subject.getPrincipals().remove(user);
            subject.getPrincipals().remove(role);

            return true;

        } catch (Exception e) {

            throw new LoginException(e.getMessage());
        }
    }
}
