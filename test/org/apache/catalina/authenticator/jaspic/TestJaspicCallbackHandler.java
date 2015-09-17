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

import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import org.apache.catalina.realm.GenericPrincipal;

public class TestJaspicCallbackHandler {

    private static final String USER = "user";

    private JaspicCallbackHandler jaspicCallbackHandler = new JaspicCallbackHandler(null);


    @Test
    public void shouldConvertCallbackToTomcatPrincipal() throws Exception {
        // given
        Subject subject = new Subject();
        CallerPrincipalCallback callerCallback = new CallerPrincipalCallback(subject, USER);
        String[] groups = new String[] { "group" };

        GroupPrincipalCallback groupCallback = new GroupPrincipalCallback(subject, groups);
        Callback[] callbacks = new Callback[] { callerCallback, groupCallback };

        // when
        jaspicCallbackHandler.handle(callbacks);

        // then
        Set<GenericPrincipal> principals = callerCallback.getSubject().getPrivateCredentials(
                GenericPrincipal.class);
        GenericPrincipal principal = principals.iterator().next();
        assertEquals(USER, principal.getName());
        assertArrayEquals(groups, principal.getRoles());
    }


    @Test(expected = IllegalStateException.class)
    public void shouldHandleUnknowCallback() throws Exception {
        // given
        Callback[] callbacks = new Callback[] { new Callback() {
        } };

        // when
        jaspicCallbackHandler.handle(callbacks);

        // then
        fail("Should throw exception");
    }
}