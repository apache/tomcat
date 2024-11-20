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

import java.io.InputStream;
import java.security.MessageDigest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;

public abstract class AbstractResource implements WebResource {

    protected static final StringManager sm = StringManager.getManager(AbstractResource.class);

    private final WebResourceRoot root;
    private final String webAppPath;

    private String mimeType = null;
    private volatile String weakETag;
    private volatile String strongETag;


    protected AbstractResource(WebResourceRoot root, String webAppPath) {
        this.root = root;
        this.webAppPath = webAppPath;
    }


    @Override
    public final WebResourceRoot getWebResourceRoot() {
        return root;
    }


    @Override
    public final String getWebappPath() {
        return webAppPath;
    }


    @Override
    public final String getLastModifiedHttp() {
        return FastHttpDateFormat.formatDate(getLastModified());
    }


    @Override
    public final String getETag() {
        if (weakETag == null) {
            synchronized (this) {
                if (weakETag == null) {
                    long contentLength = getContentLength();
                    long lastModified = getLastModified();
                    if ((contentLength >= 0) || (lastModified >= 0)) {
                        weakETag = "W/\"" + contentLength + "-" + lastModified + "\"";
                    }
                }
            }
        }
        return weakETag;
    }

    @Override
    public final String getStrongETag() {
        if (strongETag == null) {
            synchronized (this) {
                if (strongETag == null) {
                    long contentLength = getContentLength();
                    long lastModified = getLastModified();
                    if (contentLength > 0 && lastModified > 0) {
                        if (contentLength <= 16 * 1024) {
                            byte[] buf = getContent();
                            if (buf != null) {
                                buf = ConcurrentMessageDigest.digest("SHA-1", buf);
                                strongETag = "\"" + HexUtils.toHexString(buf) + "\"";
                            } else {
                                strongETag = getETag();
                            }
                        } else {
                            byte[] buf = new byte[4096];
                            try (InputStream is = getInputStream()) {
                                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                                while (true) {
                                    int n = is.read(buf);
                                    if (n <= 0) {
                                        break;
                                    }
                                    digest.update(buf, 0, n);
                                }
                                strongETag = "\"" + HexUtils.toHexString(digest.digest()) + "\"";
                            } catch (Exception e) {
                                strongETag = getETag();
                            }
                        }
                    } else {
                        strongETag = getETag();
                    }
                }
            }
        }
        return strongETag;
    }

    @Override
    public final void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }


    @Override
    public final String getMimeType() {
        return mimeType;
    }


    @Override
    public final InputStream getInputStream() {
        InputStream is = doGetInputStream();

        if (is == null || !root.getTrackLockedFiles()) {
            return is;
        }

        return new TrackedInputStream(root, getName(), is);
    }

    protected abstract InputStream doGetInputStream();


    protected abstract Log getLog();
}
