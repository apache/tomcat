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
package websocket.drawboard;

import java.util.LinkedList;

import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import websocket.drawboard.wsmessages.AbstractWebsocketMessage;
import websocket.drawboard.wsmessages.BinaryWebsocketMessage;
import websocket.drawboard.wsmessages.StringWebsocketMessage;

/**
 * Represents a client with methods to send messages.
 */
public class Client {

    private final RemoteEndpoint.Async async;

    /**
     * Contains the messages wich are buffered until the previous
     * send operation has finished.
     */
    private final LinkedList<AbstractWebsocketMessage> messagesToSend =
            new LinkedList<>();
    /**
     * If this client is currently sending a messages asynchronously.
     */
    private volatile boolean isSendingMessage = false;

    public Client(RemoteEndpoint.Async async) {
        this.async = async;
    }


    /**
     * Sends the given message asynchronously to the client.
     * If there is already a async sending in progress, then the message
     * will be buffered and sent when possible.<br><br>
     * 
     * This method can be called from multiple threads.
     * @param msg
     */
    public void sendMessage(AbstractWebsocketMessage msg) {
        synchronized (messagesToSend) {
            if (isSendingMessage) {
                // TODO: Check if the buffered messages exceed
                // a specific amount - in that case, disconnect the client
                // to prevent DoS.

                // TODO: Check if the last message is a
                // String message - in that case we should concatenate them
                // to reduce TCP overhead (using ";" as separator).

                messagesToSend.add(msg);
            } else {
                isSendingMessage = true;
                internalSendMessageAsync(msg);
            }


        }
    }

    /**
     * Internally sends the messages asynchronously.
     * @param msg
     */
    private void internalSendMessageAsync(AbstractWebsocketMessage msg) {
        try {
            if (msg instanceof StringWebsocketMessage) {
                StringWebsocketMessage sMsg = (StringWebsocketMessage) msg;
                async.sendText(sMsg.getString(), sendHandler);

            } else if (msg instanceof BinaryWebsocketMessage) {
                BinaryWebsocketMessage bMsg = (BinaryWebsocketMessage) msg;
                async.sendBinary(bMsg.getBytes(), sendHandler);
            }
        } catch (IllegalStateException ex) {
            // Trying to write to the client when the session has
            // already been closed.
            // Ignore
        }
    }



    /**
     * SendHandler that will continue to send buffered messages.
     */
    private final SendHandler sendHandler = new SendHandler() {
        @Override
        public void onResult(SendResult result) {
            synchronized (messagesToSend) {

                if (!messagesToSend.isEmpty()) {
                    AbstractWebsocketMessage msg = messagesToSend.remove();
                    internalSendMessageAsync(msg);

                } else {
                    isSendingMessage = false;
                }

            }
        }
    };

}
