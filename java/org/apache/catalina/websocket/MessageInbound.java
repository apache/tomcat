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
package org.apache.catalina.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

public abstract class MessageInbound extends StreamInbound {

    // TODO: Make buffer sizes configurable
    // TODO: Allow buffers to expand
    ByteBuffer bb = ByteBuffer.allocate(8192);
    CharBuffer cb = CharBuffer.allocate(8192);

    @Override
    protected void onBinaryData(InputStream is) throws IOException {
        int read = 0;
        while (read > -1) {
            bb.position(bb.position() + read);
            read = is.read(bb.array(), bb.position(), bb.remaining());
        }
        bb.flip();
        onBinaryMessage(bb);
        bb.clear();
    }

    @Override
    protected void onTextData(Reader r) throws IOException {
        int read = 0;
        while (read > -1) {
            cb.position(cb.position() + read);
            read = r.read(cb.array(), cb.position(), cb.remaining());
        }
        cb.limit(cb.position());
        cb.position(0);
        onTextMessage(cb);
        cb.clear();
    }

    protected abstract void onBinaryMessage(ByteBuffer message)
            throws IOException;
    protected abstract void onTextMessage(CharBuffer message)
            throws IOException;
}
