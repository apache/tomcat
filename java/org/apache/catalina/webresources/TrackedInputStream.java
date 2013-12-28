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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.catalina.TrackedWebResource;
import org.apache.catalina.WebResourceRoot;

class TrackedInputStream extends InputStream implements TrackedWebResource {

    private final WebResourceRoot root;
    private final String name;
    private final InputStream is;
    private final Exception creation;

    TrackedInputStream(WebResourceRoot root, String name, InputStream is) {
        this.root = root;
        this.name = name;
        this.is = is;
        this.creation = new Exception();

        root.registerTrackedResource(this);
    }

    @Override
    public int read() throws IOException {
        return is.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return is.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return is.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return is.skip(n);
    }

    @Override
    public int available() throws IOException {
        return is.available();
    }

    @Override
    public void close() throws IOException {
        root.deregisterTrackedResource(this);
        is.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        is.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        is.reset();
    }

    @Override
    public boolean markSupported() {
        return is.markSupported();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Exception getCreatedBy() {
        return creation;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        sw.append('[');
        sw.append(name);
        sw.append(']');
        sw.append(System.lineSeparator());
        creation.printStackTrace(pw);
        pw.flush();

        return sw.toString();
    }
}
