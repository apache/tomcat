/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.authenticator.jaspic;

import java.security.Principal;

import javax.security.auth.Subject;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import org.apache.catalina.realm.GenericPrincipal;

public class TestPrincipalGroupCallback {

    private static final String USER_NAME = "user";


    @Test
    public void shouldAddUserPrincipal() throws Exception {
        // given
        PrincipalGroupCallback principalGroupCallback = new PrincipalGroupCallback();
        UserPrincipal userPrincipal = new UserPrincipal(USER_NAME);
        Subject subject = new Subject();
        CallerPrincipalCallback callerCallback = new CallerPrincipalCallback(subject, userPrincipal);
        principalGroupCallback.setCallerPrincipalCallback(callerCallback);
        // when
        GenericPrincipal principal = principalGroupCallback.getPrincipal();

        // then
        assertEquals(USER_NAME, principal.getName());
        assertEquals(userPrincipal, principal.getUserPrincipal());
    }


    @Test
    public void shouldCreatePrincipalWithUsername() throws Exception {
        // given
        PrincipalGroupCallback principalGroupCallback = new PrincipalGroupCallback();
        Subject subject = new Subject();
        CallerPrincipalCallback callerCallback = new CallerPrincipalCallback(subject, USER_NAME);
        principalGroupCallback.setCallerPrincipalCallback(callerCallback);
        // when
        GenericPrincipal principal = principalGroupCallback.getPrincipal();

        // then
        assertEquals(USER_NAME, principal.getName());
    }


    @Test
    public void shouldAddGroupsToPrincipal() throws Exception {
        // given
        PrincipalGroupCallback principalGroupCallback = new PrincipalGroupCallback();
        Subject subject = new Subject();

        CallerPrincipalCallback callerCallback = new CallerPrincipalCallback(subject, USER_NAME);
        principalGroupCallback.setCallerPrincipalCallback(callerCallback);

        String[] groups = new String[] { "group1" };
        GroupPrincipalCallback groupCallback = new GroupPrincipalCallback(subject, groups);
        principalGroupCallback.setCallerPrincipalCallback(groupCallback);

        // when
        GenericPrincipal principal = principalGroupCallback.getPrincipal();

        // then
        assertArrayEquals(principal.getRoles(), groups);
    }


    @Test
    public void shouldReturnNullIfNoCallbackDefined() throws Exception {
        // given
        PrincipalGroupCallback principalGroupCallback = new PrincipalGroupCallback();

        // when
        GenericPrincipal principal = principalGroupCallback.getPrincipal();

        // then
        assertNull(principal);
    }


    private static class UserPrincipal implements Principal {
        private String name;

        public UserPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}