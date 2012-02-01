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

public class WsInputStream extends java.io.InputStream {

    private InputStream wrapped;
    private byte[] mask;
    private long remaining;
    private long read;

    public WsInputStream(InputStream wrapped, byte[] mask, long remaining) {
        this.wrapped = wrapped;
        this.mask = mask;
        this.remaining = remaining;
        this.read = 0;
    }

    @Override
    public int read() throws IOException {
        if (remaining == 0) {
            return -1;
        }

        remaining--;
        read++;

        int masked = wrapped.read();
        return masked ^ mask[(int) ((read - 1) % 4)];
    }

}
