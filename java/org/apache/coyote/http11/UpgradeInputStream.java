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
package org.apache.coyote.http11;

import java.io.IOException;
import java.io.InputStream;

import org.apache.coyote.InputBuffer;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.ByteChunk.ByteInputChannel;

/**
 * Provides a buffered {@link InputStream} to read data from the upgraded
 * connection.
 *
 * TODO: Override a few more {@link InputStream} methods for efficiency.
 *
 * Based on a combination of {@link org.apache.catalina.connector.InputBuffer}
 * and (@link CoyoteInputStream}.
 */
public class UpgradeInputStream extends InputStream
        implements ByteInputChannel {

    private InputBuffer ib = null;
    private ByteChunk bb = new ByteChunk(8192);

    public UpgradeInputStream(InputBuffer ib) {
        this.ib = ib;
        bb.setByteInputChannel(this);
    }

    @Override
    public int read() throws IOException {
        return bb.substract();
    }

    @Override
    public int realReadBytes(byte[] cbuf, int off, int len) throws IOException {
        return ib.doRead(bb, null);
    }
}
