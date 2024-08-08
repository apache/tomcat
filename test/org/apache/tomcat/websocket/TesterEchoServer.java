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

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletContextEvent;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;

import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.server.WsContextListener;

public class TesterEchoServer {

    public static class Config extends WsContextListener {

        public static final String PATH_ASYNC = "/echoAsync";
        public static final String PATH_BASIC = "/echoBasic";
        public static final String PATH_BASIC_LIMIT_LOW = "/echoBasicLimitLow";
        public static final String PATH_BASIC_LIMIT_HIGH = "/echoBasicLimitHigh";
        public static final String PATH_WRITER_ERROR = "/echoWriterError";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);
            ServerContainer sc = (ServerContainer) sce.getServletContext()
                    .getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            try {
                sc.addEndpoint(Async.class);
                sc.addEndpoint(Basic.class);
                sc.addEndpoint(BasicLimitLow.class);
                sc.addEndpoint(BasicLimitHigh.class);
                sc.addEndpoint(WriterError.class);
                sc.addEndpoint(RootEcho.class);
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    @ServerEndpoint("/echoAsync")
    public static class Async {

        @OnMessage
        public void echoTextMessage(Session session, String msg, boolean last) {
            try {
                session.getBasicRemote().sendText(msg, last);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @OnMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg, boolean last) {
            try {
                session.getBasicRemote().sendBinary(msg, last);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }


    @ServerEndpoint("/echoBasic")
    public static class Basic {
        @OnMessage
        public void echoTextMessage(Session session, String msg) {
            try {
                session.getBasicRemote().sendText(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @OnMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg) {
            try {
                session.getBasicRemote().sendBinary(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }


    @ServerEndpoint("/echoBasicLimitLow")
    public static class BasicLimitLow {

        public static final long MAX_SIZE = 10;

        @OnMessage(maxMessageSize = MAX_SIZE)
        public void echoTextMessage(Session session, String msg) {
            try {
                session.getBasicRemote().sendText(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @OnMessage(maxMessageSize = MAX_SIZE)
        public void echoBinaryMessage(Session session, ByteBuffer msg) {
            try {
                session.getBasicRemote().sendBinary(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }


    @ServerEndpoint("/echoBasicLimitHigh")
    public static class BasicLimitHigh {

        public static final long MAX_SIZE = 32 * 1024;

        @OnMessage(maxMessageSize = MAX_SIZE)
        public void echoTextMessage(Session session, String msg) {
            try {
                session.getBasicRemote().sendText(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @OnMessage(maxMessageSize = MAX_SIZE)
        public void echoBinaryMessage(Session session, ByteBuffer msg) {
            try {
                session.getBasicRemote().sendBinary(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }


    @ServerEndpoint("/echoWriterError")
    public static class WriterError {

        public static final String MSG_ERROR = "error";
        public static final String MSG_COUNT = "count";
        public static final String RESULT_PASS = "PASS";
        public static final String RESULT_FAIL = "FAIL";

        private AtomicInteger errorCount = new AtomicInteger(0);

        @OnMessage
        public void echoTextMessage(Session session, String msg) {
            try (Writer w = session.getBasicRemote().getSendWriter()) {
                if (MSG_ERROR.equals(msg)) {
                    // Simulate an error
                    throw new RuntimeException();
                } else if (MSG_COUNT.equals(msg)) {
                    int count = 0;
                    while (count < 200 && errorCount.get() == 0) {
                        // 200 * 50 == 10,000ms == 10s
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        count++;
                    }
                    if (errorCount.get() == 1) {
                        w.write(RESULT_PASS);
                    } else {
                        w.write(RESULT_FAIL);
                    }
                } else {
                    // Default is echo
                   w.write(msg);
                }
            } catch (IOException e) {
                // Should not happen
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @OnError
        public void onError(@SuppressWarnings("unused") Throwable t) {
            errorCount.incrementAndGet();
        }
    }

    @ServerEndpoint("/")
    public static class RootEcho {

        @OnMessage
        public void echoTextMessage(Session session, String msg) {
            try {
                session.getBasicRemote().sendText(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }
}
