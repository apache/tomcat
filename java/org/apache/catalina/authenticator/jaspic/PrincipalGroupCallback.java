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

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.security.auth.Subject;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;

import org.apache.catalina.realm.GenericPrincipal;

/**
 * This class merges two principal callbacks into one tomcat's
 * {@link GenericPrincipal}.
 */
public class PrincipalGroupCallback {
    private CallerPrincipalCallback callerPrincipalCallback;
    private GroupPrincipalCallback groupPrincipalCallback;


    public void setCallerPrincipalCallback(CallerPrincipalCallback callerPrincipalCallback) {
        this.callerPrincipalCallback = callerPrincipalCallback;
    }

    public void setGroupPrincipalCallback(GroupPrincipalCallback groupPrincipalCallback) {
        this.groupPrincipalCallback = groupPrincipalCallback;
    }

    public void configureSubject() {
        GenericPrincipal principal = getPrincipal();
        if (principal == null) {
            return;
        }
        Subject subject = getSubject();
        if (subject != null) {
            subject.getPrivateCredentials().add(principal);
        }
    }

    private Subject getSubject() {
        if (callerPrincipalCallback != null) {
            return callerPrincipalCallback.getSubject();
        }
        if (groupPrincipalCallback != null) {
            return callerPrincipalCallback.getSubject();
        }
        return null;
    }

    /**
     * Get tomcat's principal, which contains user principal and roles
     * @return {@link GenericPrincipal}
     */
    public GenericPrincipal getPrincipal() {
        if (callerPrincipalCallback == null) {
            return null;
        }
        Principal userPrincipal = getUserPrincipal();
        return new GenericPrincipal(getUserName(), null, getRoles(), userPrincipal);
    }


    private Principal getUserPrincipal() {
        if (callerPrincipalCallback == null) {
            return null;
        }
        return callerPrincipalCallback.getPrincipal();
    }


    private List<String> getRoles() {
        if (groupPrincipalCallback == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(groupPrincipalCallback.getGroups());
    }


    private String getUserName() {
        String name = null;
        if (callerPrincipalCallback != null) {
            name = callerPrincipalCallback.getName();
        }
        if (name != null) {
            return name;
        }
        return getUserPrincipalName();
    }


    private String getUserPrincipalName() {
        Principal principal = getUserPrincipal();
        if (principal == null) {
            return null;
        }
        return principal.getName();
    }
}
