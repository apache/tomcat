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


import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Properties;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.io.InputStream;
import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import javax.xml.ws.WebServiceRef;
import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.apache.InstanceManager;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.core.Constants;
import org.apache.catalina.util.StringManager;

/**
 * @version $Rev:$ $Date:$
 */
public class DefaultInstanceManager implements InstanceManager {

    private final Context context;
    private final Map<String, Map<String, String>> injectionMap;
    protected final ClassLoader classLoader;
    protected final ClassLoader containerClassLoader;
    protected boolean privileged;
    protected boolean ignoreAnnotations;
    private Properties restrictedFilters = new Properties();
    private Properties restrictedListeners = new Properties();
    private Properties restrictedServlets = new Properties();

    public DefaultInstanceManager(Context context, Map<String, Map<String, String>> injectionMap, org.apache.catalina.Context catalinaContext, ClassLoader containerClassLoader) {
        classLoader = catalinaContext.getLoader().getClassLoader();
        privileged = catalinaContext.getPrivileged();
        this.containerClassLoader = containerClassLoader;
        ignoreAnnotations = catalinaContext.getIgnoreAnnotations();
        StringManager sm = StringManager.getManager(Constants.Package);
        try {
            InputStream is =
                this.getClass().getClassLoader().getResourceAsStream
                    ("org/apache/catalina/core/RestrictedServlets.properties");
            if (is != null) {
                restrictedServlets.load(is);
            } else {
                catalinaContext.getLogger().error(sm.getString("defaultInstanceManager.restrictedServletsResource"));
            }
        } catch (IOException e) {
            catalinaContext.getLogger().error(sm.getString("defaultInstanceManager.restrictedServletsResource"), e);
        }

        try {
            InputStream is =
                    this.getClass().getClassLoader().getResourceAsStream
                            ("org/apache/catalina/core/RestrictedListeners.properties");
            if (is != null) {
                restrictedFilters.load(is);
            } else {
                catalinaContext.getLogger().error(sm.getString("defaultInstanceManager.restrictedListenersResources"));
            }
        } catch (IOException e) {
            catalinaContext.getLogger().error(sm.getString("defaultInstanceManager.restrictedListenersResources"), e);
        }
        try {
            InputStream is =
                    this.getClass().getClassLoader().getResourceAsStream
                            ("org/apache/catalina/core/RestrictedFilters.properties");
            if (is != null) {
                restrictedFilters.load(is);
            } else {
                catalinaContext.getLogger().error(sm.getString("defaultInstanceManager.restrictedFiltersResources"));
            }
        } catch (IOException e) {
            catalinaContext.getLogger().error(sm.getString("defaultInstanceManager.restrictedServletsResources"), e);
        }
        this.context = context;
        this.injectionMap = injectionMap;
    }

    public Object newInstance(String className) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException, ClassNotFoundException {
        Class clazz = loadClassMaybePrivileged(className, classLoader);
        return newInstance(clazz.newInstance(), clazz);
    }

    public Object newInstance(final String className, final ClassLoader classLoader) throws IllegalAccessException, NamingException, InvocationTargetException, InstantiationException, ClassNotFoundException {
        Class clazz = classLoader.loadClass(className);
        return newInstance(clazz.newInstance(), clazz);
    }

    public void newInstance(Object o) 
		throws IllegalAccessException, InvocationTargetException, NamingException {
    	newInstance(o, o.getClass());
    }

    private Object newInstance(Object instance, Class clazz) throws IllegalAccessException, InvocationTargetException, NamingException {
        if (!ignoreAnnotations) {
            Map<String, String> injections = injectionMap.get(clazz.getName());
            processAnnotations(instance, injections);
            postConstruct(instance, clazz);
        }
        return instance;
    }

    public void destroyInstance(Object instance) throws IllegalAccessException, InvocationTargetException {
        if (!ignoreAnnotations) {
            preDestroy(instance, instance.getClass());
        }
    }

