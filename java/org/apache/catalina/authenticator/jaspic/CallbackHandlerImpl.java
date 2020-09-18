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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.security.auth.message.callback.PasswordValidationCallback;

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
            // Need to combine data from multiple callbacks so use this to hold
            // the data
            // Process the callbacks
            for (Callback callback : callbacks) {
                if (callback instanceof CallerPrincipalCallback) {
                    CallerPrincipalCallback cpc = (CallerPrincipalCallback) callback;
                    name = cpc.getName();
                    principal = cpc.getPrincipal();
                    subject = cpc.getSubject();
                } else if (callback instanceof GroupPrincipalCallback) {
                    GroupPrincipalCallback gpc = (GroupPrincipalCallback) callback;
                    groups = gpc.getGroups();
                } else if (callback instanceof PasswordValidationCallback) {
                    if (container == null) {
                        log.warn(sm.getString("callbackHandlerImpl.containerMissing", callback.getClass().getName()));
                    } else if (container.getRealm() == null) {
                        log.warn(sm.getString("callbackHandlerImpl.realmMissing",
                                callback.getClass().getName(), container.getName()));
                    } else {
                        PasswordValidationCallback pvc = (PasswordValidationCallback) callback;
                        principal = container.getRealm().authenticate(pvc.getUsername(),
                                String.valueOf(pvc.getPassword()));
                        subject = pvc.getSubject();
                    }
                } else {
                    log.error(sm.getString("callbackHandlerImpl.jaspicCallbackMissing",
                            callback.getClass().getName()));
                }
            }

            // Create the GenericPrincipal
            Principal gp = getPrincipal(principal, name, groups);
            if (subject != null && gp != null) {
                subject.getPrivateCredentials().add(gp);
            }
        }
    }


    private Principal getPrincipal(Principal principal, String name, String[] groups) {
        // If the Principal is cached in the session JASPIC may simply return it
        if (principal instanceof GenericPrincipal) {
            return principal;
        }
        if (name == null && principal != null) {
            name = principal.getName();
        }
        if (name == null) {
            return null;
        }
        List<String> roles;
        if (groups == null || groups.length == 0) {
            roles = Collections.emptyList();
        } else {
            roles = Arrays.asList(groups);
        }

        return new GenericPrincipal(name, null, roles, principal);
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
