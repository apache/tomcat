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
package org.apache.tomcat.util.xreflection;


import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.tomcat.util.IntrospectionUtils;

public final class ObjectReflectionPropertyInspector {

    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage:\n\t"+
                "org.apache.tomcat.util.xreflection.ObjectReflectionPropertyInspector" +
                " <destination directory>"
            );
            System.exit(1);
        }

        File outputDir = new File(args[0]);
        if (!outputDir.exists() || !outputDir.isDirectory()) {
            System.err.println("Invalid output directory: "+ outputDir.getAbsolutePath());
            System.exit(1);
        }


        Set<SetPropertyClass> baseClasses = getKnownClasses()
            .stream()
            .map(ObjectReflectionPropertyInspector::processClass)
            .collect(Collectors.toSet());
        generateCode(
            baseClasses,
            "org.apache.tomcat.util",
            outputDir,
            "XReflectionIntrospectionUtils"
        );
    }

    private static final Set<Class<?>> getKnownClasses() throws ClassNotFoundException {
        return
            Collections.unmodifiableSet(new LinkedHashSet<>(
                    Arrays.asList(
                        Class.forName("org.apache.catalina.authenticator.jaspic.SimpleAuthConfigProvider"),
                        Class.forName("org.apache.catalina.authenticator.jaspic.PersistentProviderRegistrations$Property"),
                        Class.forName("org.apache.catalina.authenticator.jaspic.PersistentProviderRegistrations$Provider"),
                        Class.forName("org.apache.catalina.connector.Connector"),
                        Class.forName("org.apache.catalina.core.AprLifecycleListener"),
                        Class.forName("org.apache.catalina.core.ContainerBase"),
                        Class.forName("org.apache.catalina.core.StandardContext"),
                        Class.forName("org.apache.catalina.core.StandardEngine"),
                        Class.forName("org.apache.catalina.core.StandardHost"),
                        Class.forName("org.apache.catalina.core.StandardServer"),
                        Class.forName("org.apache.catalina.core.StandardService"),
                        Class.forName("org.apache.catalina.filters.AddDefaultCharsetFilter"),
                        Class.forName("org.apache.catalina.filters.RestCsrfPreventionFilter"),
                        Class.forName("org.apache.catalina.loader.ParallelWebappClassLoader"),
                        Class.forName("org.apache.catalina.loader.WebappClassLoaderBase"),
                        Class.forName("org.apache.catalina.realm.UserDatabaseRealm"),
                        Class.forName("org.apache.catalina.valves.AccessLogValve"),
                        Class.forName("org.apache.coyote.AbstractProtocol"),
                        Class.forName("org.apache.coyote.ajp.AbstractAjpProtocol"),
                        Class.forName("org.apache.coyote.ajp.AjpAprProtocol"),
                        Class.forName("org.apache.coyote.ajp.AjpNio2Protocol"),
                        Class.forName("org.apache.coyote.ajp.AjpNioProtocol"),
                        Class.forName("org.apache.coyote.http11.AbstractHttp11JsseProtocol"),
                        Class.forName("org.apache.coyote.http11.AbstractHttp11Protocol"),
                        Class.forName("org.apache.coyote.http11.Http11AprProtocol"),
                        Class.forName("org.apache.coyote.http11.Http11Nio2Protocol"),
                        Class.forName("org.apache.coyote.http11.Http11NioProtocol"),
                        Class.forName("org.apache.tomcat.util.descriptor.web.ContextResource"),
                        Class.forName("org.apache.tomcat.util.descriptor.web.ResourceBase"),
                        Class.forName("org.apache.tomcat.util.modeler.AttributeInfo"),
                        Class.forName("org.apache.tomcat.util.modeler.FeatureInfo"),
                        Class.forName("org.apache.tomcat.util.modeler.ManagedBean"),
                        Class.forName("org.apache.tomcat.util.modeler.OperationInfo"),
                        Class.forName("org.apache.tomcat.util.modeler.ParameterInfo"),
                        Class.forName("org.apache.tomcat.util.net.AbstractEndpoint"),
                        Class.forName("org.apache.tomcat.util.net.AprEndpoint"),
                        Class.forName("org.apache.tomcat.util.net.Nio2Endpoint"),
                        Class.forName("org.apache.tomcat.util.net.NioEndpoint"),
                        Class.forName("org.apache.tomcat.util.net.SocketProperties")
                    )
                )
            );
    }

    //types of properties that IntrospectionUtils.setProperty supports
    private static final Set<Class<?>> ALLOWED_TYPES = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(
            Boolean.TYPE,
            Integer.TYPE,
            Long.TYPE,
            String.class,
            InetAddress.class
        )
    ));
    private static Map<Class<?>, SetPropertyClass> classes = new HashMap<>();

    public static void generateCode(Set<SetPropertyClass> baseClasses, String packageName, File location, String className) throws Exception {
        String packageDirectory = packageName.replace('.','/');
        File sourceFileLocation = new File(location, packageDirectory);
        ReflectionLessCodeGenerator.generateCode(sourceFileLocation, className, packageName, baseClasses);
    }


    private static boolean isAllowedField(Field field) {
        return ALLOWED_TYPES.contains(field.getType()) && !Modifier.isFinal(field.getModifiers());
    }

    private static boolean isAllowedSetMethod(Method method) {
        return method.getName().startsWith("set") &&
            method.getParameterTypes() != null &&
            method.getParameterTypes().length == 1 &&
            ALLOWED_TYPES.contains(method.getParameterTypes()[0]) &&
            !Modifier.isPrivate(method.getModifiers());
    }

    private static boolean isAllowedGetMethod(Method method) {
        return (method.getName().startsWith("get") || method.getName().startsWith("is")) &&
            (method.getParameterTypes() == null ||
            method.getParameterTypes().length == 0) &&
            ALLOWED_TYPES.contains(method.getReturnType()) &&
            !Modifier.isPrivate(method.getModifiers());
    }


    private static SetPropertyClass getOrCreateSetPropertyClass(Class<?> clazz) {
        boolean base = (clazz.getSuperclass() == null || clazz.getSuperclass() == Object.class);
        SetPropertyClass spc = classes.get(clazz);
        if (spc == null) {
            spc = new SetPropertyClass(clazz, base ? null : getOrCreateSetPropertyClass(clazz.getSuperclass()));
            classes.put(clazz, spc);
        }
        return spc;
    }

    static Method findGetter(Class<?> declaringClass, String propertyName) {
        for (String getterName : Arrays.asList("get" + IntrospectionUtils.capitalize(propertyName), "is" + propertyName)) {
            try {
                Method method = declaringClass.getMethod(getterName);
                if (!Modifier.isPrivate(method.getModifiers())) {
                    return method;
                }
            } catch (NoSuchMethodException e) {
            }
        }
        try {
            Method method = declaringClass.getMethod("getProperty", String.class, String.class);
            if (!Modifier.isPrivate(method.getModifiers())) {
                return method;
            }
        } catch (NoSuchMethodException e) {
        }

        return null;
    }

    static Method findSetter(Class<?> declaringClass, String propertyName, Class<?> propertyType) {
        try {
            Method method = declaringClass.getMethod("set" + IntrospectionUtils.capitalize(propertyName), propertyType);
            if (!Modifier.isPrivate(method.getModifiers())) {
                return method;
            }
        } catch (NoSuchMethodException e) {
        }
        try {
            Method method = declaringClass.getMethod("setProperty", String.class, String.class);
            if (!Modifier.isPrivate(method.getModifiers())) {
                return method;
            }
        } catch (NoSuchMethodException e) {
        }
        return null;
    }

    static String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
            Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }


    static SetPropertyClass processClass(Class<?> clazz) {
        SetPropertyClass spc = getOrCreateSetPropertyClass(clazz);
        final Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (isAllowedSetMethod(method)) {
                String propertyName = decapitalize(method.getName().substring(3));
                Class<?> propertyType = method.getParameterTypes()[0];
                Method getter = findGetter(clazz, propertyName);
                Method setter = findSetter(clazz, propertyName, propertyType);
                ReflectionProperty property = new ReflectionProperty(
                    spc.getClazz().getName(),
                    propertyName,
                    propertyType,
                    setter,
                    getter
                );
                spc.addProperty(property);
            } else if (isAllowedGetMethod(method)) {
                boolean startsWithIs = method.getName().startsWith("is");
                String propertyName = decapitalize(method.getName().substring(startsWithIs ? 2 : 3));
                Class<?> propertyType = method.getReturnType();
                Method getter = findGetter(clazz, propertyName);
                Method setter = findSetter(clazz, propertyName, propertyType);
                ReflectionProperty property = new ReflectionProperty(
                    spc.getClazz().getName(),
                    propertyName,
                    propertyType,
                    setter,
                    getter
                );
                spc.addProperty(property);
            }
        }

        final Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (isAllowedField(field)) {
                Method getter = findGetter(
                    field.getDeclaringClass(),
                    IntrospectionUtils.capitalize(field.getName())
                );
                Method setter = findSetter(
                    field.getDeclaringClass(),
                    IntrospectionUtils.capitalize(field.getName()),
                    field.getType()
                );
                ReflectionProperty property = new ReflectionProperty(
                    spc.getClazz().getName(),
                    field.getName(),
                    field.getType(),
                    setter,
                    getter
                );
                spc.addProperty(property);
            }
        }

        if (!spc.isBaseClass()) {
            SetPropertyClass parent = getOrCreateSetPropertyClass(spc.getClazz().getSuperclass());
            parent.addSubClass(spc);
            return processClass(parent.getClazz());
        } else {
            return spc;
        }
    }
}
