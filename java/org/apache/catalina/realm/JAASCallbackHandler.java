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


import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * Implementation of the JAAS <code>CallbackHandler</code> interface, used to negotiate delivery of the username and
 * credentials that were specified to our constructor. No interaction with the user is required (or possible).
 * </p>
 * <p>
 * This <code>CallbackHandler</code> will pre-digest the supplied password, if required by the
 * <code>&lt;Realm&gt;</code> element in <code>server.xml</code>.
 * </p>
 * <p>
 * At present, <code>JAASCallbackHandler</code> knows how to handle callbacks of type
 * <code>javax.security.auth.callback.NameCallback</code> and
 * <code>javax.security.auth.callback.PasswordCallback</code>.
 * </p>
 *
 * @author Craig R. McClanahan
 * @author Andrew R. Jaquith
 */
public class JAASCallbackHandler implements CallbackHandler {

    // ------------------------------------------------------------ Constructor


    /**
     * Construct a callback handler configured with the specified values. Note that if the <code>JAASRealm</code>
     * instance specifies digested passwords, the <code>password</code> parameter will be pre-digested here.
     *
     * @param realm    Our associated JAASRealm instance
     * @param username Username to be authenticated with
     * @param password Password to be authenticated with
     */
    public JAASCallbackHandler(JAASRealm realm, String username, String password) {

        this(realm, username, password, null, null, null, null, null, null, null, null);
    }


    /**
     * Construct a callback handler for DIGEST authentication.
     *
     * @param realm      Our associated JAASRealm instance
     * @param username   Username to be authenticated with
     * @param password   Password to be authenticated with
     * @param nonce      Server generated nonce
     * @param nc         Nonce count
     * @param cnonce     Client generated nonce
     * @param qop        Quality of protection applied to the message
     * @param realmName  Realm name
     * @param digestA2   Second digest calculated as digest(Method + ":" + uri)
     * @param algorithm  The digest algorithm to use
     * @param authMethod The authentication method in use
     */
    public JAASCallbackHandler(JAASRealm realm, String username, String password, String nonce, String nc,
            String cnonce, String qop, String realmName, String digestA2, String algorithm, String authMethod) {
        this.realm = realm;
        this.username = username;

        if (password != null && realm.hasMessageDigest(algorithm)) {
            this.password = realm.getCredentialHandler().mutate(password);
        } else {
            this.password = password;
        }
        this.nonce = nonce;
        this.nc = nc;
        this.cnonce = cnonce;
        this.qop = qop;
        this.realmName = realmName;
        this.digestA2 = digestA2;
        this.authMethod = authMethod;
        this.algorithm = algorithm;
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(JAASCallbackHandler.class);

    /**
     * The password to be authenticated with.
     */
    protected final String password;


    /**
     * The associated <code>JAASRealm</code> instance.
     */
    protected final JAASRealm realm;

    /**
     * The username to be authenticated with.
     */
    protected final String username;

    /**
     * Server generated nonce.
     */
    protected final String nonce;

    /**
     * Nonce count.
     */
    protected final String nc;

    /**
     * Client generated nonce.
     */
    protected final String cnonce;

    /**
     * Quality of protection applied to the message.
     */
    protected final String qop;

    /**
     * Realm name.
     */
    protected final String realmName;

    /**
     * Second digest.
     */
    protected final String digestA2;

    /**
     * The authentication method to be used. If null, assume BASIC/FORM.
     */
    protected final String authMethod;

    /**
     * Algorithm.
     */
    protected final String algorithm;

    // --------------------------------------------------------- Public Methods


    /**
     * Retrieve the information requested in the provided <code>Callbacks</code>. This implementation only recognizes
     * {@link NameCallback}, {@link PasswordCallback} and {@link TextInputCallback}. {@link TextInputCallback} is used
     * to pass the various additional parameters required for DIGEST authentication.
     *
     * @param callbacks The set of <code>Callback</code>s to be processed
     *
     * @exception IOException                  if an input/output error occurs
     * @exception UnsupportedCallbackException if the login method requests an unsupported callback type
     */
    @Override
    public void handle(Callback callbacks[]) throws IOException, UnsupportedCallbackException {

        for (Callback callback : callbacks) {

            if (callback instanceof NameCallback) {
                if (realm.getContainer().getLogger().isTraceEnabled()) {
                    realm.getContainer().getLogger().trace(sm.getString("jaasCallback.username", username));
                }
                ((NameCallback) callback).setName(username);
            } else if (callback instanceof PasswordCallback) {
                final char[] passwordcontents;
                if (password != null) {
                    passwordcontents = password.toCharArray();
                } else {
                    passwordcontents = new char[0];
                }
                ((PasswordCallback) callback).setPassword(passwordcontents);
            } else if (callback instanceof TextInputCallback) {
                TextInputCallback cb = ((TextInputCallback) callback);
                if (cb.getPrompt().equals("nonce")) {
                    cb.setText(nonce);
                } else if (cb.getPrompt().equals("nc")) {
                    cb.setText(nc);
                } else if (cb.getPrompt().equals("cnonce")) {
                    cb.setText(cnonce);
                } else if (cb.getPrompt().equals("qop")) {
                    cb.setText(qop);
                } else if (cb.getPrompt().equals("realmName")) {
                    cb.setText(realmName);
                } else if (cb.getPrompt().equals("digestA2")) {
                    cb.setText(digestA2);
                } else if (cb.getPrompt().equals("authMethod")) {
                    cb.setText(authMethod);
                } else if (cb.getPrompt().equals("algorithm")) {
                    cb.setText(algorithm);
                } else if (cb.getPrompt().equals("catalinaBase")) {
                    cb.setText(realm.getContainer().getCatalinaBase().getAbsolutePath());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }
}
