/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.authenticator.jaspic;

import java.util.HashMap;
import java.util.Map;

import jakarta.security.auth.message.MessageInfo;

public class TesterMessageInfo implements MessageInfo {

    private Object requestMessage;
    private Object responseMessage;
    private final Map<String,String> map = new HashMap<>();

    @Override
    public Object getRequestMessage() {
        return requestMessage;
    }

    @Override
    public Object getResponseMessage() {
        return responseMessage;
    }

    @Override
    public void setRequestMessage(Object request) {
        requestMessage = request;
    }

    @Override
    public void setResponseMessage(Object response) {
        responseMessage = response;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Map getMap() {
        return map;
    }
}
