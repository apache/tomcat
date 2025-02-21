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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

public class Jre12Compat extends Jre9Compat {

    private static final Log log = LogFactory.getLog(Jre12Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre12Compat.class);

    private static final boolean supported;

    static {
        // Don't need any Java 12 specific classes (yet) so just test for one of
        // the new ones for now.
        Class<?> c1 = null;
        try {
            c1 = Class.forName("java.text.CompactNumberFormat");
        } catch (ReflectiveOperationException e) {
            // Must be pre-Java 12
            log.debug(sm.getString("jre12Compat.javaPre12"), e);
        }

        supported = (c1 != null);
    }

    static boolean isSupported() {
        return supported;
    }


    /*
     * The behaviour of the canonical file name cache varies by Java version.
     *
     * The cache was removed in Java 21 so these methods and the associated code can be removed once the minimum Java
     * version is 21.
     *
     * For 12 <= Java <= 20, the cache was present but disabled by default.
     *
     * For Java < 12, the cache was enabled by default. Tomcat assumes the cache is enabled unless proven otherwise.
     *
     * Tomcat 10.1 has a minimum Java version of 11.
     *
     * The static field in java.io.FileSystem will be set before any application code gets a chance to run. Therefore,
     * the value of that field can be determined by looking at the command line arguments. This enables us to determine
     * the status without having using reflection.
     *
     * This is Java 12 and later.
     */
    @Override
    public boolean isCanonCachesDisabled() {
        if (canonCachesDisabled != null) {
            return canonCachesDisabled.booleanValue();
        }
        synchronized (canonCachesDisabledLock) {
            if (canonCachesDisabled != null) {
                return canonCachesDisabled.booleanValue();
            }

            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                // If any command line argument attempts to enable the cache, assume it is enabled.
                if (arg.startsWith(USE_CANON_CACHES_CMD_ARG)) {
                    String value = arg.substring(USE_CANON_CACHES_CMD_ARG.length());
                    boolean cacheEnabled = Boolean.valueOf(value).booleanValue();
                    if (cacheEnabled) {
                        canonCachesDisabled = Boolean.FALSE;
                        return false;
                    }
                }
            }
            canonCachesDisabled = Boolean.TRUE;
            return true;
        }
    }


    /*
     * Java 12 increased security around reflection so additional code is required to disable the cache since a final
     * field needs to be changed.
     */
    @Override
    protected void ensureUseCanonCachesFieldIsPopulated() {
        if (useCanonCachesField != null) {
            return;
        }
        synchronized (useCanonCachesFieldLock) {
            if (useCanonCachesField != null) {
                return;
            }

            Field f = null;
            try {
                Class<?> clazz = Class.forName("java.io.FileSystem");
                f = clazz.getDeclaredField("useCanonCaches");
                // Need this because the 'useCanonCaches' field is private
                f.setAccessible(true);

                /*
                 * Need this in Java 12 to 17 (and it only works up to Java 17) because the 'useCanonCaches' field is
                 * final.
                 *
                 * This will fail in Java 18 to 20 but since those versions are no longer supported it is acceptable for
                 * the attempt to set the 'useCanonCaches' field to fail. Users that really want to use Java 18 to 20
                 * will have to ensure that they do not explicitly enable the canonical file name cache.
                 */
                Method privateLookupInMethod =
                        MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, Lookup.class);
                Method findVarHandleMethod =
                        Lookup.class.getDeclaredMethod("findVarHandle", Class.class, String.class, Class.class);
                clazz = Class.forName("java.lang.invoke.VarHandle");

                Lookup lookup = (Lookup) privateLookupInMethod.invoke(null, Field.class, MethodHandles.lookup());
                Object modifiers = findVarHandleMethod.invoke(lookup, Field.class, "modifiers", int.class);
                Method setMethod = null;
                try {
                    setMethod = modifiers.getClass().getDeclaredMethod("set", modifiers.getClass(), Object.class,
                            int.class);
                } catch (NoSuchMethodException e) {
                    /*
                     * Method signature changed between Java 14 and Java 15. This hack avoids creating Jre15Compat for
                     * this one line.
                     */
                    setMethod = modifiers.getClass().getDeclaredMethod("set", clazz, Object.class, int.class);
                }
                setMethod.setAccessible(true);
                setMethod.invoke(null, modifiers, f, Integer.valueOf(f.getModifiers() & ~Modifier.FINAL));
            } catch (NoSuchMethodException e) {
                // Make sure field is not set.
                f = null;
                log.warn(sm.getString("jreCompat.useCanonCaches.java18"), e);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // Make sure field is not set.
                f = null;
                log.warn(sm.getString("jreCompat.useCanonCaches.init"), t);
            }

            if (f == null) {
                useCanonCachesField = Optional.empty();
            } else {
                useCanonCachesField = Optional.of(f);
            }
        }
    }
}
