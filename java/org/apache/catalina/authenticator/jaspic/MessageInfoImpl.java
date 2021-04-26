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
package org.apache.catalina.authenticator.jaspic;

import java.util.HashMap;
import java.util.Map;

import jakarta.security.auth.message.MessageInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.res.StringManager;

public class MessageInfoImpl implements MessageInfo {
    protected static final StringManager sm = StringManager.getManager(MessageInfoImpl.class);

    public static final String IS_MANDATORY = "jakarta.security.auth.message.MessagePolicy.isMandatory";

    private final Map<String, Object> map = new HashMap<>();
    private HttpServletRequest request;
    private HttpServletResponse response;

    public MessageInfoImpl() {
    }

    public MessageInfoImpl(HttpServletRequest request, HttpServletResponse response, boolean authMandatory) {
        this.request = request;
        this.response = response;
        map.put(IS_MANDATORY, Boolean.toString(authMandatory));
    }

    @Override
    @SuppressWarnings("rawtypes")
    // JASPIC uses raw types
    public Map getMap() {
        return map;
    }

    @Override
    public Object getRequestMessage() {
        return request;
    }

    @Override
    public Object getResponseMessage() {
        return response;
    }

    @Override
    public void setRequestMessage(Object request) {
        if (!(request instanceof HttpServletRequest)) {
            throw new IllegalArgumentException(sm.getString("authenticator.jaspic.badRequestType",
                    request.getClass().getName()));
        }
        this.request = (HttpServletRequest) request;
    }

    @Override
    public void setResponseMessage(Object response) {
        if (!(response instanceof HttpServletResponse)) {
            throw new IllegalArgumentException(sm.getString("authenticator.jaspic.badResponseType",
                    response.getClass().getName()));
        }
        this.response = (HttpServletResponse) response;
    }
}