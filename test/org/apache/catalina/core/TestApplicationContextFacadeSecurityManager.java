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
package org.apache.catalina.core;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.security.SecurityUtil;
import org.apache.tomcat.util.security.SecurityManagerBaseTest;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IExpectationSetters;
import org.easymock.internal.LastControl;

@RunWith(Parameterized.class)
public final class TestApplicationContextFacadeSecurityManager extends SecurityManagerBaseTest {

    /**
     * @return {@link Collection} of non-static, non-object, public {@link
     * Method}s in {@link ApplicationContextFacade} to be run with the the Java
     * 2 {@link SecurityManager} been enabled.
     */
    @Parameterized.Parameters(name = "{index}: method={0}")
    public static Collection<Method> publicApplicationContextFacadeMethods() {
        List<Method> result = new ArrayList<>();
        for (Method m : ApplicationContextFacade.class.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) {
                try {
                    Object.class.getMethod(m.getName(), m.getParameterTypes());
                    // Skip;
                } catch (final NoSuchMethodException e) {
                    result.add(m);
                }
            }
        }
        return result;
    }


    private static Object[] getDefaultParams(final Method method) {
        final Class<?>[] paramTypes = method.getParameterTypes();
        final Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = getDefaultValue(paramTypes[i]);
        }
        return params;
    }


    @SuppressWarnings("unchecked")
    private static <T> T getDefaultValue(final Class<T> clazz) {
        return !isVoid(clazz) ? (T) Array.get(Array.newInstance(clazz, 1), 0) : null;
    }


    private static <T> boolean isVoid(Class<T> clazz) {
        return void.class.equals(clazz) || Void.class.equals(clazz);
    }


    @Parameter(0)
    public Method methodToTest;


    /**
     * Test for
     * <a href="https://bz.apache.org/bugzilla/show_bug.cgi?id=64735">Bug
     * 64735</a> which confirms that {@link ApplicationContextFacade} behaves
     * correctly when the Java 2 {@link SecurityManager} has been enabled.
     *
     * @throws NoSuchMethodException     Should never happen
     * @throws IllegalAccessException    Should never happen
     * @throws InvocationTargetException Should never happen
     */
    @Test
    public void testBug64735()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Assert.assertTrue(SecurityUtil.isPackageProtectionEnabled());

        // Mock the ApplicationContext that we provide to the ApplicationContextFacade.
        final ApplicationContext mockAppContext = EasyMock.createMock(ApplicationContext.class);
        final Method expectedAppContextMethod =
                ApplicationContext.class.getMethod(
                        methodToTest.getName(),
                        methodToTest.getParameterTypes());

        // Expect that only the provided method which is being tested will be called exactly once.
        final IExpectationSetters<Object> expectationSetters;
        if (isVoid(expectedAppContextMethod.getReturnType())) {
            expectedAppContextMethod.invoke(mockAppContext, getDefaultParams(methodToTest));
            expectationSetters = EasyMock.expectLastCall();
        } else {
            expectationSetters =
                    EasyMock.expect(expectedAppContextMethod.invoke(
                            mockAppContext, getDefaultParams(methodToTest)));
        }
        expectationSetters.andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                Assert.assertEquals(
                        expectedAppContextMethod,
                        LastControl.getCurrentInvocation().getMethod());
                return getDefaultValue(expectedAppContextMethod.getReturnType());
            }

        }).once();

        EasyMock.replay(mockAppContext);
        EasyMock.verifyUnexpectedCalls(mockAppContext);

        // Invoke the method on ApplicationContextFacade. Fail if any unexpected exceptions are
        // thrown.
        try {
            methodToTest.invoke(
                    new ApplicationContextFacade(mockAppContext),
                    getDefaultParams(methodToTest));
        } catch (final IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(
                    "Failed to call " +
                            methodToTest +
                            " with SecurityManager enabled.",
                    e);
        }

        // Verify that the method called through to the wrapped ApplicationContext correctly.
        EasyMock.verifyRecording();
    }
}