    /**
     * Call postConstruct method on the specified instance recursively from deepest superclass to actual class.
     *
     * @param instance object to call postconstruct methods on
     * @param clazz    (super) class to examine for postConstruct annotation.
     * @throws IllegalAccessException if postConstruct method is inaccessible.
     * @throws java.lang.reflect.InvocationTargetException
     *                                if call fails
     */
    protected void postConstruct(Object instance, Class clazz)
            throws IllegalAccessException, InvocationTargetException {
        Class superClass = clazz.getSuperclass();
        if (superClass != Object.class) {
            postConstruct(instance, superClass);
        }

        Method[] methods = clazz.getDeclaredMethods();
        Method postConstruct = null;
        for (Method method : methods) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                if ((postConstruct != null)
                        || (method.getParameterTypes().length != 0)
                        || (Modifier.isStatic(method.getModifiers()))
                        || (method.getExceptionTypes().length > 0)
                        || (!method.getReturnType().getName().equals("void"))) {
                    throw new IllegalArgumentException("Invalid PostConstruct annotation");
                }
                postConstruct = method;
            }
        }

        // At the end the postconstruct annotated
        // method is invoked
        if (postConstruct != null) {
            boolean accessibility = postConstruct.isAccessible();
            postConstruct.setAccessible(true);
            postConstruct.invoke(instance);
            postConstruct.setAccessible(accessibility);
        }

    }


    /**
     * Call preDestroy method on the specified instance recursively from deepest superclass to actual class.
     *
     * @param instance object to call preDestroy methods on
     * @param clazz    (super) class to examine for preDestroy annotation.
     * @throws IllegalAccessException if preDestroy method is inaccessible.
     * @throws java.lang.reflect.InvocationTargetException
     *                                if call fails
     */
    protected void preDestroy(Object instance, Class clazz)
            throws IllegalAccessException, InvocationTargetException {
        Class superClass = clazz.getSuperclass();
        if (superClass != Object.class) {
            preDestroy(instance, superClass);
        }

        Method[] methods = clazz.getDeclaredMethods();
        Method preDestroy = null;
        for (Method method : methods) {
            if (method.isAnnotationPresent(PreDestroy.class)) {
                if ((method.getParameterTypes().length != 0)
                        || (Modifier.isStatic(method.getModifiers()))
                        || (method.getExceptionTypes().length > 0)
                        || (!method.getReturnType().getName().equals("void"))) {
                    throw new IllegalArgumentException("Invalid PreDestroy annotation");
                }
                preDestroy = method;
                break;
            }
        }

        // At the end the postconstruct annotated
        // method is invoked
        if (preDestroy != null) {
            boolean accessibility = preDestroy.isAccessible();
            preDestroy.setAccessible(true);
            preDestroy.invoke(instance);
            preDestroy.setAccessible(accessibility);
        }

    }


    /**
     * Inject resources in specified instance.
     *
     * @param instance   instance to inject into
     * @param injections map of injections for this class from xml deployment descriptor
     * @throws IllegalAccessException       if injection target is inaccessible
     * @throws javax.naming.NamingException if value cannot be looked up in jndi
     * @throws java.lang.reflect.InvocationTargetException
     *                                      if injection fails
     */
    protected void processAnnotations(Object instance, Map<String, String> injections)
            throws IllegalAccessException, InvocationTargetException, NamingException {

        if (context == null) {
            // No resource injection
            return;
        }

        // Initialize fields annotations
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (injections != null && injections.containsKey(field.getName())) {
                lookupFieldResource(context, instance, field, injections.get(field.getName()));
            } else if (field.isAnnotationPresent(Resource.class)) {
                Resource annotation = field.getAnnotation(Resource.class);
                lookupFieldResource(context, instance, field, annotation.name());
            } else if (field.isAnnotationPresent(EJB.class)) {
                EJB annotation = field.getAnnotation(EJB.class);
                lookupFieldResource(context, instance, field, annotation.name());
            } else if (field.isAnnotationPresent(WebServiceRef.class)) {
                WebServiceRef annotation =
                        field.getAnnotation(WebServiceRef.class);
                lookupFieldResource(context, instance, field, annotation.name());
            } else if (field.isAnnotationPresent(PersistenceContext.class)) {
                PersistenceContext annotation =
                        field.getAnnotation(PersistenceContext.class);
                lookupFieldResource(context, instance, field, annotation.name());
            } else if (field.isAnnotationPresent(PersistenceUnit.class)) {
                PersistenceUnit annotation =
                        field.getAnnotation(PersistenceUnit.class);
                lookupFieldResource(context, instance, field, annotation.name());
            }
        }

        // Initialize methods annotations
        Method[] methods = instance.getClass().getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (injections != null && methodName.startsWith("set") && methodName.length() > 3) {
                String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                if (injections.containsKey(fieldName)) {
                    lookupMethodResource(context, instance, method, injections.get(fieldName));
                    break;
                }
            }
            if (method.isAnnotationPresent(Resource.class)) {
                Resource annotation = method.getAnnotation(Resource.class);
                lookupMethodResource(context, instance, method, annotation.name());
            } else if (method.isAnnotationPresent(EJB.class)) {
                EJB annotation = method.getAnnotation(EJB.class);
                lookupMethodResource(context, instance, method, annotation.name());
            } else if (method.isAnnotationPresent(WebServiceRef.class)) {
                WebServiceRef annotation =
                        method.getAnnotation(WebServiceRef.class);
                lookupMethodResource(context, instance, method, annotation.name());
            } else if (method.isAnnotationPresent(PersistenceContext.class)) {
                PersistenceContext annotation =
                        method.getAnnotation(PersistenceContext.class);
                lookupMethodResource(context, instance, method, annotation.name());
            } else if (method.isAnnotationPresent(PersistenceUnit.class)) {
                PersistenceUnit annotation =
                        method.getAnnotation(PersistenceUnit.class);
                lookupMethodResource(context, instance, method, annotation.name());
            }
        }

    }


    protected Class loadClassMaybePrivileged(final String className, final ClassLoader classLoader) throws ClassNotFoundException {
        Class clazz;
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                clazz = AccessController.doPrivileged(new PrivilegedExceptionAction<Class>() {

                    public Class run() throws Exception {
                        return loadClass(className, classLoader);
                    }
                });
            } catch (PrivilegedActionException e) {
                Throwable t = e.getCause();
                if (t instanceof ClassNotFoundException) {
                    throw (ClassNotFoundException) t;
                }
                throw new RuntimeException(t);
            }
        } else {
            clazz = loadClass(className, classLoader);
        }
        checkAccess(clazz);
        return clazz;
    }

    protected Class loadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        if (className.startsWith("org.apache.catalina")) {
            return containerClassLoader.loadClass(className);
        }
        try {
            Class clazz = containerClassLoader.loadClass(className);
            if (ContainerServlet.class.isAssignableFrom(clazz)) {
                return clazz;
            }
        } catch (Throwable t) {
            //ignore
        }
        return classLoader.loadClass(className);
    }

    private void checkAccess(Class clazz) {
        if (privileged) return;
        if (clazz.isAssignableFrom(Filter.class)) {
            checkAccess(clazz, restrictedFilters);
        } else if (clazz.isAssignableFrom(Servlet.class)) {
            checkAccess(clazz, restrictedServlets);
        } else {
            checkAccess(clazz, restrictedListeners);
        }
    }

    private void checkAccess(Class clazz, Properties restricted) {
        while (clazz != null) {
            if ("restricted".equals(restricted.getProperty(clazz.getName()))) {
                throw new SecurityException("Restricted class" + clazz);
            }
            clazz = clazz.getSuperclass();
        }

    }

    /**
     * Inject resources in specified field.
     *
     * @param context  jndi context to extract value from
     * @param instance object to inject into
     * @param field    field target for injection
     * @param name     jndi name value is bound under
     * @throws IllegalAccessException       if field is inaccessible
     * @throws javax.naming.NamingException if value is not accessible in naming context
     */
    protected static void lookupFieldResource(Context context,
            Object instance, Field field, String name)
            throws NamingException, IllegalAccessException {

        Object lookedupResource;
        boolean accessibility;

        if ((name != null) &&
                (name.length() > 0)) {
            lookedupResource = context.lookup(name);
        } else {
            lookedupResource = context.lookup(instance.getClass().getName() + "/" + field.getName());
        }

        accessibility = field.isAccessible();
        field.setAccessible(true);
        field.set(instance, lookedupResource);
        field.setAccessible(accessibility);
    }

    /**
     * Inject resources in specified method.
     *
     * @param context  jndi context to extract value from
     * @param instance object to inject into
     * @param method   field target for injection
     * @param name     jndi name value is bound under
     * @throws IllegalAccessException       if method is inaccessible
     * @throws javax.naming.NamingException if value is not accessible in naming context
     * @throws java.lang.reflect.InvocationTargetException
     *                                      if setter call fails
     */
    protected static void lookupMethodResource(Context context,
            Object instance, Method method, String name)
            throws NamingException, IllegalAccessException, InvocationTargetException {

        if (!method.getName().startsWith("set")
                || method.getParameterTypes().length != 1
                || !method.getReturnType().getName().equals("void")) {
            throw new IllegalArgumentException("Invalid method resource injection annotation");
        }

        Object lookedupResource;
        boolean accessibility;

        if ((name != null) &&
                (name.length() > 0)) {
            lookedupResource = context.lookup(name);
        } else {
            lookedupResource =
                    context.lookup(instance.getClass().getName() + "/" + method.getName().substring(3));
        }

        accessibility = method.isAccessible();
        method.setAccessible(true);
        method.invoke(instance, lookedupResource);
        method.setAccessible(accessibility);
    }
}
