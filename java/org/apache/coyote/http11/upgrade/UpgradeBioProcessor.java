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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.servlet.http.ProtocolHandler;

import org.apache.tomcat.util.net.SocketWrapper;

public class UpgradeBioProcessor extends UpgradeProcessor<Socket> {

    private static final int INFINITE_TIMEOUT = 0;

    public UpgradeBioProcessor(SocketWrapper<Socket> wrapper,
            ProtocolHandler httpUpgradeProcessor) throws IOException {
        super(httpUpgradeProcessor, new BioUpgradeServletInputStream(wrapper),
                new BioUpgradeServletOutputStream(wrapper));

        wrapper.getSocket().setSoTimeout(INFINITE_TIMEOUT);
    }


    // ----------------------------------------------------------- Inner classes

    private static class BioUpgradeServletInputStream
            extends UpgradeServletInputStream {

        private final InputStream is;

        public BioUpgradeServletInputStream(SocketWrapper<Socket> wrapper)
                throws IOException {
            is = wrapper.getSocket().getInputStream();
        }

        @Override
        protected int doRead() throws IOException {
            return is.read();
        }

        @Override
        protected int doRead(byte[] b, int off, int len) throws IOException {
            return is.read(b, off, len);
        }
    }

    private static class BioUpgradeServletOutputStream
            extends UpgradeServletOutputStream {

        private final OutputStream os;

        public BioUpgradeServletOutputStream(SocketWrapper<Socket> wrapper)
                throws IOException {
            os = wrapper.getSocket().getOutputStream();
        }

        @Override
        protected void doWrite(int b) throws IOException {
            os.write(b);
        }

        @Override
        protected void doWrite(byte[] b, int off, int len) throws IOException {
            os.write(b, off, len);
        }

        @Override
        protected void doFlush() throws IOException {
            os.flush();
        }
    }
}
