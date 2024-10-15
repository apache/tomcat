/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;

/**
 * Void input filter, which returns -1 when attempting a read. Used with a GET, HEAD, or a similar request.
 *
 * @author Remy Maucherat
 */
public class VoidInputFilter implements InputFilter {


    // -------------------------------------------------------------- Constants

    protected static final String ENCODING_NAME = "void";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(StandardCharsets.ISO_8859_1), 0, ENCODING_NAME.length());
    }


    // ---------------------------------------------------- InputBuffer Methods

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        return -1;
    }


    // ---------------------------------------------------- InputFilter Methods

    @Override
    public void setRequest(Request request) {
        // NOOP: Request isn't used so ignore it
    }


    @Override
    public void setBuffer(InputBuffer buffer) {
        // NOOP: No body to read
    }


    @Override
    public void recycle() {
        // NOOP
    }


    @Override
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    @Override
    public long end() throws IOException {
        return 0;
    }


    @Override
    public int available() {
        return 0;
    }


    @Override
    public boolean isFinished() {
        return true;
    }
}
