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
package org.apache.tomcat.websocket;

import java.lang.reflect.Method;

import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketOpen;

public class PojoMethodMapping {

    private final Method onOpen;
    private final Method onClose;
    private final Method onError;

    public PojoMethodMapping(Class<?> clazzPojo, String path) {
        Method open = null;
        Method close = null;
        Method error = null;
        Method[] methods = clazzPojo.getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (open == null &&
                    methods[i].getAnnotation(WebSocketOpen.class) != null) {
                open = methods[i];
            } else if (close == null &&
                    methods[i].getAnnotation(WebSocketClose.class) != null) {
                close = methods[i];
            } else if (error == null &&
                    methods[i].getAnnotation(WebSocketError.class) != null) {
                error = methods[i];
            }
        }
        this.onOpen = open;
        this.onClose = close;
        this.onError = error;
    }

    public Method getOnOpen() {
        return onOpen;
    }

    public Object[] getOnOpenArgs(String path) {
        // TODO Auto-generated method stub
        return null;
    }

    public Method getOnClose() {
        return onClose;
    }

    public Object[] getOnCloseArgs(String path) {
        // TODO Auto-generated method stub
        return null;
    }

    public Method getOnError() {
        return onError;
    }

    public Object[] getOnErrorArgs(String path) {
        // TODO Auto-generated method stub
        return null;
    }
}
