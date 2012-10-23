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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Represents a single resource (file or directory) that is located on a file
 * system.
 */
public class FileResource extends AbstractResource {

    private static final Log log = LogFactory.getLog(FileResource.class);

    private final File resource;
    private final String name;

    public FileResource(WebResourceRoot root, File resource,
            String webAppPath) {
        super(root,webAppPath);
        this.resource = resource;

        if (webAppPath.charAt(webAppPath.length() - 1) == '/') {
            String realName = resource.getName() + '/';
            if (webAppPath.endsWith(realName)) {
                name = resource.getName();
            } else {
                // This is the root directory of a mounted ResourceSet
                // Need to return the mounted name, not the real name
                int endOfName = webAppPath.length() - 1;
                name = webAppPath.substring(
                        webAppPath.lastIndexOf("/", endOfName - 1) + 1,
                        endOfName);
            }
        } else {
            // Must be a file
            name = resource.getName();
        }
    }

    @Override
    public long getLastModified() {
        return resource.lastModified();
    }

    @Override
    public boolean exists() {
        return resource.exists();
    }

    @Override
    public boolean isDirectory() {
        return resource.isDirectory();
    }

    @Override
    public boolean isFile() {
        return resource.isFile();
    }

    @Override
    public boolean delete() {
        return resource.delete();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getContentLength() {
        return resource.length();
    }

    @Override
    public String getCanonicalPath() {
        try {
            return resource.getCanonicalPath();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("fileResource.getCanonicalPathFail",
                        resource.getPath()), ioe);
            }
            return null;
        }
    }

    @Override
    public boolean canRead() {
        return resource.canRead();
    }

    @Override
    public InputStream getInputStream() {
        if (resource.exists()) {
            try {
                return new FileInputStream(resource);
            } catch (FileNotFoundException fnfe) {
                // Race condition - not an error
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public long getCreation() {
        try {
            BasicFileAttributes attrs = Files.readAttributes(resource.toPath(),
                    BasicFileAttributes.class);
            return attrs.creationTime().toMillis();
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("fileResource.getCreationFail",
                        resource.getPath()), e);
            }
            return 0;
        }
    }

    @Override
    public URL getURL() {
        if (resource.exists()) {
            try {
                return resource.toURI().toURL();
            } catch (MalformedURLException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("fileResource.getUrlFail",
                            resource.getPath()), e);
                }
                return null;
            }
        } else {
            return null;
        }
    }

    protected File getResourceInternal() {
        return resource;
    }

    @Override
    protected Log getLog() {
        return log;
    }
}
