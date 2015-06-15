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

import javax.security.auth.message.MessageInfo;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;

public class MessageInfoImpl implements MessageInfo {
    private static final String IS_MANDATORY = "javax.security.auth.message.MessagePolicy.isMandatory";

    private final Map<String, Object> map = new HashMap<>();
    private HttpServletRequest request;
    private HttpServletResponse response;

    public MessageInfoImpl() {
    }

    public MessageInfoImpl(Request request, HttpServletResponse response, boolean authMandatory) {
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
            throw new IllegalArgumentException("Request is not a servlet request but "
                    + request.getClass().getName());
        }
        this.request = (HttpServletRequest) request;
    }

    @Override
    public void setResponseMessage(Object response) {
        if (!(response instanceof HttpServletResponse)) {
            throw new IllegalArgumentException("response is not a servlet response but "
                    + response.getClass().getName());
        }
        this.response = (HttpServletResponse) response;
    }
}