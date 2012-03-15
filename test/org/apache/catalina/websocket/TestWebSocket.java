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

import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;

public class TestWebSocket extends TomcatBaseTest {

    @Test
    public void testSimple() {
        // TODO: Write a test
    }

    private static final class StreamingWebSocketServlet
            extends WebSocketServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected StreamInbound createWebSocketInbound() {
            return new SimpleStreamInbound();
        }
    }

    private static final class SimpleStreamInbound extends StreamInbound {

        @Override
        protected void onBinaryData(InputStream is) {
            // TODO Auto-generated method stub
        }

        @Override
        protected void onTextData(Reader r) {
            // TODO Auto-generated method stub
        }
    }


    private static final class MessageWebSocketServlet
            extends WebSocketServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected StreamInbound createWebSocketInbound() {
            return new SimpleMessageInbound();
        }
    }

    private static final class SimpleMessageInbound extends MessageInbound {

        @Override
        protected void onBinaryMessage(ByteBuffer message) {
            // TODO Auto-generated method stub
        }

        @Override
        protected void onTextMessage(CharBuffer message) {
            // TODO Auto-generated method stub
        }
    }
}
