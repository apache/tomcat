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

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.apache.catalina.Host;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Expand out a WAR in a Host's appBase.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @author Glenn L. Nielsen
 */
public class ExpandWar {

    private static final Log log = LogFactory.getLog(ExpandWar.class);

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * Expand the WAR file found at the specified URL into an unpacked directory structure.
     *
     * @param host     Host war is being installed for
     * @param war      URL of the web application archive to be expanded (must start with "jar:")
     * @param pathname Context path name for web application
     *
     * @exception IllegalArgumentException if this is not a "jar:" URL or if the WAR file is invalid
     * @exception IOException              if an input/output error was encountered during expansion
     *
     * @return The absolute path to the expanded directory for the given WAR
     */
    public static String expand(Host host, URL war, String pathname) throws IOException {

        /*
         * Obtaining the last modified time opens an InputStream and there is no explicit close method. We have to
         * obtain and then close the InputStream to avoid a file leak and the associated locked file.
         */
        JarURLConnection juc = (JarURLConnection) war.openConnection();
        juc.setUseCaches(false);
        URL jarFileUrl = juc.getJarFileURL();
        URLConnection jfuc = jarFileUrl.openConnection();

        boolean success = false;
        File docBase = new File(host.getAppBaseFile(), pathname);
        File warTracker = new File(host.getAppBaseFile(), pathname + Constants.WarTracker);
        long warLastModified = -1;

        try (InputStream is = jfuc.getInputStream()) {
            // Get the last modified time for the WAR
            warLastModified = jfuc.getLastModified();
        }

        // Check to see of the WAR has been expanded previously
        if (docBase.exists()) {
            // A WAR was expanded. Tomcat will have set the last modified
            // time of warTracker file to the last modified time of the WAR so
            // changes to the WAR while Tomcat is stopped can be detected
            if (!warTracker.exists() || warTracker.lastModified() == warLastModified) {
                // No (detectable) changes to the WAR
                success = true;
                return docBase.getAbsolutePath();
            }

            // WAR must have been modified. Remove expanded directory.
            log.info(sm.getString("expandWar.deleteOld", docBase));
            if (!delete(docBase)) {
                throw new IOException(sm.getString("expandWar.deleteFailed", docBase));
            }
        }

        // Create the new document base directory
        if (!docBase.mkdir() && !docBase.isDirectory()) {
            throw new IOException(sm.getString("expandWar.createFailed", docBase));
        }

        // Expand the WAR into the new document base directory
        Path canonicalDocBasePath = docBase.getCanonicalFile().toPath();

        // Creating war tracker parent (normally META-INF)
        File warTrackerParent = warTracker.getParentFile();
        if (!warTrackerParent.isDirectory() && !warTrackerParent.mkdirs()) {
            throw new IOException(sm.getString("expandWar.createFailed", warTrackerParent.getAbsolutePath()));
        }

        try (JarFile jarFile = juc.getJarFile()) {

            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                String name = jarEntry.getName();
                File expandedFile = new File(docBase, name);
                if (!expandedFile.getCanonicalFile().toPath().startsWith(canonicalDocBasePath)) {
                    // Trying to expand outside the docBase
                    // Throw an exception to stop the deployment
                    throw new IllegalArgumentException(sm.getString("expandWar.illegalPath", war, name,
                            expandedFile.getCanonicalPath(), canonicalDocBasePath));
                }
                int last = name.lastIndexOf('/');
                if (last >= 0) {
                    File parent = new File(docBase, name.substring(0, last));
                    if (!parent.mkdirs() && !parent.isDirectory()) {
                        throw new IOException(sm.getString("expandWar.createFailed", parent));
                    }
                }
                if (name.endsWith("/")) {
                    continue;
                }

                try (InputStream input = jarFile.getInputStream(jarEntry)) {
                    if (null == input) {
                        throw new ZipException(sm.getString("expandWar.missingJarEntry", jarEntry.getName()));
                    }

                    // Bugzilla 33636
                    expand(input, expandedFile);
                    long lastModified = jarEntry.getTime();
                    if ((lastModified != -1) && (lastModified != 0)) {
                        if (!expandedFile.setLastModified(lastModified)) {
                            throw new IOException(sm.getString("expandWar.lastModifiedFailed", expandedFile));
                        }
                    }
                }
            }

            // Create the warTracker file and align the last modified time
            // with the last modified time of the WAR
            if (!warTracker.createNewFile()) {
                throw new IOException(sm.getString("expandWar.createFileFailed", warTracker));
            }
            if (!warTracker.setLastModified(warLastModified)) {
                throw new IOException(sm.getString("expandWar.lastModifiedFailed", warTracker));
            }

            success = true;
        } catch (IOException e) {
            throw e;
        } finally {
            if (!success) {
                // If something went wrong, delete expanded dir to keep things
                // clean
                deleteDir(docBase);
            }
        }

