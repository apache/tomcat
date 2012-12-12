/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.websocket.EncodeException;

public abstract class PojoMessageHandlerBase<T> {

    protected final Object pojo;
    protected final Method method;
    protected final WsSession wsSession;


    public PojoMessageHandlerBase(Object pojo, Method method,
            WsSession wsSession) {
        this.pojo = pojo;
        this.method = method;
        this.wsSession = wsSession;
    }


    protected void processResult(Object result) {
        try {
            if (result instanceof String) {
                wsSession.getRemote().sendString((String) result);
            } else if (result instanceof ByteBuffer) {
                wsSession.getRemote().sendBytes((ByteBuffer) result);
            } else if (result instanceof byte[]) {
                wsSession.getRemote().sendBytes(
                        ByteBuffer.wrap((byte[]) result));
            } else if (result != null) {
                wsSession.getRemote().sendObject(result);
            }
        } catch (IOException | EncodeException ioe) {
            throw new IllegalStateException(ioe);
        }
    }
}
