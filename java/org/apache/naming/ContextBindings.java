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
package org.apache.naming;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Handles the associations :
 * <ul>
 * <li>Object with a NamingContext</li>
 * <li>Calling thread with a NamingContext</li>
 * <li>Calling thread with object bound to the same naming context</li>
 * <li>Thread context class loader with a NamingContext</li>
 * <li>Thread context class loader with object bound to the same
 *     NamingContext</li>
 * </ul>
 * The objects are typically Catalina Server or Context objects.
 *
 * @author Remy Maucherat
 */
public class ContextBindings {

    // -------------------------------------------------------------- Variables

    /**
     * Bindings object - naming context. Keyed by object.
     */
    private static final Map<Object,Context> objectBindings = new ConcurrentHashMap<>();


    /**
     * Bindings thread - naming context. Keyed by thread.
     */
    private static final Map<Thread,Context> threadBindings = new ConcurrentHashMap<>();


    /**
     * Bindings thread - object. Keyed by thread.
     */
    private static final Map<Thread,Object> threadObjectBindings = new ConcurrentHashMap<>();


    /**
     * Bindings class loader - naming context. Keyed by class loader.
     */
    private static final Map<ClassLoader,Context> clBindings = new ConcurrentHashMap<>();


    /**
     * Bindings class loader - object. Keyed by class loader.
     */
    private static final Map<ClassLoader,Object> clObjectBindings = new ConcurrentHashMap<>();


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ContextBindings.class);


    // --------------------------------------------------------- Public Methods

    /**
     * Binds an object and a naming context.
     *
     * @param obj       Object to bind with naming context
     * @param context   Associated naming context instance
     */
    public static void bindContext(Object obj, Context context) {
        bindContext(obj, context, null);
    }


    /**
     * Binds an object and a naming context.
     *
     * @param obj       Object to bind with naming context
     * @param context   Associated naming context instance
     * @param token     Security token
     */
    public static void bindContext(Object obj, Context context, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            objectBindings.put(obj, context);
        }
    }


    /**
     * Unbinds an object and a naming context.
     *
     * @param obj   Object to unbind
     * @param token Security token
     */
    public static void unbindContext(Object obj, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            objectBindings.remove(obj);
        }
    }


    /**
     * Retrieve a naming context.
     *
     * @param obj   Object bound to the required naming context
     */
    static Context getContext(Object obj) {
        return objectBindings.get(obj);
    }


    /**
     * Binds a naming context to a thread.
     *
     * @param obj   Object bound to the required naming context
     * @param token Security token
     *
     * @throws NamingException If no naming context is bound to the provided
     *         object
     */
    public static void bindThread(Object obj, Object token) throws NamingException {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Context context = objectBindings.get(obj);
            if (context == null) {
                throw new NamingException(
                        sm.getString("contextBindings.unknownContext", obj));
            }
            Thread currentThread = Thread.currentThread();
            threadBindings.put(currentThread, context);
            threadObjectBindings.put(currentThread, obj);
        }
    }


    /**
     * Unbinds a thread and a naming context.
     *
     * @param obj   Object bound to the required naming context
     * @param token Security token
     */
    public static void unbindThread(Object obj, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Thread currentThread = Thread.currentThread();
            threadBindings.remove(currentThread);
            threadObjectBindings.remove(currentThread);
        }
    }


    /**
     * Retrieves the naming context bound to the current thread.
     *
     * @return The naming context bound to the current thread.
     *
     * @throws NamingException If no naming context is bound to the current
     *         thread
     */
    public static Context getThread() throws NamingException {
        Context context = threadBindings.get(Thread.currentThread());
        if (context == null) {
            throw new NamingException
                    (sm.getString("contextBindings.noContextBoundToThread"));
        }
        return context;
    }


    /**
     * Retrieves the name of the object bound to the naming context that is also
     * bound to the current thread.
     */
    static String getThreadName() throws NamingException {
        Object obj = threadObjectBindings.get(Thread.currentThread());
        if (obj == null) {
            throw new NamingException
                    (sm.getString("contextBindings.noContextBoundToThread"));
        }
        return obj.toString();
    }


    /**
     * Tests if current thread is bound to a naming context.
     *
     * @return <code>true</code> if the current thread is bound to a naming
     *         context, otherwise <code>false</code>
     */
    public static boolean isThreadBound() {
        return threadBindings.containsKey(Thread.currentThread());
    }


    /**
     * Binds a naming context to a class loader.
     *
     * @param obj           Object bound to the required naming context
     * @param token         Security token
     * @param classLoader   The class loader to bind to the naming context
     *
     * @throws NamingException If no naming context is bound to the provided
     *         object
     */
    public static void bindClassLoader(Object obj, Object token,
            ClassLoader classLoader) throws NamingException {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Context context = objectBindings.get(obj);
            if (context == null) {
                throw new NamingException
                        (sm.getString("contextBindings.unknownContext", obj));
            }
            clBindings.put(classLoader, context);
            clObjectBindings.put(classLoader, obj);
        }
    }


    /**
     * Unbinds a naming context and a class loader.
     *
     * @param obj           Object bound to the required naming context
     * @param token         Security token
     * @param classLoader   The class loader bound to the naming context
     */
    public static void unbindClassLoader(Object obj, Object token,
            ClassLoader classLoader) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Object o = clObjectBindings.get(classLoader);
            if (o == null || !o.equals(obj)) {
                return;
            }
            clBindings.remove(classLoader);
            clObjectBindings.remove(classLoader);
        }
    }


    /**
     * Retrieves the naming context bound to a class loader.
     *
     * @return the naming context bound to current class loader or one of its
     *         parents
     *
     * @throws NamingException If no naming context was bound
     */
    public static Context getClassLoader() throws NamingException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Context context = null;
        do {
            context = clBindings.get(cl);
            if (context != null) {
                return context;
            }
        } while ((cl = cl.getParent()) != null);
        throw new NamingException(sm.getString("contextBindings.noContextBoundToCL"));
    }


    /**
     * Retrieves the name of the object bound to the naming context that is also
     * bound to the thread context class loader.
     */
    static String getClassLoaderName() throws NamingException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Object obj = null;
        do {
            obj = clObjectBindings.get(cl);
            if (obj != null) {
                return obj.toString();
            }
        } while ((cl = cl.getParent()) != null);
        throw new NamingException (sm.getString("contextBindings.noContextBoundToCL"));
    }


    /**
     * Tests if the thread context class loader is bound to a context.
     *
     * @return <code>true</code> if the thread context class loader or one of
     *         its parents is bound to a naming context, otherwise
     *         <code>false</code>
     */
    public static boolean isClassLoaderBound() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        do {
            if (clBindings.containsKey(cl)) {
                return true;
            }
        } while ((cl = cl.getParent()) != null);
        return false;
    }
}
