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

/**
 * This enumeration lists the different types of dispatches that request
 * processing can trigger. In this instance, dispatch means re-process this
 * request using the given socket status.
 */
public enum DispatchType {

    NON_BLOCKING_READ(SocketStatus.OPEN_READ),
    NON_BLOCKING_WRITE(SocketStatus.OPEN_WRITE);

    private final SocketStatus status;

    private DispatchType(SocketStatus status) {
        this.status = status;
    }

    public SocketStatus getSocketStatus() {
        return status;
    }
}
