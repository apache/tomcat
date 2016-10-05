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
package org.apache.catalina.webresources.war;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;

import org.apache.tomcat.util.buf.UriUtil;


public class WarURLConnection extends URLConnection {

    private final URLConnection wrappedJarUrlConnection;
    private boolean connected;

    protected WarURLConnection(URL url) throws IOException {
        super(url);
        URL innerJarUrl = UriUtil.warToJar(url);
        wrappedJarUrlConnection = innerJarUrl.openConnection();
    }


    @Override
    public void connect() throws IOException {
        if (!connected) {
            wrappedJarUrlConnection.connect();
            connected = true;
        }
    }


    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return wrappedJarUrlConnection.getInputStream();
    }


    @Override
    public Permission getPermission() throws IOException {
        return wrappedJarUrlConnection.getPermission();
    }
}
