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


package org.apache.catalina.startup;


import java.io.File;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.catalina.loader.StandardClassLoader;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * <p>Utility class for building class loaders for Catalina.  The factory
 * method requires the following parameters in order to build a new class
 * loader (with suitable defaults in all cases):</p>
 * <ul>
 * <li>A set of directories containing unpacked classes (and resources)
 *     that should be included in the class loader's
 *     repositories.</li>
 * <li>A set of directories containing classes and resources in JAR files.
 *     Each readable JAR file discovered in these directories will be
 *     added to the class loader's repositories.</li>
 * <li><code>ClassLoader</code> instance that should become the parent of
 *     the new class loader.</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @version $Id$
 */

public final class ClassLoaderFactory {


    private static final Log log = LogFactory.getLog(ClassLoaderFactory.class);

    protected static final Integer IS_DIR = Integer.valueOf(0);
    protected static final Integer IS_JAR = Integer.valueOf(1);
    protected static final Integer IS_GLOB = Integer.valueOf(2);
    protected static final Integer IS_URL = Integer.valueOf(3);

    // --------------------------------------------------------- Public Methods


    /**
     * Create and return a new class loader, based on the configuration
     * defaults and the specified directory paths:
     *
     * @param unpacked Array of pathnames to unpacked directories that should
     *  be added to the repositories of the class loader, or <code>null</code> 
     * for no unpacked directories to be considered
     * @param packed Array of pathnames to directories containing JAR files
     *  that should be added to the repositories of the class loader, 
     * or <code>null</code> for no directories of JAR files to be considered
     * @param parent Parent class loader for the new class loader, or
     *  <code>null</code> for the system class loader.
     *
     * @exception Exception if an error occurs constructing the class loader
     */
    public static ClassLoader createClassLoader(File unpacked[],
                                                File packed[],
                                                final ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())
            log.debug("Creating new class loader");

        // Construct the "class path" for this class loader
        Set<URL> set = new LinkedHashSet<URL>();

        // Add unpacked directories
        if (unpacked != null) {
            for (int i = 0; i < unpacked.length; i++)  {
                File file = unpacked[i];
                if (!file.exists() || !file.canRead())
                    continue;
                file = new File(file.getCanonicalPath() + File.separator);
                URL url = file.toURI().toURL();
                if (log.isDebugEnabled())
                    log.debug("  Including directory " + url);
                set.add(url);
            }
        }

        // Add packed directory JAR files
        if (packed != null) {
            for (int i = 0; i < packed.length; i++) {
                File directory = packed[i];
                if (!directory.isDirectory() || !directory.exists() ||
                    !directory.canRead())
                    continue;
                String filenames[] = directory.list();
                for (int j = 0; j < filenames.length; j++) {
                    String filename = filenames[j].toLowerCase(Locale.ENGLISH);
                    if (!filename.endsWith(".jar"))
                        continue;
                    File file = new File(directory, filenames[j]);
                    if (log.isDebugEnabled())
                        log.debug("  Including jar file " + file.getAbsolutePath());
                    URL url = file.toURI().toURL();
                    set.add(url);
                }
            }
        }

        // Construct the class loader itself
        final URL[] array = set.toArray(new URL[set.size()]);
        return AccessController.doPrivileged(
                new PrivilegedAction<StandardClassLoader>() {
                    @Override
                    public StandardClassLoader run() {
                        if (parent == null)
                            return new StandardClassLoader(array);
                        else
                            return new StandardClassLoader(array, parent);
                    }
                });
    }


    /**
     * Create and return a new class loader, based on the configuration
     * defaults and the specified directory paths:
     *
     * @param locations Array of strings containing class directories, jar files,
     *  jar directories or URLS that should be added to the repositories of
     *  the class loader. The type is given by the member of param types.
     * @param types Array of types for the members of param locations.
     *  Possible values are IS_DIR (class directory), IS_JAR (single jar file),
     *  IS_GLOB (directory of jar files) and IS_URL (URL).
     * @param parent Parent class loader for the new class loader, or
     *  <code>null</code> for the system class loader.
     *
     * @exception Exception if an error occurs constructing the class loader
     */
    public static ClassLoader createClassLoader(String locations[],
                                                Integer types[],
                                                final ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())
            log.debug("Creating new class loader");

        // Construct the "class path" for this class loader
        Set<URL> set = new LinkedHashSet<URL>();

        if (locations != null && types != null && locations.length == types.length) {
            for (int i = 0; i < locations.length; i++)  {
                String location = locations[i];
                if ( types[i] == IS_URL ) {
                    URL url = new URL(location);
                    if (log.isDebugEnabled())
                        log.debug("  Including URL " + url);
                    set.add(url);
                } else if ( types[i] == IS_DIR ) {
                    File directory = new File(location);
                    directory = new File(directory.getCanonicalPath());
                    if (!directory.exists() || !directory.isDirectory() ||
                        !directory.canRead())
                         continue;
                    URL url = directory.toURI().toURL();
                    if (log.isDebugEnabled())
                        log.debug("  Including directory " + url);
                    set.add(url);
                } else if ( types[i] == IS_JAR ) {
                    File file=new File(location);
                    file = new File(file.getCanonicalPath());
                    if (!file.exists() || !file.canRead())
                        continue;
                    URL url = file.toURI().toURL();
                    if (log.isDebugEnabled())
                        log.debug("  Including jar file " + url);
                    set.add(url);
                } else if ( types[i] == IS_GLOB ) {
                    File directory=new File(location);
                    if (!directory.exists() || !directory.isDirectory() ||
                        !directory.canRead())
                        continue;
                    if (log.isDebugEnabled())
                        log.debug("  Including directory glob "
                            + directory.getAbsolutePath());
                    String filenames[] = directory.list();
                    for (int j = 0; j < filenames.length; j++) {
                        String filename = filenames[j].toLowerCase(Locale.ENGLISH);
                        if (!filename.endsWith(".jar"))
                            continue;
                        File file = new File(directory, filenames[j]);
                        file = new File(file.getCanonicalPath());
                        if (!file.exists() || !file.canRead())
                            continue;
                        if (log.isDebugEnabled())
                            log.debug("    Including glob jar file "
                                + file.getAbsolutePath());
                        URL url = file.toURI().toURL();
                        set.add(url);
                    }
                }
            }
        }

        // Construct the class loader itself
        final URL[] array = set.toArray(new URL[set.size()]);
        if (log.isDebugEnabled())
            for (int i = 0; i < array.length; i++) {
                log.debug("  location " + i + " is " + array[i]);
            }

        return AccessController.doPrivileged(
                new PrivilegedAction<StandardClassLoader>() {
                    @Override
                    public StandardClassLoader run() {
                        if (parent == null)
                            return new StandardClassLoader(array);
                        else
                            return new StandardClassLoader(array, parent);
                    }
                });
    }


}
