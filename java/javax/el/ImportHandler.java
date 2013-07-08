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
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @since EL 3.0
 */
public class ImportHandler {

    private List<String> packages = new ArrayList<>();
    private Map<String,Class<?>> clazzes = new HashMap<>();
    private Map<String,Class<?>> statics = new HashMap<>();


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

        Class<?> clazz = findClass(className, false);

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
        if (!name.contains(".")) {
            throw new ELException(Util.message(
                    null, "importHandler.invalidClassName", name));
        }

        Class<?> clazz = findClass(name, true);

        if (clazz == null) {
            throw new ELException(Util.message(
                    null, "importHandler.classNotFound", name));
        }
    }


    public void importPackage(String name) {
        // Import ambiguity is handled at resolution, not at import
        Package p = Package.getPackage(name);
        if (p == null) {
            // Either the package does not exist or no class has been loaded
            // from that package. Check if the package exists.
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            String path = name.replace('.', '/');
            URL url = cl.getResource(path);
            if (url == null) {
                throw new ELException(Util.message(
                        null, "importHandler.invalidPackage", name));
            }
        }
        packages.add(name);
    }


    public java.lang.Class<?> resolveClass(String name) {
        Class<?> result = clazzes.get(name);

        if (result == null) {
            // Search the package imports - note there may be multiple matches
            // (which correctly triggers an error)
            for (String p : packages) {
                String className = p + '.' + name;
                result = findClass(className, true);
            }
        }

        return result;
    }


    public java.lang.Class<?> resolveStatic(String name) {
        return statics.get(name);
    }


    private Class<?> findClass(String name, boolean cache) {
        Class<?> clazz;
        try {
             clazz = Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }

        // Class must be public, non-abstract and not an interface
        int modifiers = clazz.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers) ||
                Modifier.isInterface(modifiers)) {
            throw new ELException(Util.message(
                    null, "importHandler.invalidClass", name));
        }

        if (cache) {
            String simpleName = clazz.getSimpleName();
            Class<?> conflict = clazzes.get(simpleName);

            if (conflict != null) {
                throw new ELException(Util.message(null,
                        "importHandler.ambiguousImport", name, conflict.getName()));
            }

            clazzes.put(simpleName, clazz);
        }

        return clazz;
    }
}
