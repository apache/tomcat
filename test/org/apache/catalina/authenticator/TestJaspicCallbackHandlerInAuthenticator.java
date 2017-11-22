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

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.authenticator.jaspic.CallbackHandlerImpl;
import org.apache.catalina.connector.Request;

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


    private void testCallbackHandlerCreation(String callbackHandlerImplClassName,
            Class<?> callbackHandlerImplClass)
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        TestAuthenticator authenticator = new TestAuthenticator();
        authenticator.setJaspicCallbackHandlerClass(callbackHandlerImplClassName);
        Method createCallbackHandlerMethod =
                AuthenticatorBase.class.getDeclaredMethod("createCallbackHandler");
        createCallbackHandlerMethod.setAccessible(true);
        CallbackHandler callbackHandler =
                (CallbackHandler) createCallbackHandlerMethod.invoke(authenticator);
        Assert.assertTrue(callbackHandlerImplClass.isInstance(callbackHandler));
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