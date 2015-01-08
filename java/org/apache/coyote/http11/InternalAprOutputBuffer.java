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
package org.apache.coyote.http11;

import java.io.IOException;

import org.apache.coyote.Response;

/**
 * Output buffer.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class InternalAprOutputBuffer extends AbstractOutputBuffer<Long> {

    // ----------------------------------------------------------- Constructors

    /**
     * Default constructor.
     */
    public InternalAprOutputBuffer(Response response, int headerBufferSize) {
        super(response, headerBufferSize);
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected synchronized void addToBB(byte[] buf, int offset, int length) throws IOException {
        socketWrapper.write(isBlocking(), buf, offset, length);
    }


    @Override
    protected boolean flushBuffer(boolean block) throws IOException {
        return socketWrapper.flush(block);
    }


    @Override
    protected void registerWriteInterest() throws IOException {
        socketWrapper.registerWriteInterest();
    }
}
