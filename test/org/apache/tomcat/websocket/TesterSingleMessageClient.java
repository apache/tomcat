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
package org.apache.tomcat.websocket;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;

public class TesterSingleMessageClient {

    public static class TesterProgrammaticEndpoint extends Endpoint {

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            clearLatch(session);
        }

        @Override
        public void onError(Session session, Throwable throwable) {
            clearLatch(session);
        }

        private void clearLatch(Session session) {
            CountDownLatch latch =
                    (CountDownLatch) session.getUserProperties().get("latch");
            if (latch != null) {
                while (latch.getCount() > 0) {
                    latch.countDown();
                }
            }
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            // NO-OP
        }
    }

    @ClientEndpoint
    public static class TesterAnnotatedEndpoint {

        @OnClose
        public void onClose(Session session) {
            clearLatch(session);
        }

        @OnError
        public void onError(Session session,
                @SuppressWarnings("unused") Throwable throwable) {
            clearLatch(session);
        }

        private void clearLatch(Session session) {
            CountDownLatch latch =
                    (CountDownLatch) session.getUserProperties().get("latch");
            if (latch != null) {
                while (latch.getCount() > 0) {
                    latch.countDown();
                }
            }
        }

        @OnOpen
        public void onOpen() {
            // NO-OP
        }
    }


    public abstract static class BasicHandler<T>
            implements MessageHandler.Whole<T> {

        private final CountDownLatch latch;

        private final List<T> messages = new CopyOnWriteArrayList<>();

        public BasicHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        public List<T> getMessages() {
            return messages;
        }
    }

    public static class BasicBinary extends BasicHandler<ByteBuffer> {

        public BasicBinary(CountDownLatch latch) {
            super(latch);
        }

        @Override
        public void onMessage(ByteBuffer message) {
            getMessages().add(message);
            if (getLatch() != null) {
                getLatch().countDown();
            }
        }
    }

    public static class BasicText extends BasicHandler<String> {


        public BasicText(CountDownLatch latch) {
            super(latch);
        }

        @Override
        public void onMessage(String message) {
            getMessages().add(message);
            if (getLatch() != null) {
                getLatch().countDown();
            }
        }
    }

    public abstract static class AsyncHandler<T>
            implements MessageHandler.Partial<T> {

        private final CountDownLatch latch;

        private final List<T> messages = new CopyOnWriteArrayList<>();

        public AsyncHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        public List<T> getMessages() {
            return messages;
        }
    }

    public static class AsyncBinary extends AsyncHandler<ByteBuffer> {

        public AsyncBinary(CountDownLatch latch) {
            super(latch);
        }

        @Override
        public void onMessage(ByteBuffer message, boolean last) {
            getMessages().add(message);
            if (last && getLatch() != null) {
                getLatch().countDown();
            }
        }
    }

    public static class AsyncText extends AsyncHandler<String> {


        public AsyncText(CountDownLatch latch) {
            super(latch);
        }

        @Override
        public void onMessage(String message, boolean last) {
            getMessages().add(message);
            if (last && getLatch() != null) {
                getLatch().countDown();
            }
        }
    }
}
