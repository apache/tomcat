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
import java.nio.channels.Selector;

import javax.servlet.http.ProtocolHandler;

import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioSelectorPool;
import org.apache.tomcat.util.net.SocketWrapper;

public class UpgradeNioProcessor extends UpgradeProcessor<NioChannel> {

    private static final int INFINITE_TIMEOUT = -1;

    public UpgradeNioProcessor(SocketWrapper<NioChannel> wrapper,
            ProtocolHandler httpUpgradeProcessor, NioSelectorPool pool) {
        super(httpUpgradeProcessor,
                new UpgradeNioServletInputStream(wrapper, pool),
                new NioUpgradeServletOutputStream(wrapper, pool));

        wrapper.setTimeout(INFINITE_TIMEOUT);
    }


    // ----------------------------------------------------------- Inner classes

    private static class NioUpgradeServletOutputStream
            extends UpgradeServletOutputStream {

        private final NioChannel nioChannel;
        private final NioSelectorPool pool;
        private final int maxWrite;

        public NioUpgradeServletOutputStream(
                SocketWrapper<NioChannel> wrapper, NioSelectorPool pool) {
            nioChannel = wrapper.getSocket();
            this.pool = pool;
            maxWrite = nioChannel.getBufHandler().getWriteBuffer().capacity();
        }

        @Override
        protected void doWrite(int b) throws IOException {
            writeToSocket(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        protected void doWrite(byte[] b, int off, int len) throws IOException {
            int written = 0;
            while (len - written > maxWrite) {
                written += writeToSocket(b, off + written, maxWrite);
            }
            writeToSocket(b, off + written, len - written);
        }

        @Override
        protected void doFlush() throws IOException {
            NioEndpoint.KeyAttachment att =
                    (NioEndpoint.KeyAttachment) nioChannel.getAttachment(false);
            if (att == null) {
                throw new IOException("Key must be cancelled");
            }
            long writeTimeout = att.getTimeout();
            Selector selector = null;
            try {
                selector = pool.get();
            } catch ( IOException x ) {
                //ignore
            }
            try {
                do {
                    if (nioChannel.flush(true, selector, writeTimeout)) {
                        break;
                    }
                } while (true);
            } finally {
                if (selector != null) {
                    pool.put(selector);
                }
            }
        }

        /*
         * Adapted from the NioOutputBuffer
         */
        private synchronized int writeToSocket(byte[] bytes, int off, int len)
                throws IOException {

            nioChannel.getBufHandler().getWriteBuffer().clear();
            nioChannel.getBufHandler().getWriteBuffer().put(bytes, off, len);
            nioChannel.getBufHandler().getWriteBuffer().flip();

            int written = 0;
            NioEndpoint.KeyAttachment att =
                    (NioEndpoint.KeyAttachment) nioChannel.getAttachment(false);
            if (att == null) {
                throw new IOException("Key must be cancelled");
            }
            long writeTimeout = att.getTimeout();
            Selector selector = null;
            try {
                selector = pool.get();
            } catch ( IOException x ) {
                //ignore
            }
            try {
                written = pool.write(nioChannel.getBufHandler().getWriteBuffer(),
                        nioChannel, selector, writeTimeout, true);
            } finally {
                if (selector != null) {
                    pool.put(selector);
                }
            }
            return written;
        }
    }
}
