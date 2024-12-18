/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.buf;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.tomcat.util.res.StringManager;

/**
 * Utility class used to provide String representations of objects. It is typically used in debug logging.
 */
public class ToStringUtil {

    private static final StringManager sm = StringManager.getManager(ToStringUtil.class);

    private static final String INDENT = "    ";


    private ToStringUtil() {
        // Utility class. Hide default constructor.
    }


    /**
     * Generate a String representation of the class path for the given class loader and any parent class loaders to aid
     * debugging of {@link ClassNotFoundException}.
     *
     * @param classLoader The class loader to analyse
     *
     * @return A String representation of the class path. The format is undefined and may change in future point
     *             releases. The output includes new lines.
     */
    public static String classPathForCNFE(ClassLoader classLoader) {
        // The result is expected to be fairly large
        StringBuilder result = new StringBuilder(4096);
        result.append(sm.getString("toStringUtil.classpath.header"));
        result.append("\n");
        while (classLoader != null) {
            classPathForCNFE(classLoader, result);
            classLoader = classLoader.getParent();
        }
        return result.toString();
    }


    private static void classPathForCNFE(ClassLoader classLoader, StringBuilder result) {
        result.append(INDENT);
        result.append(sm.getString("toStringUtil.classpath.classloader", classLoader));
        result.append("\n");
        if (classLoader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) classLoader).getURLs();
            for (URL url : urls) {
                result.append(INDENT);
                result.append(INDENT);
                result.append(url);
                result.append("\n");
            }
        } else if (classLoader == ClassLoader.getSystemClassLoader()) {
            // From Java 9 the internal class loaders no longer extend
            // URLCLassLoader
            String cp = System.getProperty("java.class.path");
            if (cp != null && cp.length() > 0) {
                String[] paths = cp.split(File.pathSeparator);
                for (String path : paths) {
                    result.append(INDENT);
                    result.append(INDENT);
                    result.append(path);
                    result.append("\n");
                }
            }
        } else if (classLoader == ClassLoader.getPlatformClassLoader()) {
            // From Java 9 the internal class loaders no longer extend
            // URLCLassLoader
            result.append(INDENT);
            result.append(INDENT);
            result.append(sm.getString("toStringUtil.classpath.platform"));
            result.append("\n");
        } else {
            result.append(INDENT);
            result.append(INDENT);
            result.append(sm.getString("toStringUtil.classpath.unknown"));
            result.append("\n");
        }
    }
}
