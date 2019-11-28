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
import java.util.concurrent.Semaphore;

import jakarta.websocket.OnMessage;
import jakarta.websocket.RemoteEndpoint.Async;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import org.apache.tomcat.websocket.server.TesterEndpointConfig;

public class TesterAsyncTiming {

    public static class Config extends TesterEndpointConfig {

        public static final String PATH = "/timing";
        public static final int ITERATIONS = 500;
        public static final int SLEEP_MILLI = 50;

        @Override
        protected Class<?> getEndpointClass() {
            return Endpoint.class;
        }
    }

    @ServerEndpoint(Config.PATH)
    public static class Endpoint {

        private static final ByteBuffer LARGE_DATA= ByteBuffer.allocate(16 * 1024);
        private static final ByteBuffer SMALL_DATA= ByteBuffer.allocate(4 * 1024);

        @OnMessage
        public void onMessage(Session session, @SuppressWarnings("unused") String text) {

            Semaphore semaphore = new Semaphore(1);
            SendHandler handler = new SemaphoreSendHandler(semaphore);

            Async remote = session.getAsyncRemote();
            for (int i = 0; i < Config.ITERATIONS; i++) {
                try {
                    semaphore.acquire(1);
                    remote.sendBinary(LARGE_DATA, handler);
                    semaphore.acquire(1);
                    remote.sendBinary(SMALL_DATA, handler);
                    Thread.sleep(Config.SLEEP_MILLI);
                    LARGE_DATA.flip();
                    SMALL_DATA.flip();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        private static class SemaphoreSendHandler implements SendHandler {

            private final Semaphore semaphore;

            private SemaphoreSendHandler(Semaphore semaphore) {
                this.semaphore = semaphore;
            }

            @Override
            public void onResult(SendResult result) {
                semaphore.release();
            }
        }
    }
}
