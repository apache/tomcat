/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.authenticator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.security.auth.message.callback.PasswordValidationCallback;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.authenticator.jaspic.CallbackHandlerImpl;
import org.apache.catalina.connector.Request;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.RealmBase;

public class TestJaspicCallbackHandlerInAuthenticator {

    @Test
    public void testCustomCallbackHandlerCreation() throws Exception {
        testCallbackHandlerCreation("org.apache.catalina.authenticator.TestCallbackHandlerImpl",
                TestCallbackHandlerImpl.class);
    }


    @Test
    public void testDefaultCallbackHandlerCreation() throws Exception {
        testCallbackHandlerCreation(null, CallbackHandlerImpl.class);
    }


    private void testCallbackHandlerCreation(String callbackHandlerImplClassName, Class<?> callbackHandlerImplClass)
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        CallbackHandler callbackHandler = createCallbackHandler(callbackHandlerImplClassName);
        Assert.assertTrue(callbackHandlerImplClass.isInstance(callbackHandler));
    }


    @Test
    public void testCallerPrincipalCallback() throws Exception {
        CallbackHandler callbackHandler = createCallbackHandler(null);
        Subject clientSubject = new Subject();
        CallerPrincipalCallback cpc1 = new CallerPrincipalCallback(clientSubject, "name1");
        callbackHandler.handle(new Callback[] { cpc1 });
        CallerPrincipalCallback cpc2 = new CallerPrincipalCallback(clientSubject, new Principal() {
            @Override
            public String getName() {
                return "name2";
            }
        });
        callbackHandler.handle(new Callback[] { cpc2 });
        Set<Object> credentials = clientSubject.getPrivateCredentials();
        Assert.assertTrue(credentials.size() == 2);
        Set<String> names = new HashSet<>(Arrays.asList(new String[] { "name1", "name2" }));
        for (Object o : credentials) {
            names.remove(((GenericPrincipal) o).getName());
        }
        Assert.assertTrue(names.isEmpty());
    }

    @Test
    public void testGroupPrincipalCallback() throws Exception {
        CallbackHandler callbackHandler = createCallbackHandler(null);
        Subject clientSubject = new Subject();
        CallerPrincipalCallback cpc = new CallerPrincipalCallback(clientSubject, "name");
        GroupPrincipalCallback gpc = new GroupPrincipalCallback(clientSubject,
                new String[] { "group1", "group2" });
        callbackHandler.handle(new Callback[] { cpc, gpc });
        Set<Object> credentials = clientSubject.getPrivateCredentials();
        Assert.assertTrue(credentials.size() == 1);
        GenericPrincipal gp = (GenericPrincipal) credentials.iterator().next();
        Assert.assertEquals("name", gp.getName());
        Assert.assertTrue(gp.hasRole("group1"));
        Assert.assertTrue(gp.hasRole("group2"));
    }

    @Test
    public void testPasswordValidationCallback() throws Exception {
        CallbackHandler callbackHandler = createCallbackHandler(null);
        Container container = new TestContainer();
        container.setRealm(new TestRealm());
        ((Contained) callbackHandler).setContainer(container);
        Subject clientSubject = new Subject();
        PasswordValidationCallback pvc1 = new PasswordValidationCallback(clientSubject, "name1",
                "password".toCharArray());
        callbackHandler.handle(new Callback[] { pvc1 });
        PasswordValidationCallback pvc2 = new PasswordValidationCallback(clientSubject, "name2",
                "invalid".toCharArray());
        callbackHandler.handle(new Callback[] { pvc2 });
        Set<Object> credentials = clientSubject.getPrivateCredentials();
        Assert.assertTrue(credentials.size() == 1);
        GenericPrincipal gp = (GenericPrincipal) credentials.iterator().next();
        Assert.assertEquals("name1", gp.getName());
    }


    private CallbackHandler createCallbackHandler(String callbackHandlerImplClassName) throws NoSuchMethodException,
            SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        TestAuthenticator authenticator = new TestAuthenticator();
        if (callbackHandlerImplClassName != null) {
            authenticator.setJaspicCallbackHandlerClass(callbackHandlerImplClassName);
        }
        Method createCallbackHandlerMethod = AuthenticatorBase.class.getDeclaredMethod("createCallbackHandler");
        createCallbackHandlerMethod.setAccessible(true);
        return (CallbackHandler) createCallbackHandlerMethod.invoke(authenticator);
    }


    private static class TestAuthenticator extends AuthenticatorBase {

        @Override
        protected boolean doAuthenticate(Request request, HttpServletResponse response)
                throws IOException {
            return false;
        }

        @Override
        protected String getAuthMethod() {
            return null;
        }

    }


    private static class TestContainer extends ContainerBase {

        @Override
        protected String getObjectNameKeyProperties() {
            return null;
        }
    }


    private static class TestRealm extends RealmBase {

        @Override
        public Principal authenticate(String username, String password) {
            if (getPassword(username).equals(password))
                return getPrincipal(username);
            return null;
        }

        @Override
        protected String getPassword(String username) {
            return "password";
        }

        @Override
        protected Principal getPrincipal(String username) {
            return new GenericPrincipal(username, null, null);
        }

        @Override
        protected String getName() {
            return "Test Realm";
        }
    }
}


class TestCallbackHandlerImpl implements CallbackHandler {

    public TestCallbackHandlerImpl() {
        // Default constructor required by reflection
    }


    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        // don't have to do anything; needed only for instantiation
    }
}