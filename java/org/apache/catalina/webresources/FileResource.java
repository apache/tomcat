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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Represents a single resource (file or directory) that is located on a file system.
 */
public class FileResource extends AbstractResource {

    private static final Log log = LogFactory.getLog(FileResource.class);

    private static final boolean PROPERTIES_NEED_CONVERT;
    static {
        boolean isEBCDIC = false;
        try {
            String encoding = Charset.defaultCharset().displayName();
            if (encoding.contains("EBCDIC")) {
                isEBCDIC = true;
            }
        } catch (SecurityException e) {
            // Ignore
        }
        PROPERTIES_NEED_CONVERT = isEBCDIC;
    }


    private final Path resource;
    private final String name;
    private final boolean readOnly;
    private final Manifest manifest;
    private final boolean needConvert;

    public FileResource(WebResourceRoot root, String webAppPath, File resource, boolean readOnly, Manifest manifest) {
        this(root, webAppPath, resource.toPath(), readOnly, manifest);
    }

    public FileResource(WebResourceRoot root, String webAppPath, Path resource, boolean readOnly, Manifest manifest) {
        super(root, webAppPath);
        this.resource = resource;

        if (webAppPath.charAt(webAppPath.length() - 1) == '/') {
            String realName = resource.getFileName().toString() + '/';
            if (webAppPath.endsWith(realName)) {
                name = resource.getFileName().toString();
            } else {
                // This is the root directory of a mounted ResourceSet
                // Need to return the mounted name, not the real name
                int endOfName = webAppPath.length() - 1;
                name = webAppPath.substring(webAppPath.lastIndexOf('/', endOfName - 1) + 1, endOfName);
            }
        } else {
            // Must be a file
            name = resource.getFileName().toString();
        }

        this.readOnly = readOnly;
        this.manifest = manifest;
        this.needConvert = PROPERTIES_NEED_CONVERT && name.endsWith(".properties");
    }

    @Override
    public long getLastModified() {
        try {
            return Files.getLastModifiedTime(resource).toMillis();
        } catch (final IOException e) {
            return -1;
        }
    }

    @Override
    public boolean exists() {
        return Files.exists(resource);
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return Files.isDirectory(resource);
    }

    @Override
    public boolean isFile() {
        return Files.isRegularFile(resource);
    }

    @Override
    public boolean delete() {
        if (readOnly) {
            return false;
        }
        try {
            Files.delete(resource);
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getContentLength() {
        return getContentLengthInternal(needConvert);
    }

    private long getContentLengthInternal(boolean convert) {
        if (convert) {
            byte[] content = getContent();
            if (content == null) {
                return -1;
            } else {
                return content.length;
            }
        }

        if (isDirectory()) {
            return -1;
        }

        try {
            return Files.size(resource);
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public String getCanonicalPath() {
        return resource.toAbsolutePath().normalize().toString();
    }

    @Override
    public boolean canRead() {
        return Files.isReadable(resource);
    }

    @Override
    protected InputStream doGetInputStream() {
        if (needConvert) {
            byte[] content = getContent();
            if (content == null) {
                return null;
            } else {
                return new ByteArrayInputStream(content);
            }
        }
        try {
            return Files.newInputStream(resource);
        } catch (IOException fnfe) {
            // Race condition (file has been deleted) - not an error
            return null;
        }
    }

    @Override
    public final byte[] getContent() {
        // Use internal version to avoid loop when needConvert is true
        long len = getContentLengthInternal(false);

        if (len > Integer.MAX_VALUE) {
            // Can't create an array that big
            throw new ArrayIndexOutOfBoundsException(
                    sm.getString("abstractResource.getContentTooLarge", getWebappPath(), Long.valueOf(len)));
        }

        if (len < 0) {
            // Content is not applicable here (e.g. is a directory)
            return null;
        }

        int size = (int) len;
        byte[] result = new byte[size];

        int pos = 0;
        try (InputStream is = Files.newInputStream(resource)) {
            while (pos < size) {
                int n = is.read(result, pos, size - pos);
                if (n < 0) {
                    break;
                }
                pos += n;
            }
        } catch (IOException ioe) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractResource.getContentFail", getWebappPath()), ioe);
            }
            return null;
        }

        if (needConvert) {
            // Workaround for certain files on platforms that use
            // EBCDIC encoding, when they are read through FileInputStream.
            // See commit message of rev.303915 for original details
            // https://svn.apache.org/viewvc?view=revision&revision=303915
            String str = new String(result);
            try {
                result = str.getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                result = null;
            }
        }
        return result;
    }


    @Override
    public long getCreation() {
        try {
            BasicFileAttributes attrs = Files.readAttributes(resource, BasicFileAttributes.class);
            return attrs.creationTime().toMillis();
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("fileResource.getCreationFail", resource.toString()), e);
            }
            return 0;
        }
    }

    @Override
    public URL getURL() {
        if (Files.exists(resource)) {
            try {
                return resource.toUri().toURL();
            } catch (MalformedURLException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("fileResource.getUrlFail", resource.toString()), e);
                }
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Certificate[] getCertificates() {
        return null;
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    protected Log getLog() {
        return log;
    }
}
