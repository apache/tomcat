package org.apache.catalina.authenticator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.authenticator.jaspic.CallbackHandlerImpl;
import org.apache.catalina.connector.Request;
import org.junit.Assert;
import org.junit.Test;

public class TestJaspicCallbackHandlerInAuthenticator {

    @Test
    public void testCustomCallbackHandlerCreation() throws Exception {
        testCallbackHandlerCreation("org.apache.catalina.authenticator.TestCallbackHandlerImpl", TestCallbackHandlerImpl.class);
    }

    @Test
    public void testDefaultCallbackHandlerCreation() throws Exception {
        testCallbackHandlerCreation(null, CallbackHandlerImpl.class);
    }


    private void testCallbackHandlerCreation(String callbackHandlerImplClassName, Class<?> callbackHandlerImplClass)
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        TestAuthenticator authenticator = new TestAuthenticator();
        authenticator.setJaspicCallbackHandlerClass(callbackHandlerImplClassName);
        Method createCallbackHandlerMethod = AuthenticatorBase.class.getDeclaredMethod("createCallbackHandler");
        createCallbackHandlerMethod.setAccessible(true);
        CallbackHandler callbackHandler = (CallbackHandler) createCallbackHandlerMethod.invoke(authenticator);
        Assert.assertTrue(callbackHandlerImplClass.isInstance(callbackHandler));
    }

    private static class TestAuthenticator extends AuthenticatorBase {

        @Override
        protected boolean doAuthenticate(Request request, HttpServletResponse response) throws IOException {
            return false;
        }

        @Override
        protected String getAuthMethod() {
            return null;
        }

    }
}

class TestCallbackHandlerImpl implements CallbackHandler {

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        // don't have to do anything; needed only for instantiation
    }
}