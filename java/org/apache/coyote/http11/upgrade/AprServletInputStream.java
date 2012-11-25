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
package org.apache.coyote.http11.upgrade;

import java.io.IOException;

import org.apache.tomcat.util.net.SocketWrapper;

public class AprServletInputStream extends AbstractServletInputStream {

    private final long socket;


    public AprServletInputStream(SocketWrapper<Long> wrapper) {
        this.socket = wrapper.getSocket().longValue();
    }
/*
    @Override
    protected int doRead() throws IOException {
        byte[] bytes = new byte[1];
        int result = Socket.recv(socket, bytes, 0, 1);
        if (result == -1) {
            return -1;
        } else {
            return bytes[0] & 0xFF;
        }
    }

    @Override
    protected int doRead(byte[] b, int off, int len) throws IOException {
        boolean block = true;
        if (!block) {
            Socket.optSet(socket, Socket.APR_SO_NONBLOCK, -1);
        }
        try {
            int result = Socket.recv(socket, b, off, len);
            if (result > 0) {
                return result;
            } else if (-result == Status.EAGAIN) {
                return 0;
            } else {
                throw new IOException(sm.getString("apr.error",
                        Integer.valueOf(-result)));
            }
        } finally {
            if (!block) {
                Socket.optSet(socket, Socket.APR_SO_NONBLOCK, 0);
            }
        }
    }
}
*/

    @Override
    protected int doRead(boolean block, byte[] b, int off, int len)
            throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    protected boolean doIsReady() {
        // TODO Auto-generated method stub
        return false;
    }

}
