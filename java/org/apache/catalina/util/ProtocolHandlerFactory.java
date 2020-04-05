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
package org.apache.catalina.util;

import org.apache.catalina.core.AprLifecycleListener;
import org.apache.coyote.ProtocolHandler;

import java.lang.reflect.InvocationTargetException;

public class ProtocolHandlerFactory {

    public static ProtocolHandler createProtocolHandler(String protocol) throws
        ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
        InvocationTargetException, InstantiationException {

        boolean apr = AprLifecycleListener.isAprAvailable() && AprLifecycleListener.getUseAprConnector();

        if ("HTTP/1.1".equals(protocol) || protocol == null) {
            protocol = apr ?
                "org.apache.coyote.http11.Http11AprProtocol" :
                "org.apache.coyote.http11.Http11NioProtocol";
        } else if ("AJP/1.3".equals(protocol)) {
            protocol = apr ?
                "org.apache.coyote.ajp.AjpAprProtocol" :
                "org.apache.coyote.ajp.AjpNioProtocol";

        }

        // Instantiate protocol handler
        Class<?> clazz = Class.forName(protocol);
        return (ProtocolHandler) clazz.getConstructor().newInstance();
    }
}
