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


import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.xml.ws.WebServiceRef;

import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityUtil;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * @version $Id$
 */
public class DefaultInstanceManager implements InstanceManager {

    private final Context context;
    private final Map<String, Map<String, String>> injectionMap;
    protected final ClassLoader classLoader;
    protected final ClassLoader containerClassLoader;
    protected boolean privileged;
    protected boolean ignoreAnnotations;
    private final Properties restrictedFilters = new Properties();
    private final Properties restrictedListeners = new Properties();
    private final Properties restrictedServlets = new Properties();
    private final Map<Class<?>,WeakReference<List<AnnotationCacheEntry>>> annotationCache =
        new WeakHashMap<Class<?>, WeakReference<List<AnnotationCacheEntry>>>();

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
                restrictedListeners.load(is);
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

    @Override
    public Object newInstance(String className) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException, ClassNotFoundException {
        Class<?> clazz = loadClassMaybePrivileged(className, classLoader);
        return newInstance(clazz.newInstance(), clazz);
    }

    @Override
    public Object newInstance(final String className, final ClassLoader classLoader) throws IllegalAccessException, NamingException, InvocationTargetException, InstantiationException, ClassNotFoundException {
        Class<?> clazz = classLoader.loadClass(className);
        return newInstance(clazz.newInstance(), clazz);
    }

    @Override
    public void newInstance(Object o)
            throws IllegalAccessException, InvocationTargetException, NamingException {
        newInstance(o, o.getClass());
    }

    private Object newInstance(Object instance, Class<?> clazz) throws IllegalAccessException, InvocationTargetException, NamingException {
        if (!ignoreAnnotations) {
            Map<String, String> injections = injectionMap.get(clazz.getName());
            populateAnnotationsCache(clazz, injections);
            processAnnotations(instance, injections);
            postConstruct(instance, clazz);
        }
        return instance;
    }

