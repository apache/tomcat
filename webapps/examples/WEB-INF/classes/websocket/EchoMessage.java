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
package websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import org.apache.catalina.websocket.MessageInbound;
import org.apache.catalina.websocket.StreamInbound;
import org.apache.catalina.websocket.WebSocketServlet;


public class EchoMessage extends WebSocketServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected StreamInbound createWebSocketInbound() {
        return new EchoMessageInbound();
    }

    private static final class EchoMessageInbound extends MessageInbound {

        @Override
        protected void onBinaryMessage(ByteBuffer message) throws IOException {
            System.out.write(message.array(), 0, message.limit());
            System.out.print('\n');
        }

        @Override
        protected void onTextMessage(CharBuffer message) throws IOException {
            System.out.println(message);
        }
    }
}
