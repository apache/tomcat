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
package org.apache.catalina.util;


import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.tomcat.util.ExceptionUtils;


/**
 * Simple utility module to make it easy to plug in the server identifier when integrating Tomcat.
 */
public class ServerInfo {


    // ------------------------------------------------------- Static Variables


    /**
     * The server information String with which we identify ourselves.
     */
    private static final String serverInfo;

    /**
     * The server built String.
     */
    private static final String serverBuilt;

    /**
     * The server built String, in ISO-8604 date format.
     */
    private static final String serverBuiltIso;

    /**
     * The server's version number String.
     */
    private static final String serverNumber;

    static {

        String info = null;
        String built = null;
        String builtIso = null;
        String number = null;

        Properties props = new Properties();
        try (InputStream is = ServerInfo.class.getResourceAsStream("/org/apache/catalina/util/ServerInfo.properties")) {
            props.load(is);
            info = props.getProperty("server.info");
            built = props.getProperty("server.built");
            builtIso = props.getProperty("server.built.iso");
            number = props.getProperty("server.number");
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }
        if (info == null || info.equals("Apache Tomcat/@VERSION@")) {
            info = "Apache Tomcat/12.0.x-dev";
        }
        if (built == null || built.equals("@VERSION_BUILT@")) {
            built = "unknown";
        }
        if (builtIso == null || builtIso.equals("@VERSION_BUILT_ISO@")) {
            builtIso = "unknown";
        }
        if (number == null || number.equals("@VERSION_NUMBER@")) {
            number = "12.0.x";
        }

        serverInfo = info;
        serverBuilt = built;
        serverBuiltIso = builtIso;
        serverNumber = number;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * @return the server identification for this version of Tomcat.
     */
    public static String getServerInfo() {
        return serverInfo;
    }

    /**
     * @return the server built time for this version of Tomcat.
     */
    public static String getServerBuilt() {
        return serverBuilt;
    }

    /**
     * @return the server built date for this version of Tomcat in ISO-8601 date format.
     */
    public static String getServerBuiltISO() {
        return serverBuiltIso;
    }

    /**
     * @return the server's version number.
     */
    public static String getServerNumber() {
        return serverNumber;
    }

    public static void main(String[] args) {
        // Suppress INFO logging from library initialization
        java.util.logging.Logger.getLogger("org.apache.tomcat.util.net.openssl.panama").setLevel(java.util.logging.Level.WARNING);
        java.util.logging.Logger.getLogger("org.apache.catalina.core").setLevel(java.util.logging.Level.WARNING);

        System.out.println("Server version: " + getServerInfo());
        System.out.println("Server built:   " + getServerBuilt());
        System.out.println("Server number:  " + getServerNumber());
        System.out.println("OS Name:        " + System.getProperty("os.name"));
        System.out.println("OS Version:     " + System.getProperty("os.version"));
        System.out.println("Architecture:   " + System.getProperty("os.arch"));
        System.out.println("JVM Version:    " + System.getProperty("java.runtime.version"));
        System.out.println("JVM Vendor:     " + System.getProperty("java.vm.vendor"));

        // Get CATALINA_HOME for library scanning (already displayed in catalina script output preface)
        String catalinaHome = System.getProperty("catalina.home");

        // Display APR/Tomcat Native information if available
        boolean aprLoaded = false;
        try {
            // Try to initialize APR by creating an instance and calling isAprAvailable()
            // Creating an instance sets the instance flag which allows initialization
            Class<?> aprLifecycleListenerClass = Class.forName("org.apache.catalina.core.AprLifecycleListener");
            aprLifecycleListenerClass.getConstructor().newInstance();
            Boolean aprAvailable = (Boolean) aprLifecycleListenerClass.getMethod("isAprAvailable").invoke(null);
            if (aprAvailable != null && aprAvailable.booleanValue()) {
                // APR is available, get version information using public methods
                String tcnVersion = (String) aprLifecycleListenerClass.getMethod("getInstalledTcnVersion").invoke(null);
                String aprVersion = (String) aprLifecycleListenerClass.getMethod("getInstalledAprVersion").invoke(null);

                System.out.println("APR loaded:     true");
                System.out.println("APR Version:    " + aprVersion);
                System.out.println("Tomcat Native:  " + tcnVersion);
                aprLoaded = true;

                // Check if installed version is older than recommended
                try {
                    String warning = (String) aprLifecycleListenerClass.getMethod("getTcnVersionWarning").invoke(null);

                    if (warning != null) {
                        System.out.println("                " + warning);
                    }
                } catch (Exception e) {
                    // Failed to check version - ignore
                }

                // Display OpenSSL version if available
                try {
                    String openSSLVersion = (String) aprLifecycleListenerClass.getMethod("getInstalledOpenSslVersion").invoke(null);

                    if (openSSLVersion != null && !openSSLVersion.isEmpty()) {
                        System.out.println("OpenSSL (APR):  " + openSSLVersion);
                    }
                } catch (Exception e) {
                    // SSL not initialized or not available
                }
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // APR/Tomcat Native classes not available on classpath
        } catch (Exception e) {
            // Error checking APR status
        }

        if (!aprLoaded) {
            System.out.println("APR loaded:     false");
        }

        // Display FFM OpenSSL information if available
        try {
            // Try to initialize FFM OpenSSL by creating an instance and calling isAvailable()
            // Creating an instance sets the instance flag which allows initialization
            Class<?> openSSLLifecycleListenerClass = Class.forName("org.apache.catalina.core.OpenSSLLifecycleListener");
            openSSLLifecycleListenerClass.getConstructor().newInstance();
            Boolean ffmAvailable = (Boolean) openSSLLifecycleListenerClass.getMethod("isAvailable").invoke(null);

            if (ffmAvailable != null && ffmAvailable.booleanValue()) {
                // FFM OpenSSL is available, get version information using public method
                String versionString = (String) openSSLLifecycleListenerClass.getMethod("getInstalledOpenSslVersion").invoke(null);

                if (versionString != null && !versionString.isEmpty()) {
                    System.out.println("OpenSSL (FFM):  " + versionString);
                }
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // FFM OpenSSL classes not available on classpath
        } catch (Exception e) {
            // Error checking FFM OpenSSL status
        }

        // Display third-party libraries in CATALINA_HOME/lib
        if (catalinaHome != null) {
            File libDir = new File(catalinaHome, "lib");
            if (libDir.exists() && libDir.isDirectory()) {
                File[] allJars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));

                if (allJars != null && allJars.length > 0) {
                    // First pass: collect third-party JARs and find longest name
                    List<File> thirdPartyJars = new ArrayList<>();
                    int maxNameLength = 0;
                    for (File jar : allJars) {
                        if (!isTomcatCoreJar(jar)) {
                            thirdPartyJars.add(jar);
                            maxNameLength = Math.max(maxNameLength, jar.getName().length());
                        }
                    }

                    // Second pass: print with aligned formatting
                    if (!thirdPartyJars.isEmpty()) {
                        System.out.println();
                        System.out.println("Third-party libraries:");
                        for (File jar : thirdPartyJars) {
                            String version = getJarVersion(jar);
                            String jarName = jar.getName();
                            // Colon right after name, then pad to align version numbers
                            String nameWithColon = jarName + ":";
                            String paddedName = String.format("%-" + (maxNameLength + 1) + "s", nameWithColon);
                            if (version != null) {
                                System.out.println("  " + paddedName + " " + version);
                            } else {
                                System.out.println("  " + paddedName + " (unknown)");
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isTomcatCoreJar(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();

            if (manifest != null) {
                // Check Bundle-SymbolicName to identify Tomcat core JARs
                String bundleName = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
                if (bundleName != null) {
                    // Tomcat core JARs have Bundle-SymbolicName starting with org.apache.tomcat,
                    // org.apache.catalina, or jakarta.
                    if (bundleName.startsWith("org.apache.tomcat") ||
                            bundleName.startsWith("org.apache.catalina") ||
                            bundleName.startsWith("jakarta.")) {
                        return true;
                    }
                }

                // Fallback: Check Implementation-Vendor and Implementation-Title
                String implVendor = manifest.getMainAttributes().getValue("Implementation-Vendor");
                String implTitle = manifest.getMainAttributes().getValue("Implementation-Title");

                if ("Apache Software Foundation".equals(implVendor) && "Apache Tomcat".equals(implTitle)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore errors reading JAR manifest
        }

        return false;
    }

    private static String getJarVersion(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();

            if (manifest != null) {
                // Try different common version attributes
                String[] versionAttrs = {"Bundle-Version", "Implementation-Version", "Specification-Version"};
                for (String attr : versionAttrs) {
                    String version = manifest.getMainAttributes().getValue(attr);
                    if (version != null) {
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors reading JAR manifest
        }

        return null;
    }

}
