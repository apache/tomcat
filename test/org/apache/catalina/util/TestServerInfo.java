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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.OpenSSLLifecycleListener;
import org.apache.tomcat.util.compat.JreCompat;

public class TestServerInfo {

    /**
     * Test that prints the server version info.
     */
    @Test
    public void testServerInfo() {
        ServerInfo.main(new String[0]);
    }

    /**
     * Test that ServerInfo.main() outputs expected basic information.
     */
    @Test
    public void testServerInfoOutput() throws Exception {
        String output = captureServerInfoOutput();

        // Check for expected output lines
        Assert.assertTrue("Should contain server version", output.contains("Server version:"));
        Assert.assertTrue("Should contain server built", output.contains("Server built:"));
        Assert.assertTrue("Should contain server number", output.contains("Server number:"));
        Assert.assertTrue("Should contain OS Name", output.contains("OS Name:"));
        Assert.assertTrue("Should contain JVM Version", output.contains("JVM Version:"));
        Assert.assertTrue("Should contain APR loaded status", output.contains("APR loaded:"));
    }

    /**
     * Test isTomcatCoreJar() with Tomcat core JAR (Bundle-SymbolicName pattern).
     */
    @Test
    public void testIsTomcatCoreJarWithBundleSymbolicName() throws Exception {
        withTestJar("test-tomcat-core.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Bundle-SymbolicName", "org.apache.tomcat-test");
        }, jar -> Assert.assertTrue("Should identify org.apache.tomcat-* as core JAR",
                invokeIsTomcatCoreJar(jar)));
    }

    /**
     * Test isTomcatCoreJar() with Catalina core JAR (Bundle-SymbolicName pattern).
     */
    @Test
    public void testIsTomcatCoreJarWithCatalinaSymbolicName() throws Exception {
        withTestJar("test-catalina-core.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Bundle-SymbolicName", "org.apache.catalina-ha");
        }, jar -> Assert.assertTrue("Should identify org.apache.catalina-* as core JAR",
                invokeIsTomcatCoreJar(jar)));
    }

    /**
     * Test isTomcatCoreJar() with Jakarta API JAR (Bundle-SymbolicName pattern).
     */
    @Test
    public void testIsTomcatCoreJarWithJakartaSymbolicName() throws Exception {
        withTestJar("test-jakarta-api.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Bundle-SymbolicName", "jakarta.servlet.api");
        }, jar -> Assert.assertTrue("Should identify jakarta.* as core JAR",
                invokeIsTomcatCoreJar(jar)));
    }

    /**
     * Test isTomcatCoreJar() with Tomcat core JAR (Implementation-Vendor fallback).
     */
    @Test
    public void testIsTomcatCoreJarWithImplementationVendor() throws Exception {
        withTestJar("test-tomcat-i18n.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VENDOR, "Apache Software Foundation");
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, "Apache Tomcat");
        }, jar -> Assert.assertTrue("Should identify ASF/Tomcat as core JAR",
                invokeIsTomcatCoreJar(jar)));
    }

    /**
     * Test isTomcatCoreJar() with third-party JAR.
     */
    @Test
    public void testIsTomcatCoreJarWithThirdParty() throws Exception {
        withTestJar("test-third-party.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Bundle-SymbolicName", "com.example.library");
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VENDOR, "Example Corp");
        }, jar -> Assert.assertFalse("Should not identify third-party JAR as core",
                invokeIsTomcatCoreJar(jar)));
    }

    /**
     * Test getJarVersion() with Bundle-Version.
     */
    @Test
    public void testGetJarVersionWithBundleVersion() throws Exception {
        withTestJar("test-bundle-version.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Bundle-Version", "1.2.3");
        }, jar -> Assert.assertEquals("Should read Bundle-Version", "1.2.3",
                invokeGetJarVersion(jar)));
    }

    /**
     * Test getJarVersion() with Implementation-Version.
     */
    @Test
    public void testGetJarVersionWithImplementationVersion() throws Exception {
        withTestJar("test-impl-version.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "2.3.4");
        }, jar -> Assert.assertEquals("Should read Implementation-Version", "2.3.4",
                invokeGetJarVersion(jar)));
    }

    /**
     * Test getJarVersion() with Specification-Version.
     */
    @Test
    public void testGetJarVersionWithSpecificationVersion() throws Exception {
        withTestJar("test-spec-version.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.SPECIFICATION_VERSION, "3.4.5");
        }, jar -> Assert.assertEquals("Should read Specification-Version", "3.4.5",
                invokeGetJarVersion(jar)));
    }

    /**
     * Test getJarVersion() priority: Bundle-Version takes precedence.
     */
    @Test
    public void testGetJarVersionPriority() throws Exception {
        withTestJar("test-version-priority.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Bundle-Version", "1.0.0");
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "2.0.0");
            manifest.getMainAttributes().put(Attributes.Name.SPECIFICATION_VERSION, "3.0.0");
        }, jar -> Assert.assertEquals("Should prioritize Bundle-Version", "1.0.0",
                invokeGetJarVersion(jar)));
    }

    /**
     * Test getJarVersion() with no version information.
     */
    @Test
    public void testGetJarVersionWithNoVersion() throws Exception {
        withTestJar("test-no-version.jar", manifest -> {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }, jar -> Assert.assertNull("Should return null when no version found",
                invokeGetJarVersion(jar)));
    }

    /**
     * Test ServerInfo.main() output with APR/Tomcat Native when available.
     */
    @Test
    public void testServerInfoOutputWithApr() throws Exception {
        // Only run this test if APR is available
        Assume.assumeTrue("APR not available", AprLifecycleListener.isAprAvailable());

        String output = captureServerInfoOutput();

        // Check for APR-specific output
        Assert.assertTrue("Should contain 'APR loaded: true'", output.contains("APR loaded:     true"));
        Assert.assertTrue("Should contain APR Version", output.contains("APR Version:"));
        Assert.assertTrue("Should contain Tomcat Native version", output.contains("Tomcat Native:"));
        // OpenSSL via APR should be present if SSL is initialized
        // Note: May not always be present depending on initialization state
    }

    /**
     * Test ServerInfo.main() output with FFM OpenSSL when available.
     */
    @Test
    public void testServerInfoOutputWithFFM() throws Exception {
        // Only run this test if JRE 22+ is available
        Assume.assumeTrue("JRE 22+ not available", JreCompat.isJre22Available());

        // Initialize FFM OpenSSL
        boolean ffmAvailable = OpenSSLLifecycleListener.isAvailable();
        Assume.assumeTrue("FFM OpenSSL not available", ffmAvailable);

        String output = captureServerInfoOutput();

        // Check for FFM OpenSSL output
        Assert.assertTrue("Should contain OpenSSL (FFM) information", output.contains("OpenSSL (FFM):"));
    }

    /**
     * Test ServerInfo.main() output when neither APR nor FFM is available.
     */
    @Test
    public void testServerInfoOutputWithoutNativeLibraries() throws Exception {
        // Skip if APR or FFM is available
        boolean aprAvailable = AprLifecycleListener.isAprAvailable();
        boolean ffmAvailable = JreCompat.isJre22Available() && OpenSSLLifecycleListener.isAvailable();

        // Only run if neither is available (or force the test by not initializing them)
        // This test validates the "not available" code path
        if (!aprAvailable && !ffmAvailable) {
            String output = captureServerInfoOutput();

            // When no native libraries are available, should show APR loaded: false
            Assert.assertTrue("Should contain 'APR loaded: false'", output.contains("APR loaded:     false"));
            // Should NOT contain FFM or APR version information
            Assert.assertFalse("Should not contain APR Version", output.contains("APR Version:"));
            Assert.assertFalse("Should not contain Tomcat Native", output.contains("Tomcat Native:"));
        }
    }

    /**
     * Test that APR version info is displayed correctly.
     */
    @Test
    public void testAprVersionInfo() throws Exception {
        // Only run if APR is available
        Assume.assumeTrue("APR not available", AprLifecycleListener.isAprAvailable());

        String output = captureServerInfoOutput();

        // Verify version info format (should contain version numbers)
        String[] lines = output.split("\n");
        boolean foundAprVersion = false;
        boolean foundTcnVersion = false;

        for (String line : lines) {
            if (line.contains("APR Version:")) {
                foundAprVersion = true;
                // APR version should be in format like "1.7.0"
                Assert.assertTrue("APR Version line should contain version number",
                        line.matches(".*APR Version:\\s+\\d+\\.\\d+.*"));
            }
            if (line.contains("Tomcat Native:")) {
                foundTcnVersion = true;
                // Tomcat Native version should be in format like "2.0.5"
                Assert.assertTrue("Tomcat Native line should contain version number",
                        line.matches(".*Tomcat Native:\\s+\\d+\\.\\d+.*"));
            }
        }

        Assert.assertTrue("Should have found APR Version line", foundAprVersion);
        Assert.assertTrue("Should have found Tomcat Native line", foundTcnVersion);
    }

    /**
     * Test that version warning is returned when APR is available but outdated.
     * This tests the real version check using the installed APR library.
     */
    @Test
    public void testTomcatNativeVersionWarningWithRealVersion() throws Exception {
        // Only run if APR is available
        Assume.assumeTrue("APR not available", AprLifecycleListener.isAprAvailable());

        // If APR is available, getTcnVersionWarning() should return non-null if version is old,
        // or null if version is current. We can't predict which, so just verify the method works.
        String warning = AprLifecycleListener.getTcnVersionWarning();

        // The warning should either be null (version is OK) or contain expected text
        if (warning != null) {
            Assert.assertTrue("Warning should mention 'WARNING'", warning.contains("WARNING"));
            Assert.assertTrue("Warning should mention version", warning.matches(".*\\d+\\.\\d+\\.\\d+.*"));
        }
        // If warning is null, that's also valid (version is current)
    }

    /**
     * Test that FFM OpenSSL version info is displayed correctly.
     */
    @Test
    public void testFFMVersionInfo() throws Exception {
        // Only run if JRE 22+ and FFM OpenSSL are available
        Assume.assumeTrue("JRE 22+ not available", JreCompat.isJre22Available());

        boolean ffmAvailable = OpenSSLLifecycleListener.isAvailable();
        Assume.assumeTrue("FFM OpenSSL not available", ffmAvailable);

        String output = captureServerInfoOutput();

        // Verify FFM OpenSSL version info format
        String[] lines = output.split("\n");
        boolean foundFFMVersion = false;

        for (String line : lines) {
            if (line.contains("OpenSSL (FFM):")) {
                foundFFMVersion = true;
                // Should contain either version string or library name
                Assert.assertTrue("OpenSSL (FFM) line should not be empty",
                        line.length() > "OpenSSL (FFM):  ".length());
            }
        }

        Assert.assertTrue("Should have found OpenSSL (FFM) line", foundFFMVersion);
    }

    /**
     * Test that OpenSSLLibrary.getVersionString() returns the native version string.
     * This ensures FFM output format matches APR output format.
     */
    @Test
    public void testOpenSSLLibraryVersionString() throws Exception {
        // Only run if JRE 22+ and FFM OpenSSL are available
        Assume.assumeTrue("JRE 22+ not available", JreCompat.isJre22Available());

        boolean ffmAvailable = OpenSSLLifecycleListener.isAvailable();
        Assume.assumeTrue("FFM OpenSSL not available", ffmAvailable);

        // Call OpenSSLLibrary.getVersionString() via reflection
        Class<?> openSSLLibraryClass = Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
        String versionString = (String) openSSLLibraryClass.getMethod("getVersionString").invoke(null);

        // Verify the version string is in the expected format
        Assert.assertNotNull("Version string should not be null", versionString);
        Assert.assertTrue("Version string should start with 'OpenSSL' or library name",
                versionString.matches("^(OpenSSL|LibreSSL|BoringSSL).*"));
        Assert.assertTrue("Version string should contain version number",
                versionString.matches(".*\\d+\\.\\d+.*"));
    }

    /**
     * Functional interface for test logic that can throw exceptions.
     */
    @FunctionalInterface
    private interface TestWithJar {
        void test(File jarFile) throws Exception;
    }

    /**
     * Helper method to run a test with a JAR file and ensure cleanup.
     */
    private void withTestJar(String filename, Consumer<Manifest> customizer, TestWithJar test) throws Exception {
        File testJar = createTestJar(filename, customizer);
        try {
            test.test(testJar);
        } finally {
            testJar.delete();
        }
    }

    /**
     * Helper method to invoke the private isTomcatCoreJar() method via reflection.
     */
    private boolean invokeIsTomcatCoreJar(File jarFile) throws Exception {
        Method method = ServerInfo.class.getDeclaredMethod("isTomcatCoreJar", File.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, jarFile);
    }

    /**
     * Helper method to invoke the private getJarVersion() method via reflection.
     */
    private String invokeGetJarVersion(File jarFile) throws Exception {
        Method method = ServerInfo.class.getDeclaredMethod("getJarVersion", File.class);
        method.setAccessible(true);
        return (String) method.invoke(null, jarFile);
    }

    /**
     * Helper method to capture ServerInfo.main() output.
     */
    private String captureServerInfoOutput() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream oldOut = System.out;
        try {
            System.setOut(ps);
            ServerInfo.main(new String[0]);
        } finally {
            System.setOut(oldOut);
        }
        return baos.toString();
    }

    /**
     * Helper method to create a test JAR file with custom manifest.
     */
    private File createTestJar(String filename, Consumer<Manifest> customizer) throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File jarFile = new File(tempDir, filename);

        Manifest manifest = new Manifest();
        customizer.accept(manifest);

        try (FileOutputStream fos = new FileOutputStream(jarFile);
                JarOutputStream jos = new JarOutputStream(fos, manifest)) {
            // Empty JAR with just manifest
        }

        return jarFile;
    }
}
