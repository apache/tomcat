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
package org.apache.tomcat.jni;

import java.io.File;

/** Library
 *
 * @author Mladen Turk
 */
public final class Library {

    /* Default library names */
    private static final String [] NAMES = {"tcnative-2", "libtcnative-2", "tcnative-1", "libtcnative-1"};
    /*
     * A handle to the unique Library singleton instance.
     */
    private static Library _instance = null;

    private Library() throws Exception {
        boolean loaded = false;
        StringBuilder err = new StringBuilder();
        File binLib = new File(System.getProperty("catalina.home"), "bin");
        for (int i = 0; i < NAMES.length; i++) {
            File library = new File(binLib, System.mapLibraryName(NAMES[i]));
            try {
                System.load(library.getAbsolutePath());
                loaded = true;
            } catch (ThreadDeath | VirtualMachineError t) {
                throw t;
            } catch (Throwable t) {
                if (library.exists()) {
                    // File exists but failed to load
                    throw t;
                }
                if (i > 0) {
                    err.append(", ");
                }
                err.append(t.getMessage());
            }
            if (loaded) {
                break;
            }
        }
        if (!loaded) {
            String path = System.getProperty("java.library.path");
            String [] paths = path.split(File.pathSeparator);
            for (String value : NAMES) {
                try {
                    System.loadLibrary(value);
                    loaded = true;
                } catch (ThreadDeath | VirtualMachineError t) {
                    throw t;
                } catch (Throwable t) {
                    String name = System.mapLibraryName(value);
                    for (String s : paths) {
                        File fd = new File(s, name);
                        if (fd.exists()) {
                            // File exists but failed to load
                            throw t;
                        }
                    }
                    if (err.length() > 0) {
                        err.append(", ");
                    }
                    err.append(t.getMessage());
                }
                if (loaded) {
                    break;
                }
            }
        }
        if (!loaded) {
            StringBuilder names = new StringBuilder();
            for (String name : NAMES) {
                names.append(name);
                names.append(", ");
            }
            throw new LibraryNotFoundError(names.substring(0, names.length() -2), err.toString());
        }
    }

    private Library(String libraryName)
    {
        System.loadLibrary(libraryName);
    }

    /* create global TCN's APR pool
     * This has to be the first call to TCN library.
     */
    private static native boolean initialize();
    /* destroy global TCN's APR pool
     * This has to be the last call to TCN library.
     */
    public static native void terminate();
    /* Internal function for loading APR Features */
    private static native int version(int what);

    /* TCN_MAJOR_VERSION */
    public static int TCN_MAJOR_VERSION  = 0;
    /* TCN_MINOR_VERSION */
    public static int TCN_MINOR_VERSION  = 0;
    /* TCN_PATCH_VERSION */
    public static int TCN_PATCH_VERSION  = 0;
    /* TCN_IS_DEV_VERSION */
    public static int TCN_IS_DEV_VERSION = 0;
    /* APR_MAJOR_VERSION */
    public static int APR_MAJOR_VERSION  = 0;
    /* APR_MINOR_VERSION */
    public static int APR_MINOR_VERSION  = 0;
    /* APR_PATCH_VERSION */
    public static int APR_PATCH_VERSION  = 0;
    /* APR_IS_DEV_VERSION */
    public static int APR_IS_DEV_VERSION = 0;

    /* TCN_VERSION_STRING */
    public static native String versionString();
    /* APR_VERSION_STRING */
    public static native String aprVersionString();

    /**
     * Setup any APR internal data structures.  This MUST be the first function
     * called for any APR library.
     * @param libraryName the name of the library to load
     *
     * @return {@code true} if the native code was initialized successfully
     *         otherwise {@code false}
     *
     * @throws Exception if a problem occurred during initialization
     */
    public static synchronized boolean initialize(String libraryName) throws Exception {
        if (_instance == null) {
            if (libraryName == null) {
                _instance = new Library();
            } else {
                _instance = new Library(libraryName);
            }
            TCN_MAJOR_VERSION  = version(0x01);
            TCN_MINOR_VERSION  = version(0x02);
            TCN_PATCH_VERSION  = version(0x03);
            TCN_IS_DEV_VERSION = version(0x04);
            APR_MAJOR_VERSION  = version(0x11);
            APR_MINOR_VERSION  = version(0x12);
            APR_PATCH_VERSION  = version(0x13);
            APR_IS_DEV_VERSION = version(0x14);

            if (APR_MAJOR_VERSION < 1) {
                throw new UnsatisfiedLinkError("Unsupported APR Version (" +
                                               aprVersionString() + ")");
            }
        }
        return initialize();
    }
}
