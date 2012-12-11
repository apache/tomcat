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
package websocket.echo;

import java.io.IOException;

import javax.websocket.Endpoint;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

public class EchoEndpoint extends Endpoint{

    @Override
    public void onOpen(Session session) {
        RemoteEndpoint remoteEndpoint = session.getRemote();
        session.addMessageHandler(new EchoMessageHandler<>(remoteEndpoint));
    }

    private static class EchoMessageHandler<T>
            implements javax.websocket.MessageHandler.Basic<String> {

        private final RemoteEndpoint remoteEndpoint;

        private EchoMessageHandler(RemoteEndpoint remoteEndpoint) {
            this.remoteEndpoint = remoteEndpoint;
        }

        @Override
        public void onMessage(String message) {
            try {
                System.out.println(message);
                if (remoteEndpoint != null) {
                    remoteEndpoint.sendString(message);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
