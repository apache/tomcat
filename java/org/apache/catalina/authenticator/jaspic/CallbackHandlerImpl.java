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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import jakarta.security.auth.message.callback.CallerPrincipalCallback;
import jakarta.security.auth.message.callback.GroupPrincipalCallback;
import jakarta.security.auth.message.callback.PasswordValidationCallback;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Default implementation of a JASPIC CallbackHandler.
 */
public class CallbackHandlerImpl implements CallbackHandler, Contained {

    private static final StringManager sm = StringManager.getManager(CallbackHandlerImpl.class);
    private final Log log = LogFactory.getLog(CallbackHandlerImpl.class); // must not be static

    private Container container;


    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

        String name = null;
        Principal principal = null;
        Subject subject = null;
        String[] groups = null;

        if (callbacks != null) {
            /*
             * There may be multiple callbacks passed to this method and/or multiple calls to this method. The Jakarta
             * Authentication specification recommends that this class does not maintain state for individual requests
             * and that the Subject is used to maintain state.
             */
            for (Callback callback : callbacks) {
                if (callback instanceof CallerPrincipalCallback) {
                    CallerPrincipalCallback cpc = (CallerPrincipalCallback) callback;
                    name = cpc.getName();
                    if (cpc.getPrincipal() != null) {
                        principal = cpc.getPrincipal();
                    }
                    subject = cpc.getSubject();
                } else if (callback instanceof GroupPrincipalCallback) {
                    GroupPrincipalCallback gpc = (GroupPrincipalCallback) callback;
                    groups = gpc.getGroups();
                } else if (callback instanceof PasswordValidationCallback) {
                    if (container == null) {
                        log.warn(sm.getString("callbackHandlerImpl.containerMissing", callback.getClass().getName()));
                    } else if (container.getRealm() == null) {
                        log.warn(sm.getString("callbackHandlerImpl.realmMissing", callback.getClass().getName(),
                                container.getName()));
                    } else {
                        PasswordValidationCallback pvc = (PasswordValidationCallback) callback;
                        principal =
                                container.getRealm().authenticate(pvc.getUsername(), String.valueOf(pvc.getPassword()));
                        pvc.setResult(principal != null);
                        subject = pvc.getSubject();
                    }
                } else {
                    log.error(sm.getString("callbackHandlerImpl.jaspicCallbackMissing", callback.getClass().getName()));
                }
            }

            // If subject is null, there is nothing to do
            if (subject != null) {

                // Need a name to create a Principal
                if (name == null && principal != null) {
                    name = principal.getName();
                }

                if (name != null) {
                    // If the Principal has been cached in the session, just return it.
                    if (principal instanceof GenericPrincipal) {
                        // Duplicates are unlikely and will be handled in AuthenticatorBase.getPrincipal()
                        subject.getPrivateCredentials().add(principal);
                    } else {
                        /*
                         * There should only be a single GenericPrincipal in the private credentials for the Subject. If
                         * one is already present, merge the groups to create a new GenericPrincipal. The code assumes
                         * that the name and principal (if any) will be the same.
                         */
                        List<String> mergedRoles = new ArrayList<>();

                        Set<GenericPrincipal> gps = subject.getPrivateCredentials(GenericPrincipal.class);
                        if (!gps.isEmpty()) {
                            GenericPrincipal gp = gps.iterator().next();
                            mergedRoles.addAll(Arrays.asList(gp.getRoles()));
                            // Remove the existing GenericPrincipal
                            subject.getPrivateCredentials().remove(gp);
                        }
                        if (groups != null) {
                            mergedRoles.addAll(Arrays.asList(groups));
                        }

                        if (mergedRoles.size() == 0) {
                            mergedRoles = Collections.emptyList();
                        }

                        subject.getPrivateCredentials().add(new GenericPrincipal(name, mergedRoles, principal));
                    }
                }
            }
        }
    }


    // Contained interface methods
    @Override
    public Container getContainer() {
        return this.container;
    }


    @Override
    public void setContainer(Container container) {
        this.container = container;
    }
}
