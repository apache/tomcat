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
package javax.el;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @since EL 3.0
 */
public class ImportHandler {

    private List<String> packageNames = new ArrayList<>();
    private ConcurrentHashMap<String,String> classNames = new ConcurrentHashMap<>();
    private Map<String,Class<?>> clazzes = new ConcurrentHashMap<>();
    private Map<String,Class<?>> statics = new ConcurrentHashMap<>();


    public ImportHandler() {
        importPackage("java.lang");
    }


    public void importStatic(String name) throws javax.el.ELException {
        int lastPeriod = name.lastIndexOf('.');

        if (lastPeriod < 0) {
            throw new ELException(Util.message(
                    null, "importHandler.invalidStaticName", name));
        }

        String className = name.substring(0, lastPeriod);
        String fieldOrMethodName = name.substring(lastPeriod + 1);

        Class<?> clazz = findClass(className, true);

        if (clazz == null) {
            throw new ELException(Util.message(
                    null, "importHandler.invalidClassNameForStatic",
                    className, name));
        }

        boolean found = false;

        for (Field field : clazz.getFields()) {
            if (field.getName().equals(fieldOrMethodName)) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) &&
                        Modifier.isPublic(modifiers)) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(fieldOrMethodName)) {
                    int modifiers = method.getModifiers();
                    if (Modifier.isStatic(modifiers) &&
                            Modifier.isPublic(modifiers)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (!found) {
            throw new ELException(Util.message(null,
                    "importHandler.staticNotFound", fieldOrMethodName,
                    className, name));
        }

        Class<?> conflict = statics.get(fieldOrMethodName);
        if (conflict != null) {
            throw new ELException(Util.message(null,
                    "importHandler.ambiguousStaticImport", name,
                    conflict.getName() + '.' +  fieldOrMethodName));
        }

        statics.put(fieldOrMethodName, clazz);
    }


    public void importClass(String name) throws javax.el.ELException {
        int lastPeriodIndex = name.lastIndexOf('.');

        if (lastPeriodIndex < 0) {
            throw new ELException(Util.message(
                    null, "importHandler.invalidClassName", name));
        }

        String unqualifiedName = name.substring(lastPeriodIndex + 1);
        String currentName = classNames.putIfAbsent(unqualifiedName, name);

        if (currentName != null && !currentName.equals(name)) {
            // Conflict. Same unqualifiedName, different fully qualified names
            throw new ELException(Util.message(null,
                    "importHandler.ambiguousImport", name, currentName));
        }
    }


    public void importPackage(String name) {
        // Import ambiguity is handled at resolution, not at import
        // Whether the package exists is not checked,
        // a) for sake of performance when used in JSPs (BZ 57142),
        // b) java.lang.Package.getPackage(name) is not reliable (BZ 57574),
        // c) such check is not required by specification.
        packageNames.add(name);
    }


    public java.lang.Class<?> resolveClass(String name) {
        if (name == null || name.contains(".")) {
            return null;
        }

        // Has it been previously resolved?
        Class<?> result = clazzes.get(name);

        if (result != null) {
            if (NotFound.class.equals(result)) {
                return null;
            } else {
                return result;
            }
        }

        // Search the class imports
        String className = classNames.get(name);
        if (className != null) {
            Class<?> clazz = findClass(className, true);
            if (clazz != null) {
                clazzes.put(className, clazz);
                return clazz;
            }
        }

        // Search the package imports - note there may be multiple matches
        // (which correctly triggers an error)
        for (String p : packageNames) {
            className = p + '.' + name;
            Class<?> clazz = findClass(className, false);
            if (clazz != null) {
                if (result != null) {
                    throw new ELException(Util.message(null,
                            "importHandler.ambiguousImport", className,
                            result.getName()));
                }
                result = clazz;
            }
        }
        if (result == null) {
            // Cache NotFound results to save repeated calls to findClass()
            // which is relatively slow
            clazzes.put(name, NotFound.class);
        } else {
            clazzes.put(name, result);
        }

        return result;
    }


    public java.lang.Class<?> resolveStatic(String name) {
        return statics.get(name);
    }


    private Class<?> findClass(String name, boolean throwException) {
        Class<?> clazz;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        String path = name.replace('.', '/') + ".class";
        try {
            /* Given that findClass() has to be called for every imported
             * package and that getResource() is a lot faster then loadClass()
             * for resources that don't exist, the overhead of the getResource()
             * for the case where the class does exist is a lot less than the
             * overhead we save by not calling loadClass().
             */
            if (cl.getResource(path) == null) {
                return null;
            }
        } catch (ClassCircularityError cce) {
            // May happen under a security manager. Ignore it and try loading
            // the class normally.
        }
        try {
            clazz = cl.loadClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }

        // Class must be public, non-abstract and not an interface
        int modifiers = clazz.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers) ||
                Modifier.isInterface(modifiers)) {
            if (throwException) {
                throw new ELException(Util.message(
                        null, "importHandler.invalidClass", name));
            } else {
                return null;
            }
        }

        return clazz;
    }


    /*
     * Marker class used because null values are not permitted in a
     * ConcurrentHashMap.
     */
    private static class NotFound {
    }
}
