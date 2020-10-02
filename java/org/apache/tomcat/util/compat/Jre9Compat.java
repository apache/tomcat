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
package org.apache.tomcat.util.compat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Deque;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

class Jre9Compat extends Jre8Compat {

    private static final Log log = LogFactory.getLog(Jre9Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre9Compat.class);

    private static final Class<?> inaccessibleObjectExceptionClazz;
    private static final Method setDefaultUseCachesMethod;
    private static final Method bootMethod;
    private static final Method configurationMethod;
    private static final Method modulesMethod;
    private static final Method referenceMethod;
    private static final Method locationMethod;
    private static final Method isPresentMethod;
    private static final Method getMethod;
    private static final Constructor<JarFile> jarFileConstructor;
    private static final Method isMultiReleaseMethod;
    private static final Object RUNTIME_VERSION;
    private static final int RUNTIME_MAJOR_VERSION;
    private static final Method canAccessMethod;
    private static final Method getModuleMethod;
    private static final Method isExportedMethod;
    private static final Method getNameMethod;

    static {
        Class<?> c1 = null;
        Method m4 = null;
        Method m5 = null;
        Method m6 = null;
        Method m7 = null;
        Method m8 = null;
        Method m9 = null;
        Method m10 = null;
        Method m11 = null;
        Constructor<JarFile> c12 = null;
        Method m13 = null;
        Object o14 = null;
        Object o15 = null;
        Method m16 = null;
        Method m17 = null;
        Method m18 = null;
        Method m19 = null;

        try {
            // Order is important for the error handling below.
            // Must look up c1 first.
            c1 = Class.forName("java.lang.reflect.InaccessibleObjectException");

            Class<?> moduleLayerClazz = Class.forName("java.lang.ModuleLayer");
            Class<?> configurationClazz = Class.forName("java.lang.module.Configuration");
            Class<?> resolvedModuleClazz = Class.forName("java.lang.module.ResolvedModule");
            Class<?> moduleReferenceClazz = Class.forName("java.lang.module.ModuleReference");
            Class<?> optionalClazz = Class.forName("java.util.Optional");
            Class<?> versionClazz = Class.forName("java.lang.Runtime$Version");
            Method runtimeVersionMethod = JarFile.class.getMethod("runtimeVersion");
            Method majorMethod = versionClazz.getMethod("major");

            m4 = URLConnection.class.getMethod("setDefaultUseCaches", String.class, boolean.class);
            m5 = moduleLayerClazz.getMethod("boot");
            m6 = moduleLayerClazz.getMethod("configuration");
            m7 = configurationClazz.getMethod("modules");
            m8 = resolvedModuleClazz.getMethod("reference");
            m9 = moduleReferenceClazz.getMethod("location");
            m10 = optionalClazz.getMethod("isPresent");
            m11 = optionalClazz.getMethod("get");
            c12 = JarFile.class.getConstructor(File.class, boolean.class, int.class, versionClazz);
            m13 = JarFile.class.getMethod("isMultiRelease");
            o14 = runtimeVersionMethod.invoke(null);
            o15 = majorMethod.invoke(o14);
            m16 = AccessibleObject.class.getMethod("canAccess", new Class<?>[] { Object.class });
            m17 = Class.class.getMethod("getModule");
            Class<?> moduleClass = Class.forName("java.lang.Module");
            m18 = moduleClass.getMethod("isExported", String.class);
            m19 = moduleClass.getMethod("getName");

        } catch (ClassNotFoundException e) {
            if (c1 == null) {
                // Must be pre-Java 9
                log.debug(sm.getString("jre9Compat.javaPre9"), e);
            } else {
                // Should never happen - signature error in lookup?
                log.error(sm.getString("jre9Compat.unexpected"), e);
            }
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Should never happen
            log.error(sm.getString("jre9Compat.unexpected"), e);
        }

        inaccessibleObjectExceptionClazz = c1;
        setDefaultUseCachesMethod = m4;
        bootMethod = m5;
        configurationMethod = m6;
        modulesMethod = m7;
        referenceMethod = m8;
        locationMethod = m9;
        isPresentMethod = m10;
        getMethod = m11;
        jarFileConstructor = c12;
        isMultiReleaseMethod = m13;

        RUNTIME_VERSION = o14;
        if (o15 != null) {
            RUNTIME_MAJOR_VERSION = ((Integer) o15).intValue();
        } else {
            // Must be Java 8
            RUNTIME_MAJOR_VERSION = 8;
        }

        canAccessMethod = m16;
        getModuleMethod = m17;
        isExportedMethod = m18;
        getNameMethod = m19;
    }


    static boolean isSupported() {
        return inaccessibleObjectExceptionClazz != null;
    }


    @Override
    public boolean isInstanceOfInaccessibleObjectException(Throwable t) {
        if (t == null) {
            return false;
        }

        return inaccessibleObjectExceptionClazz.isAssignableFrom(t.getClass());
    }


    @Override
    public void disableCachingForJarUrlConnections() throws IOException {
        try {
            setDefaultUseCachesMethod.invoke(null, "JAR", Boolean.FALSE);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public void addBootModulePath(Deque<URL> classPathUrlsToProcess) {
        try {
            Object bootLayer = bootMethod.invoke(null);
            Object bootConfiguration = configurationMethod.invoke(bootLayer);
            Set<?> resolvedModules = (Set<?>) modulesMethod.invoke(bootConfiguration);
            for (Object resolvedModule : resolvedModules) {
                Object moduleReference = referenceMethod.invoke(resolvedModule);
                Object optionalURI = locationMethod.invoke(moduleReference);
                Boolean isPresent = (Boolean) isPresentMethod.invoke(optionalURI);
                if (isPresent.booleanValue()) {
                    URI uri = (URI) getMethod.invoke(optionalURI);
                    try {
                        URL url = uri.toURL();
                        classPathUrlsToProcess.add(url);
                    } catch (MalformedURLException e) {
                        log.warn(sm.getString("jre9Compat.invalidModuleUri", uri), e);
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public JarFile jarFileNewInstance(File f) throws IOException {
        try {
            return jarFileConstructor.newInstance(
                    f, Boolean.TRUE, Integer.valueOf(ZipFile.OPEN_READ), RUNTIME_VERSION);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new IOException(e);
        }
    }


    @Override
    public boolean jarFileIsMultiRelease(JarFile jarFile) {
        try {
            return ((Boolean) isMultiReleaseMethod.invoke(jarFile)).booleanValue();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
    }


    @Override
    public int jarFileRuntimeMajorVersion() {
        return RUNTIME_MAJOR_VERSION;
    }


    @Override
    public boolean canAccess(Object base, AccessibleObject accessibleObject) {
        try {
            return ((Boolean) canAccessMethod.invoke(accessibleObject, base)).booleanValue();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
    }


    @Override
    public boolean isExported(Class<?> type) {
        try {
            String packageName = type.getPackage().getName();
            Object module = getModuleMethod.invoke(type);
            return ((Boolean) isExportedMethod.invoke(module, packageName)).booleanValue();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }


    @Override
    public String getModuleName(Class<?> type) {
        try {
            Object module = getModuleMethod.invoke(type);
            return (String) getNameMethod.invoke(module);
        } catch (ReflectiveOperationException e) {
            return "ERROR";
        }
    }
}
