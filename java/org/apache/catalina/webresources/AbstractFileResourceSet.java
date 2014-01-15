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
        if (!mustExist || file.canRead()) {

            if (getRoot().getAllowLinking()) {
                return file;
            }

            // Check that this file is located under the WebResourceSet's base
            String canPath = null;
            try {
                canPath = file.getCanonicalPath();
            } catch (IOException e) {
                // Ignore
            }
            if (canPath == null)
                return null;

            if (!canPath.startsWith(canonicalBase)) {
                return null;
            }

            // Case sensitivity check
            // Note: We know the resource is located somewhere under base at
            //       point. The purpose of this code is to check in a case
            //       sensitive manner, the path to the resource under base
            //       agrees with what was requested
            String fileAbsPath = file.getAbsolutePath();
            if (fileAbsPath.endsWith("."))
                fileAbsPath = fileAbsPath + '/';
            String absPath = normalize(fileAbsPath);
            if ((absoluteBase.length() < absPath.length())
                && (canonicalBase.length() < canPath.length())) {
                absPath = absPath.substring(absoluteBase.length() + 1);
                if (absPath.equals(""))
                    absPath = "/";
                canPath = canPath.substring(canonicalBase.length() + 1);
                if (canPath.equals(""))
                    canPath = "/";
                if (!canPath.equals(absPath))
                    return null;
            }

        } else {
            return null;
        }
        return file;
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
        return RequestUtil.normalize(path, File.separatorChar == '/');
    }

    @Override
    public URL getBaseUrl() {
        try {
            return getFileBase().toURI().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }


    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {
        fileBase = new File(getBase(), getInternalPath());
        checkType(fileBase);

        String absolutePath = fileBase.getAbsolutePath();
        if (absolutePath.endsWith(".")) {
            absolutePath = absolutePath + '/';
        }
        this.absoluteBase = normalize(absolutePath);

        try {
            this.canonicalBase = fileBase.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }


    protected abstract void checkType(File file);
}
