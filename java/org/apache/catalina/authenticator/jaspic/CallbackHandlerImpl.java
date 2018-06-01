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

import org.apache.catalina.realm.GenericPrincipal;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Implemented as a singleton since the class is stateless.
 */
public class CallbackHandlerImpl implements CallbackHandler {

    private static final StringManager sm = StringManager.getManager(CallbackHandlerImpl.class);

    private static CallbackHandler instance;


    static {
        instance = new CallbackHandlerImpl();
    }


    public static CallbackHandler getInstance() {
        return instance;
    }


    private  CallbackHandlerImpl() {
        // Hide default constructor
    }


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
                } else {
                    // This is a singleton so need to get correct Logger for
                    // current TCCL
                    Log log = LogFactory.getLog(CallbackHandlerImpl.class);
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
}
