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
package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.catalina.LifecycleException;
import org.apache.tomcat.util.compat.JrePlatform;
import org.apache.tomcat.util.http.RequestUtil;

public abstract class AbstractFileResourceSet extends AbstractResourceSet {

    protected static final String[] EMPTY_STRING_ARRAY = new String[0];

    private File fileBase;
    private String absoluteBase;
    private String canonicalBase;
    private boolean readOnly = false;

    protected AbstractFileResourceSet(String internalPath) {
        setInternalPath(internalPath);
    }

    protected final File getFileBase() {
        return fileBase;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    protected final File file(String name, boolean mustExist) {

        if (name.equals("/")) {
            name = "";
        }
        File file = new File(fileBase, name);

        // If the requested names ends in '/', the Java File API will return a
        // matching file if one exists. This isn't what we want as it is not
        // consistent with the Servlet spec rules for request mapping.
        if (name.endsWith("/") && file.isFile()) {
            return null;
        }

        // If the file/dir must exist but the identified file/dir can't be read
        // then signal that the resource was not found
        if (mustExist && !file.canRead()) {
            return null;
        }

        // If allow linking is enabled, files are not limited to being located
        // under the fileBase so all further checks are disabled.
        if (getRoot().getAllowLinking()) {
            return file;
        }

        // Additional Windows specific checks to handle known problems with
        // File.getCanonicalPath()
        if (JrePlatform.IS_WINDOWS && isInvalidWindowsFilename(name)) {
            return null;
        }

        // Check that this file is located under the WebResourceSet's base
        String canPath = null;
        try {
            canPath = file.getCanonicalPath();
        } catch (IOException e) {
            // Ignore
        }
        if (canPath == null || !canPath.startsWith(canonicalBase)) {
            return null;
        }

        // Ensure that the file is not outside the fileBase. This should not be
        // possible for standard requests (the request is normalized early in
        // the request processing) but might be possible for some access via the
        // Servlet API (RequestDispatcher, HTTP/2 push etc.) therefore these
        // checks are retained as an additional safety measure
        // absoluteBase has been normalized so absPath needs to be normalized as
        // well.
        String absPath = normalize(file.getAbsolutePath());
        if (absoluteBase.length() > absPath.length()) {
            return null;
        }

        // Remove the fileBase location from the start of the paths since that
        // was not part of the requested path and the remaining check only
        // applies to the request path
        absPath = absPath.substring(absoluteBase.length());
        canPath = canPath.substring(canonicalBase.length());

        // Case sensitivity check
        // The normalized requested path should be an exact match the equivalent
        // canonical path. If it is not, possible reasons include:
        // - case differences on case insensitive file systems
        // - Windows removing a trailing ' ' or '.' from the file name
        //
        // In all cases, a mis-match here results in the resource not being
        // found
        //
        // absPath is normalized so canPath needs to be normalized as well
        // Can't normalize canPath earlier as canonicalBase is not normalized
        if (canPath.length() > 0) {
            canPath = normalize(canPath);
        }
        if (!canPath.equals(absPath)) {
            return null;
        }

        return file;
    }


    private boolean isInvalidWindowsFilename(String name) {
        final int len = name.length();
        if (len == 0) {
            return false;
        }
        // This consistently ~10 times faster than the equivalent regular
        // expression irrespective of input length.
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (c == '\"' || c == '<' || c == '>') {
                // These characters are disallowed in Windows file names and
                // there are known problems for file names with these characters
                // when using File#getCanonicalPath().
                // Note: There are additional characters that are disallowed in
                //       Windows file names but these are not known to cause
                //       problems when using File#getCanonicalPath().
                return true;
            }
        }
        // Windows does not allow file names to end in ' ' unless specific low
        // level APIs are used to create the files that bypass various checks.
        // File names that end in ' ' are known to cause problems when using
        // File#getCanonicalPath().
        if (name.charAt(len -1) == ' ') {
            return true;
        }
        return false;
    }


    /**
     * Return a context-relative path, beginning with a "/", that represents
     * the canonical version of the specified path after ".." and "." elements
     * are resolved out.  If the specified path attempts to go outside the
     * boundaries of the current context (i.e. too many ".." path elements
     * are present), return <code>null</code> instead.
     *
     * @param path Path to be normalized
     */
    private String normalize(String path) {
        return RequestUtil.normalize(path, File.separatorChar == '\\');
    }

    @Override
    public URL getBaseUrl() {
        try {
            return getFileBase().toURI().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This is a NO-OP by default for File based resource sets.
     */
    @Override
    public void gc() {
        // NO-OP
    }


    //-------------------------------------------------------- Lifecycle methods

    @Override
    protected void initInternal() throws LifecycleException {
        fileBase = new File(getBase(), getInternalPath());
        checkType(fileBase);

        this.absoluteBase = normalize(fileBase.getAbsolutePath());

        try {
            this.canonicalBase = fileBase.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }


    protected abstract void checkType(File file);
}