    @Override
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
    protected void postConstruct(Object instance, final Class<?> clazz)
            throws IllegalAccessException, InvocationTargetException {
        if (context == null) {
            // No resource injection
            return;
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != Object.class) {
            postConstruct(instance, superClass);
        }

        // At the end the postconstruct annotated
        // method is invoked
        List<AnnotationCacheEntry> annotations;
        synchronized (annotationCache) {
            annotations = annotationCache.get(clazz).get();
        }
        for (AnnotationCacheEntry entry : annotations) {
            if (entry.getType() == AnnotationCacheEntryType.POST_CONSTRUCT) {
                Method postConstruct = (Method) entry.getAccessibleObject();
                boolean accessibility = postConstruct.isAccessible();
                synchronized (postConstruct) {
                    postConstruct.setAccessible(true);
                    postConstruct.invoke(instance);
                    postConstruct.setAccessible(accessibility);
                }
            }
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
    protected void preDestroy(Object instance, final Class<?> clazz)
            throws IllegalAccessException, InvocationTargetException {
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != Object.class) {
            preDestroy(instance, superClass);
        }

        // At the end the postconstruct annotated
        // method is invoked
        List<AnnotationCacheEntry> annotations = null;
        synchronized (annotationCache) {
            WeakReference<List<AnnotationCacheEntry>> ref =
                annotationCache.get(clazz);
            if (ref != null) {
                annotations = ref.get();
            }
        }
        if (annotations == null) {
            // instance not created through the instance manager
            return;
        }
        for (AnnotationCacheEntry entry : annotations) {
            if (entry.getType() == AnnotationCacheEntryType.PRE_DESTROY) {
                Method preDestroy = (Method) entry.getAccessibleObject();
                boolean accessibility = preDestroy.isAccessible();
                synchronized (preDestroy) {
                    preDestroy.setAccessible(true);
                    preDestroy.invoke(instance);
                    preDestroy.setAccessible(accessibility);
                }
            }
        }
    }


    /**
     * Make sure that the annotations cache has been populated for the provided
     * class.
     *
     * @param clazz         clazz to populate annotations for
     * @param injections    map of injections for this class from xml deployment
     *                      descriptor
     * @throws IllegalAccessException       if injection target is inaccessible
     * @throws javax.naming.NamingException if value cannot be looked up in jndi
     * @throws java.lang.reflect.InvocationTargetException
     *                                      if injection fails
     */
    protected void populateAnnotationsCache(Class<?> clazz,
            Map<String, String> injections) throws IllegalAccessException,
            InvocationTargetException, NamingException {

        while (clazz != null) {
            List<AnnotationCacheEntry> annotations = null;
            synchronized (annotationCache) {
                WeakReference<List<AnnotationCacheEntry>> ref =
                    annotationCache.get(clazz);
                if (ref != null) {
                    annotations = ref.get();
                }
            }
            if (annotations == null) {
                annotations = new ArrayList<AnnotationCacheEntry>();

                if (context != null) {
                    // Initialize fields annotations for resource injection if
                    // JNDI is enabled
                    Field[] fields = null;
                    if (Globals.IS_SECURITY_ENABLED) {
                        final Class<?> clazz2 = clazz;
                        fields = AccessController.doPrivileged(
                                new PrivilegedAction<Field[]>(){
                            @Override
                            public Field[] run(){
                                return clazz2.getDeclaredFields();
                            }
                        });
                    } else {
                        fields = clazz.getDeclaredFields();
                    }
                    for (Field field : fields) {
                        if (injections != null && injections.containsKey(field.getName())) {
                            annotations.add(new AnnotationCacheEntry(field,
                                    injections.get(field.getName()),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (field.isAnnotationPresent(Resource.class)) {
                            Resource annotation = field.getAnnotation(Resource.class);
                            annotations.add(new AnnotationCacheEntry(field,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (field.isAnnotationPresent(EJB.class)) {
                            EJB annotation = field.getAnnotation(EJB.class);
                            annotations.add(new AnnotationCacheEntry(field,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (field.isAnnotationPresent(WebServiceRef.class)) {
                            WebServiceRef annotation =
                                    field.getAnnotation(WebServiceRef.class);
                            annotations.add(new AnnotationCacheEntry(field,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (field.isAnnotationPresent(PersistenceContext.class)) {
                            PersistenceContext annotation =
                                    field.getAnnotation(PersistenceContext.class);
                            annotations.add(new AnnotationCacheEntry(field,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (field.isAnnotationPresent(PersistenceUnit.class)) {
                            PersistenceUnit annotation =
                                    field.getAnnotation(PersistenceUnit.class);
                            annotations.add(new AnnotationCacheEntry(field,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        }
                    }
                }

                // Initialize methods annotations
                Method[] methods = null;
                if (Globals.IS_SECURITY_ENABLED) {
                    final Class<?> clazz2 = clazz;
                    methods = AccessController.doPrivileged(
                            new PrivilegedAction<Method[]>(){
                        @Override
                        public Method[] run(){
                            return clazz2.getDeclaredMethods();
                        }
                    });
                } else {
                    methods = clazz.getDeclaredMethods();
                }
                Method postConstruct = null;
                Method preDestroy = null;
                for (Method method : methods) {
                    String methodName = method.getName();
                    if (context != null) {
                        // Resource injection only if JNDI is enabled
                        if (injections != null && methodName.startsWith("set") && methodName.length() > 3) {
                            String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                            if (injections.containsKey(fieldName)) {
                                annotations.add(new AnnotationCacheEntry(method,
                                        injections.get(method.getName()),
                                        AnnotationCacheEntryType.FIELD));
                                break;
                            }
                        }
                        if (method.isAnnotationPresent(Resource.class)) {
                            Resource annotation = method.getAnnotation(Resource.class);
                            annotations.add(new AnnotationCacheEntry(method,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (method.isAnnotationPresent(EJB.class)) {
                            EJB annotation = method.getAnnotation(EJB.class);
                            annotations.add(new AnnotationCacheEntry(method,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (method.isAnnotationPresent(WebServiceRef.class)) {
                            WebServiceRef annotation =
                                    method.getAnnotation(WebServiceRef.class);
                            annotations.add(new AnnotationCacheEntry(method,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (method.isAnnotationPresent(PersistenceContext.class)) {
                            PersistenceContext annotation =
                                    method.getAnnotation(PersistenceContext.class);
                            annotations.add(new AnnotationCacheEntry(method,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (method.isAnnotationPresent(PersistenceUnit.class)) {
                            PersistenceUnit annotation =
                                    method.getAnnotation(PersistenceUnit.class);
                            annotations.add(new AnnotationCacheEntry(method,
                                    annotation.name(),
                                    AnnotationCacheEntryType.FIELD));
                        }
                    }

                    if (method.isAnnotationPresent(PostConstruct.class)) {
                        if ((postConstruct != null) ||
                                (method.getParameterTypes().length != 0) ||
                                (Modifier.isStatic(method.getModifiers())) ||
                                (method.getExceptionTypes().length > 0) ||
                                (!method.getReturnType().getName().equals("void"))) {
                            throw new IllegalArgumentException(
                                    "Invalid PostConstruct annotation");
                        }
                        postConstruct = method;
                    }

                    if (method.isAnnotationPresent(PreDestroy.class)) {
                        if ((preDestroy != null ||
                                method.getParameterTypes().length != 0) ||
                                (Modifier.isStatic(method.getModifiers())) ||
                                (method.getExceptionTypes().length > 0) ||
                                (!method.getReturnType().getName().equals("void"))) {
                            throw new IllegalArgumentException(
                                    "Invalid PreDestroy annotation");
                        }
                        preDestroy = method;
                    }
                }
                if (postConstruct != null) {
                    annotations.add(new AnnotationCacheEntry(postConstruct,
                            null, AnnotationCacheEntryType.POST_CONSTRUCT));
                }
                if (preDestroy != null) {
                    annotations.add(new AnnotationCacheEntry(preDestroy,
                            null, AnnotationCacheEntryType.PRE_DESTROY));
                }
                if (annotations.size() == 0) {
                    // Use common empty list to save memory
                    annotations = Collections.emptyList();
                }
                synchronized (annotationCache) {
                    annotationCache.put(clazz,
                            new WeakReference<List<AnnotationCacheEntry>>(
                                    annotations));
                }
            } else {
                // If the annotations for this class have been cached, the
                // annotations for all the super classes will have been cachced
                // as well
                break;
            }
            clazz = clazz.getSuperclass();
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

        Class<?> clazz = instance.getClass();

        while (clazz != null) {
            List<AnnotationCacheEntry> annotations;
            synchronized (annotationCache) {
                annotations = annotationCache.get(clazz).get();
            }
            for (AnnotationCacheEntry entry : annotations) {
                if (entry.getType() == AnnotationCacheEntryType.FIELD) {
                    if (entry.getAccessibleObject() instanceof Method) {
                        lookupMethodResource(context, instance,
                                (Method) entry.getAccessibleObject(),
                                entry.getName(), clazz);
                    } else {
                        lookupFieldResource(context, instance,
                                (Field) entry.getAccessibleObject(),
                                entry.getName(), clazz);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }


    /**
     * Makes cache size available to unit tests.
     */
    protected int getAnnotationCacheSize() {
        synchronized (annotationCache) {
            return annotationCache.size();
        }
    }


    protected Class<?> loadClassMaybePrivileged(final String className, final ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> clazz;
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                clazz = AccessController.doPrivileged(new PrivilegedExceptionAction<Class<?>>() {

                    @Override
                    public Class<?> run() throws Exception {
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

    protected Class<?> loadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        if (className.startsWith("org.apache.catalina")) {
            return containerClassLoader.loadClass(className);
        }
        try {
            Class<?> clazz = containerClassLoader.loadClass(className);
            if (ContainerServlet.class.isAssignableFrom(clazz)) {
                return clazz;
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }
        return classLoader.loadClass(className);
    }

    private void checkAccess(Class<?> clazz) {
        if (privileged) {
            return;
        }
        if (Filter.class.isAssignableFrom(clazz)) {
            checkAccess(clazz, restrictedFilters);
        } else if (Servlet.class.isAssignableFrom(clazz)) {
            if (ContainerServlet.class.isAssignableFrom(clazz)) {
                throw new SecurityException("Restricted (ContainerServlet) " +
                        clazz);
            }
            checkAccess(clazz, restrictedServlets);
        } else {
            checkAccess(clazz, restrictedListeners);
        }
    }

    private void checkAccess(Class<?> clazz, Properties restricted) {
        while (clazz != null) {
            if ("restricted".equals(restricted.getProperty(clazz.getName()))) {
                throw new SecurityException("Restricted " + clazz);
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
     * @param clazz    class annotation is defined in
     * @throws IllegalAccessException       if field is inaccessible
     * @throws javax.naming.NamingException if value is not accessible in naming context
     */
    protected static void lookupFieldResource(Context context,
            Object instance, Field field, String name, Class<?> clazz)
            throws NamingException, IllegalAccessException {

        Object lookedupResource;
        boolean accessibility;

        String normalizedName = normalize(name);

        if ((normalizedName != null) && (normalizedName.length() > 0)) {
            lookedupResource = context.lookup(normalizedName);
        } else {
            lookedupResource =
                context.lookup(clazz.getName() + "/" + field.getName());
        }

        accessibility = field.isAccessible();
        synchronized (field) {
            field.setAccessible(true);
            field.set(instance, lookedupResource);
            field.setAccessible(accessibility);
        }
    }

    /**
     * Inject resources in specified method.
     *
     * @param context  jndi context to extract value from
     * @param instance object to inject into
     * @param method   field target for injection
     * @param name     jndi name value is bound under
     * @param clazz    class annotation is defined in
     * @throws IllegalAccessException       if method is inaccessible
     * @throws javax.naming.NamingException if value is not accessible in naming context
     * @throws java.lang.reflect.InvocationTargetException
     *                                      if setter call fails
     */
    protected static void lookupMethodResource(Context context,
            Object instance, Method method, String name, Class<?> clazz)
            throws NamingException, IllegalAccessException, InvocationTargetException {

        if (!method.getName().startsWith("set")
                || method.getName().length() < 4
                || method.getParameterTypes().length != 1
                || !method.getReturnType().getName().equals("void")) {
            throw new IllegalArgumentException("Invalid method resource injection annotation");
        }

        Object lookedupResource;
        boolean accessibility;

        String normalizedName = normalize(name);

        if ((normalizedName != null) && (normalizedName.length() > 0)) {
            lookedupResource = context.lookup(normalizedName);
        } else {
            lookedupResource = context.lookup(
                    clazz.getName() + "/" + getName(method));
        }

        accessibility = method.isAccessible();
        synchronized (method) {
            method.setAccessible(true);
            method.invoke(instance, lookedupResource);
            method.setAccessible(accessibility);
        }
    }

    public static String getName(Method setter) {
        StringBuilder name = new StringBuilder(setter.getName());

        // remove 'set'
        name.delete(0, 3);

        // lowercase first char
        name.setCharAt(0, Character.toLowerCase(name.charAt(0)));

        return name.toString();
    }

    private static String normalize(String jndiName){
        if(jndiName != null && jndiName.startsWith("java:comp/env/")){
            return jndiName.substring(14);
        }
        return jndiName;
    }

    private static final class AnnotationCacheEntry {
        private final AccessibleObject accessibleObject;
        private final String name;
        private final AnnotationCacheEntryType type;

        public AnnotationCacheEntry(AccessibleObject accessibleObject,
                String name, AnnotationCacheEntryType type) {
            this.accessibleObject = accessibleObject;
            this.name = name;
            this.type = type;
        }

        public AccessibleObject getAccessibleObject() {
            return accessibleObject;
        }

        public String getName() {
            return name;
        }
        public AnnotationCacheEntryType getType() {
            return type;
        }
    }

    private static enum AnnotationCacheEntryType {
        FIELD, POST_CONSTRUCT, PRE_DESTROY
    }
}