        // Return the absolute path to our new document base directory
        return docBase.getAbsolutePath();
    }


    /**
     * Validate the WAR file found at the specified URL.
     *
     * @param host     Host war is being installed for
     * @param war      URL of the web application archive to be validated (must start with "jar:")
     * @param pathname Context path name for web application
     *
     * @exception IllegalArgumentException if this is not a "jar:" URL or if the WAR file is invalid
     * @exception IOException              if an input/output error was encountered during validation
     */
    public static void validate(Host host, URL war, String pathname) throws IOException {

        File docBase = new File(host.getAppBaseFile(), pathname);

        // Calculate the document base directory
        Path canonicalDocBasePath = docBase.getCanonicalFile().toPath();
        JarURLConnection juc = (JarURLConnection) war.openConnection();
        juc.setUseCaches(false);
        try (JarFile jarFile = juc.getJarFile()) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                String name = jarEntry.getName();
                File expandedFile = new File(docBase, name);
                if (!expandedFile.getCanonicalFile().toPath().startsWith(canonicalDocBasePath)) {
                    // Entry located outside the docBase
                    // Throw an exception to stop the deployment
                    throw new IllegalArgumentException(sm.getString("expandWar.illegalPath", war, name,
                            expandedFile.getCanonicalPath(), canonicalDocBasePath));
                }
            }
        } catch (IOException e) {
            throw e;
        }
    }


    /**
     * Copy the specified file or directory to the destination.
     *
     * @param src  File object representing the source
     * @param dest File object representing the destination
     *
     * @return <code>true</code> if the copy was successful
     */
    public static boolean copy(File src, File dest) {

        boolean result = true;

        String files[] = null;
        if (src.isDirectory()) {
            files = src.list();
            result = dest.mkdir();
        } else {
            files = new String[1];
            files[0] = "";
        }
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; (i < files.length) && result; i++) {
            File fileSrc = new File(src, files[i]);
            File fileDest = new File(dest, files[i]);
            if (fileSrc.isDirectory()) {
                result = copy(fileSrc, fileDest);
            } else {
                try (FileInputStream fis = new FileInputStream(fileSrc);
                        FileChannel ic = (fis).getChannel();
                        FileOutputStream fos = new FileOutputStream(fileDest);
                        FileChannel oc = (fos).getChannel()) {
                    long size = ic.size();
                    long position = 0;
                    while (size > 0) {
                        long count = ic.transferTo(position, size, oc);
                        if (count > 0) {
                            position += count;
                            size -= count;
                        } else {
                            throw new EOFException();
                        }
                    }
                } catch (IOException e) {
                    log.error(sm.getString("expandWar.copy", fileSrc, fileDest), e);
                    result = false;
                }
            }
        }
        return result;
    }


    /**
     * Delete the specified directory, including all of its contents and sub-directories recursively. Any failure will
     * be logged.
     *
     * @param dir File object representing the directory to be deleted
     *
     * @return <code>true</code> if the deletion was successful
     */
    public static boolean delete(File dir) {
        // Log failure by default
        return delete(dir, true);
    }


    /**
     * Delete the specified directory, including all of its contents and sub-directories recursively.
     *
     * @param dir        File object representing the directory to be deleted
     * @param logFailure <code>true</code> if failure to delete the resource should be logged
     *
     * @return <code>true</code> if the deletion was successful
     */
    public static boolean delete(File dir, boolean logFailure) {
        boolean result;
        if (dir.isDirectory()) {
            result = deleteDir(dir, logFailure);
        } else {
            if (dir.exists()) {
                result = dir.delete();
            } else {
                result = true;
            }
        }
        if (logFailure && !result) {
            log.error(sm.getString("expandWar.deleteFailed", dir.getAbsolutePath()));
        }
        return result;
    }


    /**
     * Delete the specified directory, including all of its contents and sub-directories recursively. Any failure will
     * be logged.
     *
     * @param dir File object representing the directory to be deleted
     *
     * @return <code>true</code> if the deletion was successful
     */
    public static boolean deleteDir(File dir) {
        return deleteDir(dir, true);
    }


    /**
     * Delete the specified directory, including all of its contents and sub-directories recursively.
     *
     * @param dir        File object representing the directory to be deleted
     * @param logFailure <code>true</code> if failure to delete the resource should be logged
     *
     * @return <code>true</code> if the deletion was successful
     */
    public static boolean deleteDir(File dir, boolean logFailure) {

        String files[] = dir.list();
        if (files == null) {
            files = new String[0];
        }
        for (String s : files) {
            File file = new File(dir, s);
            if (file.isDirectory()) {
                deleteDir(file, logFailure);
            } else {
                file.delete();
            }
        }

        boolean result;
        if (dir.exists()) {
            result = dir.delete();
        } else {
            result = true;
        }

        if (logFailure && !result) {
            log.error(sm.getString("expandWar.deleteFailed", dir.getAbsolutePath()));
        }

        return result;
    }


    /**
     * Expand the specified input stream into the specified file.
     *
     * @param input InputStream to be copied
     * @param file  The file to be created
     *
     * @exception IOException if an input/output error occurs
     */
    private static void expand(InputStream input, File file) throws IOException {
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            byte buffer[] = new byte[2048];
            while (true) {
                int n = input.read(buffer);
                if (n <= 0) {
                    break;
                }
                output.write(buffer, 0, n);
            }
        }
    }
}
