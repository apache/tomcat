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
import java.nio.ByteBuffer;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;

/**
 * Input filter responsible for replaying the request body when restoring the saved request after FORM authentication.
 */
public class SavedRequestInputFilter implements InputFilter {

    /**
     * The original request body.
     */
    protected ByteChunk input = null;

    /**
     * Create a new SavedRequestInputFilter.
     *
     * @param input The saved request body to be replayed.
     */
    public SavedRequestInputFilter(ByteChunk input) {
        this.input = input;
    }

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        if (input.getStart() >= input.getEnd()) {
            return -1;
        }

        int len = input.getLength();
        handler.setByteBuffer(ByteBuffer.wrap(input.getBytes(), input.getStart(), len));
        input.setStart(input.getEnd());
        return len;
    }

    /**
     * {@inheritDoc} Set the content length on the request.
     */
    @Override
    public void setRequest(org.apache.coyote.Request request) {
        request.setContentLength(input.getLength());
    }

    @Override
    public void recycle() {
        input = null;
    }

    /**
     * @return null
     */
    @Override
    public ByteChunk getEncodingName() {
        return null;
    }

    /**
     * Set the next buffer in the filter pipeline (has no effect).
     *
     * @param buffer ignored
     */
    @Override
    public void setBuffer(InputBuffer buffer) {
        // NOOP since this filter will be providing the request body
    }

    @Override
    public int available() {
        return input.getLength();
    }

    /**
     * End the current request (has no effect).
     *
     * @return 0
     */
    @Override
    public long end() throws IOException {
        return 0;
    }

    @Override
    public boolean isFinished() {
        return input.getStart() >= input.getEnd();
    }
}
