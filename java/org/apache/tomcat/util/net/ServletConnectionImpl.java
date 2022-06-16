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
package org.apache.tomcat.util.net;

import jakarta.servlet.ServletConnection;


public class ServletConnectionImpl implements ServletConnection {

    private final String connectionId;
    private final String protocol;
    private final String protocolConnectionId;
    private final boolean secure;

    public ServletConnectionImpl(String connectionId, String protocol, String protocolConnectionId, boolean secure) {
        this.connectionId = connectionId;
        this.protocol = protocol;
        this.protocolConnectionId = protocolConnectionId;
        this.secure = secure;
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getProtocolConnectionId() {
        return protocolConnectionId;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }
}
