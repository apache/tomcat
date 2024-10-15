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
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import jakarta.xml.ws.WebServiceRef;

import org.apache.catalina.ContainerServlet;
import org.apache.catalina.util.Introspection;
import org.apache.juli.logging.Log;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.ManagedConcurrentWeakHashMap;
import org.apache.tomcat.util.res.StringManager;

public class DefaultInstanceManager implements InstanceManager {

    // Used when there are no annotations in a class
    private static final AnnotationCacheEntry[] ANNOTATIONS_EMPTY = new AnnotationCacheEntry[0];

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(DefaultInstanceManager.class);

    private static final boolean EJB_PRESENT;
    private static final boolean JPA_PRESENT;
    private static final boolean WS_PRESENT;

    static {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("jakarta.ejb.EJB");
        } catch (ClassNotFoundException cnfe) {
            // Expected
        }
        EJB_PRESENT = (clazz != null);

        clazz = null;
        try {
            clazz = Class.forName("jakarta.persistence.PersistenceContext");
        } catch (ClassNotFoundException cnfe) {
            // Expected
        }
        JPA_PRESENT = (clazz != null);

        clazz = null;
        try {
            clazz = Class.forName("jakarta.xml.ws.WebServiceRef");
        } catch (ClassNotFoundException cnfe) {
            // Expected
        }
        WS_PRESENT = (clazz != null);
    }


    private final Context context;
    private final Map<String,Map<String,String>> injectionMap;
    protected final ClassLoader classLoader;
    protected final ClassLoader containerClassLoader;
    protected final boolean privileged;
    protected final boolean ignoreAnnotations;
    protected final boolean metadataComplete;
    private final Set<String> restrictedClasses;
    private final ManagedConcurrentWeakHashMap<Class<?>,AnnotationCacheEntry[]> annotationCache =
            new ManagedConcurrentWeakHashMap<>();
    private final Map<String,String> postConstructMethods;
    private final Map<String,String> preDestroyMethods;

    public DefaultInstanceManager(Context context, Map<String,Map<String,String>> injectionMap,
            org.apache.catalina.Context catalinaContext, ClassLoader containerClassLoader) {
        classLoader = catalinaContext.getLoader().getClassLoader();
        privileged = catalinaContext.getPrivileged();
        this.containerClassLoader = containerClassLoader;
        ignoreAnnotations = catalinaContext.getIgnoreAnnotations();
        metadataComplete = catalinaContext.getMetadataComplete();
        Log log = catalinaContext.getLogger();
        Set<String> classNames = new HashSet<>();
        loadProperties(classNames, "org/apache/catalina/core/RestrictedServlets.properties",
                "defaultInstanceManager.restrictedServletsResource", log);
        loadProperties(classNames, "org/apache/catalina/core/RestrictedListeners.properties",
                "defaultInstanceManager.restrictedListenersResource", log);
        loadProperties(classNames, "org/apache/catalina/core/RestrictedFilters.properties",
                "defaultInstanceManager.restrictedFiltersResource", log);
        restrictedClasses = Collections.unmodifiableSet(classNames);
        this.context = context;
        this.injectionMap = injectionMap;
        this.postConstructMethods = catalinaContext.findPostConstructMethods();
        this.preDestroyMethods = catalinaContext.findPreDestroyMethods();
    }

    @Override
    public Object newInstance(Class<?> clazz) throws IllegalAccessException, InvocationTargetException, NamingException,
            InstantiationException, IllegalArgumentException, NoSuchMethodException, SecurityException {
        return newInstance(clazz.getConstructor().newInstance(), clazz);
    }

    @Override
    public Object newInstance(String className)
            throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException,
            ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, SecurityException {
        Class<?> clazz = loadClassMaybePrivileged(className, classLoader);
        return newInstance(clazz.getConstructor().newInstance(), clazz);
    }

    @Override
    public Object newInstance(final String className, final ClassLoader classLoader)
            throws IllegalAccessException, NamingException, InvocationTargetException, InstantiationException,
            ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, SecurityException {
        Class<?> clazz = classLoader.loadClass(className);
        return newInstance(clazz.getConstructor().newInstance(), clazz);
    }

    @Override
    public void newInstance(Object o) throws IllegalAccessException, InvocationTargetException, NamingException {
        newInstance(o, o.getClass());
    }

    private Object newInstance(Object instance, Class<?> clazz)
            throws IllegalAccessException, InvocationTargetException, NamingException {
        if (!ignoreAnnotations) {
            Map<String,String> injections = assembleInjectionsFromClassHierarchy(clazz);
            populateAnnotationsCache(clazz, injections);
            processAnnotations(instance, injections);
            postConstruct(instance, clazz);
        }
        return instance;
    }

    private Map<String,String> assembleInjectionsFromClassHierarchy(Class<?> clazz) {
        Map<String,String> injections = new HashMap<>();
        Map<String,String> currentInjections = null;
        while (clazz != null) {
            currentInjections = this.injectionMap.get(clazz.getName());
            if (currentInjections != null) {
                injections.putAll(currentInjections);
            }
            clazz = clazz.getSuperclass();
        }
        return injections;
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
     *
     * @throws IllegalAccessException                      if postConstruct method is inaccessible.
     * @throws java.lang.reflect.InvocationTargetException if call fails
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
        AnnotationCacheEntry[] annotations = annotationCache.get(clazz);
        for (AnnotationCacheEntry entry : annotations) {
            if (entry.getType() == AnnotationCacheEntryType.POST_CONSTRUCT) {
                // This will always return a new Method instance
                // Making this instance accessible does not affect other instances
                Method postConstruct = getMethod(clazz, entry);
                // If this doesn't work, just let invoke() fail
                postConstruct.trySetAccessible();
                postConstruct.invoke(instance);
            }
        }
    }


    /**
     * Call preDestroy method on the specified instance recursively from deepest superclass to actual class.
     *
     * @param instance object to call preDestroy methods on
     * @param clazz    (super) class to examine for preDestroy annotation.
     *
     * @throws IllegalAccessException                      if preDestroy method is inaccessible.
     * @throws java.lang.reflect.InvocationTargetException if call fails
     */
    protected void preDestroy(Object instance, final Class<?> clazz)
            throws IllegalAccessException, InvocationTargetException {
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != Object.class) {
            preDestroy(instance, superClass);
        }

        // At the end the postconstruct annotated
        // method is invoked
        AnnotationCacheEntry[] annotations = annotationCache.get(clazz);
        if (annotations == null) {
            // instance not created through the instance manager
            return;
        }
        for (AnnotationCacheEntry entry : annotations) {
            if (entry.getType() == AnnotationCacheEntryType.PRE_DESTROY) {
                // This will always return a new Method instance
                // Making this instance accessible does not affect other instances
                Method preDestroy = getMethod(clazz, entry);
                // If this doesn't work, just let invoke() fail
                preDestroy.trySetAccessible();
                preDestroy.invoke(instance);
            }
        }
    }


    @Override
    public void backgroundProcess() {
        annotationCache.maintain();
    }


    /**
     * Make sure that the annotations cache has been populated for the provided class.
     *
     * @param clazz      clazz to populate annotations for
     * @param injections map of injections for this class from xml deployment descriptor
     *
     * @throws IllegalAccessException                      if injection target is inaccessible
     * @throws javax.naming.NamingException                if value cannot be looked up in jndi
     * @throws java.lang.reflect.InvocationTargetException if injection fails
     */
    protected void populateAnnotationsCache(Class<?> clazz, Map<String,String> injections)
            throws IllegalAccessException, InvocationTargetException, NamingException {

        List<AnnotationCacheEntry> annotations = null;
        Set<String> injectionsMatchedToSetter = new HashSet<>();

        while (clazz != null) {
            AnnotationCacheEntry[] annotationsArray = annotationCache.get(clazz);
            if (annotationsArray == null) {
                if (annotations == null) {
                    annotations = new ArrayList<>();
                } else {
                    annotations.clear();
                }

                // Initialize methods annotations
                Method[] methods = clazz.getDeclaredMethods();
                Method postConstruct = null;
                String postConstructFromXml = postConstructMethods.get(clazz.getName());
                Method preDestroy = null;
                String preDestroyFromXml = preDestroyMethods.get(clazz.getName());
                for (Method method : methods) {
                    if (context != null) {
                        // Resource injection only if JNDI is enabled
                        if (injections != null && Introspection.isValidSetter(method)) {
                            String fieldName = Introspection.getPropertyName(method);
                            injectionsMatchedToSetter.add(fieldName);
                            if (injections.containsKey(fieldName)) {
                                annotations.add(new AnnotationCacheEntry(method.getName(), method.getParameterTypes(),
                                        injections.get(fieldName), AnnotationCacheEntryType.SETTER));
                                continue;
                            }
                        }
                        if (!metadataComplete) {
                            Resource resourceAnnotation;
                            Annotation ejbAnnotation;
                            Annotation webServiceRefAnnotation;
                            Annotation persistenceContextAnnotation;
                            Annotation persistenceUnitAnnotation;
                            if ((resourceAnnotation = method.getAnnotation(Resource.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(method.getName(), method.getParameterTypes(),
                                        resourceAnnotation.name(), AnnotationCacheEntryType.SETTER));
                            } else if (EJB_PRESENT && (ejbAnnotation = method.getAnnotation(EJB.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(method.getName(), method.getParameterTypes(),
                                        ((EJB) ejbAnnotation).name(), AnnotationCacheEntryType.SETTER));
                            } else if (WS_PRESENT &&
                                    (webServiceRefAnnotation = method.getAnnotation(WebServiceRef.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(method.getName(), method.getParameterTypes(),
                                        ((WebServiceRef) webServiceRefAnnotation).name(),
                                        AnnotationCacheEntryType.SETTER));
                            } else if (JPA_PRESENT && (persistenceContextAnnotation =
                                    method.getAnnotation(PersistenceContext.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(method.getName(), method.getParameterTypes(),
                                        ((PersistenceContext) persistenceContextAnnotation).name(),
                                        AnnotationCacheEntryType.SETTER));
                            } else if (JPA_PRESENT &&
                                    (persistenceUnitAnnotation = method.getAnnotation(PersistenceUnit.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(method.getName(), method.getParameterTypes(),
                                        ((PersistenceUnit) persistenceUnitAnnotation).name(),
                                        AnnotationCacheEntryType.SETTER));
                            }
                        }
                    }

                    postConstruct = findPostConstruct(postConstruct, postConstructFromXml, method);

                    preDestroy = findPreDestroy(preDestroy, preDestroyFromXml, method);
                }

                if (postConstruct != null) {
                    annotations.add(new AnnotationCacheEntry(postConstruct.getName(), postConstruct.getParameterTypes(),
                            null, AnnotationCacheEntryType.POST_CONSTRUCT));
                } else if (postConstructFromXml != null) {
                    throw new IllegalArgumentException(sm.getString("defaultInstanceManager.postConstructNotFound",
                            postConstructFromXml, clazz.getName()));
                }
                if (preDestroy != null) {
                    annotations.add(new AnnotationCacheEntry(preDestroy.getName(), preDestroy.getParameterTypes(), null,
                            AnnotationCacheEntryType.PRE_DESTROY));
                } else if (preDestroyFromXml != null) {
                    throw new IllegalArgumentException(sm.getString("defaultInstanceManager.preDestroyNotFound",
                            preDestroyFromXml, clazz.getName()));
                }

                if (context != null) {
                    // Initialize fields annotations for resource injection if
                    // JNDI is enabled
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {
                        Resource resourceAnnotation;
                        Annotation ejbAnnotation;
                        Annotation webServiceRefAnnotation;
                        Annotation persistenceContextAnnotation;
                        Annotation persistenceUnitAnnotation;
                        String fieldName = field.getName();
                        if (injections != null && injections.containsKey(fieldName) &&
                                !injectionsMatchedToSetter.contains(fieldName)) {
                            annotations.add(new AnnotationCacheEntry(fieldName, null, injections.get(fieldName),
                                    AnnotationCacheEntryType.FIELD));
                        } else if (!metadataComplete) {
                            if ((resourceAnnotation = field.getAnnotation(Resource.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(fieldName, null, resourceAnnotation.name(),
                                        AnnotationCacheEntryType.FIELD));
                            } else if (EJB_PRESENT && (ejbAnnotation = field.getAnnotation(EJB.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(fieldName, null, ((EJB) ejbAnnotation).name(),
                                        AnnotationCacheEntryType.FIELD));
                            } else if (WS_PRESENT &&
                                    (webServiceRefAnnotation = field.getAnnotation(WebServiceRef.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(fieldName, null,
                                        ((WebServiceRef) webServiceRefAnnotation).name(),
                                        AnnotationCacheEntryType.FIELD));
                            } else if (JPA_PRESENT && (persistenceContextAnnotation =
                                    field.getAnnotation(PersistenceContext.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(fieldName, null,
                                        ((PersistenceContext) persistenceContextAnnotation).name(),
                                        AnnotationCacheEntryType.FIELD));
                            } else if (JPA_PRESENT &&
                                    (persistenceUnitAnnotation = field.getAnnotation(PersistenceUnit.class)) != null) {
                                annotations.add(new AnnotationCacheEntry(fieldName, null,
                                        ((PersistenceUnit) persistenceUnitAnnotation).name(),
                                        AnnotationCacheEntryType.FIELD));
                            }
                        }
                    }
                }

                if (annotations.isEmpty()) {
                    // Use common object to save memory
                    annotationsArray = ANNOTATIONS_EMPTY;
                } else {
                    annotationsArray = annotations.toArray(new AnnotationCacheEntry[0]);
                }
                synchronized (annotationCache) {
                    annotationCache.put(clazz, annotationsArray);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }


    /**
     * Inject resources in specified instance.
     *
     * @param instance   instance to inject into
     * @param injections map of injections for this class from xml deployment descriptor
     *
     * @throws IllegalAccessException                      if injection target is inaccessible
     * @throws javax.naming.NamingException                if value cannot be looked up in jndi
     * @throws java.lang.reflect.InvocationTargetException if injection fails
     */
    protected void processAnnotations(Object instance, Map<String,String> injections)
            throws IllegalAccessException, InvocationTargetException, NamingException {

        if (context == null) {
            // No resource injection
            return;
        }

        Class<?> clazz = instance.getClass();

        while (clazz != null) {
            AnnotationCacheEntry[] annotations = annotationCache.get(clazz);
            for (AnnotationCacheEntry entry : annotations) {
                if (entry.getType() == AnnotationCacheEntryType.SETTER) {
                    lookupMethodResource(context, instance, getMethod(clazz, entry), entry.getName(), clazz);
                } else if (entry.getType() == AnnotationCacheEntryType.FIELD) {
                    lookupFieldResource(context, instance, getField(clazz, entry), entry.getName(), clazz);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }


    /**
     * Makes cache size available to unit tests.
     *
     * @return the cache size
     */
    protected int getAnnotationCacheSize() {
        return annotationCache.size();
    }


    protected Class<?> loadClassMaybePrivileged(final String className, final ClassLoader classLoader)
            throws ClassNotFoundException {
        Class<?> clazz = loadClass(className, classLoader);
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
        if (ContainerServlet.class.isAssignableFrom(clazz)) {
            throw new SecurityException(sm.getString("defaultInstanceManager.restrictedContainerServlet", clazz));
        }
        while (clazz != null) {
            if (restrictedClasses.contains(clazz.getName())) {
                throw new SecurityException(sm.getString("defaultInstanceManager.restrictedClass", clazz));
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
     *
     * @throws IllegalAccessException       if field is inaccessible
     * @throws javax.naming.NamingException if value is not accessible in naming context
     */
    protected static void lookupFieldResource(Context context, Object instance, Field field, String name,
            Class<?> clazz) throws NamingException, IllegalAccessException {

        Object lookedupResource;

        String normalizedName = normalize(name);

        if ((normalizedName != null) && (normalizedName.length() > 0)) {
            lookedupResource = context.lookup(normalizedName);
        } else {
            lookedupResource = context.lookup(clazz.getName() + "/" + field.getName());
        }

        // This will always be a new Field instance
        // Making this instance accessible does not affect other instances
        // If this doesn't work, just let set() fail
        field.trySetAccessible();
        field.set(instance, lookedupResource);
    }

    /**
     * Inject resources in specified method.
     *
     * @param context  jndi context to extract value from
     * @param instance object to inject into
     * @param method   field target for injection
     * @param name     jndi name value is bound under
     * @param clazz    class annotation is defined in
     *
     * @throws IllegalAccessException                      if method is inaccessible
     * @throws javax.naming.NamingException                if value is not accessible in naming context
     * @throws java.lang.reflect.InvocationTargetException if setter call fails
     */
    protected static void lookupMethodResource(Context context, Object instance, Method method, String name,
            Class<?> clazz) throws NamingException, IllegalAccessException, InvocationTargetException {

        if (!Introspection.isValidSetter(method)) {
            throw new IllegalArgumentException(sm.getString("defaultInstanceManager.invalidInjection"));
        }

        Object lookedupResource;

        String normalizedName = normalize(name);

        if ((normalizedName != null) && (normalizedName.length() > 0)) {
            lookedupResource = context.lookup(normalizedName);
        } else {
            lookedupResource = context.lookup(clazz.getName() + "/" + Introspection.getPropertyName(method));
        }

        // This will always be a new Method instance
        // Making this instance accessible does not affect other instances
        // If this doesn't work, just let invoke() fail
        method.trySetAccessible();
        method.invoke(instance, lookedupResource);
    }

    private static void loadProperties(Set<String> classNames, String resourceName, String messageKey, Log log) {
        Properties properties = new Properties();
        ClassLoader cl = DefaultInstanceManager.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(resourceName)) {
            if (is == null) {
                log.error(sm.getString(messageKey, resourceName));
            } else {
                properties.load(is);
            }
        } catch (IOException ioe) {
            log.error(sm.getString(messageKey, resourceName), ioe);
        }
        if (properties.isEmpty()) {
            return;
        }
        for (Map.Entry<Object,Object> e : properties.entrySet()) {
            if ("restricted".equals(e.getValue())) {
                classNames.add(e.getKey().toString());
            } else {
                log.warn(sm.getString("defaultInstanceManager.restrictedWrongValue", resourceName, e.getKey(),
                        e.getValue()));
            }
        }
    }

    private static String normalize(String jndiName) {
        if (jndiName != null && jndiName.startsWith("java:comp/env/")) {
            return jndiName.substring(14);
        }
        return jndiName;
    }

    private static Method getMethod(final Class<?> clazz, final AnnotationCacheEntry entry) {
        Method result = null;
        try {
            result = clazz.getDeclaredMethod(entry.getAccessibleObjectName(), entry.getParamTypes());
        } catch (NoSuchMethodException e) {
            // Should never happen. On that basis don't log it.
        }
        return result;
    }

    private static Field getField(final Class<?> clazz, final AnnotationCacheEntry entry) {
        Field result = null;
        try {
            result = clazz.getDeclaredField(entry.getAccessibleObjectName());
        } catch (NoSuchFieldException e) {
            // Should never happen. On that basis don't log it.
        }
        return result;
    }


    private Method findPostConstruct(Method currentPostConstruct, String postConstructFromXml, Method method) {
        return findLifecycleCallback(currentPostConstruct, postConstructFromXml, method, PostConstruct.class);
    }

    private Method findPreDestroy(Method currentPreDestroy, String preDestroyFromXml, Method method) {
        return findLifecycleCallback(currentPreDestroy, preDestroyFromXml, method, PreDestroy.class);
    }

    private Method findLifecycleCallback(Method currentMethod, String methodNameFromXml, Method method,
            Class<? extends Annotation> annotation) {
        Method result = currentMethod;
        if (methodNameFromXml != null) {
            if (method.getName().equals(methodNameFromXml)) {
                if (!Introspection.isValidLifecycleCallback(method)) {
                    throw new IllegalArgumentException(
                            sm.getString("defaultInstanceManager.invalidAnnotation", annotation.getName()));
                }
                result = method;
            }
        } else if (!metadataComplete) {
            if (method.isAnnotationPresent(annotation)) {
                if (currentMethod != null || !Introspection.isValidLifecycleCallback(method)) {
                    throw new IllegalArgumentException(
                            sm.getString("defaultInstanceManager.invalidAnnotation", annotation.getName()));
                }
                result = method;
            }
        }
        return result;
    }

    private static final class AnnotationCacheEntry {
        private final String accessibleObjectName;
        private final Class<?>[] paramTypes;
        private final String name;
        private final AnnotationCacheEntryType type;

        AnnotationCacheEntry(String accessibleObjectName, Class<?>[] paramTypes, String name,
                AnnotationCacheEntryType type) {
            this.accessibleObjectName = accessibleObjectName;
            this.paramTypes = paramTypes;
            this.name = name;
            this.type = type;
        }

        public String getAccessibleObjectName() {
            return accessibleObjectName;
        }

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }

        public String getName() {
            return name;
        }

        public AnnotationCacheEntryType getType() {
            return type;
        }
    }


    private enum AnnotationCacheEntryType {
        FIELD,
        SETTER,
        POST_CONSTRUCT,
        PRE_DESTROY
    }
}
