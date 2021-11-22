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
package org.apache.tomcat.util.http;

import java.io.Serializable;

import org.apache.tomcat.util.buf.MessageBytes;


/**
 *  Server-side cookie representation.
 *  Allows recycling and uses MessageBytes as low-level
 *  representation ( and thus the byte -&gt; char conversion can be delayed
 *  until we know the charset ).
 *
 *  Tomcat.core uses this recyclable object to represent cookies,
 *  and the facade will convert it to the external representation.
 */
public class ServerCookie implements Serializable {

    private static final long serialVersionUID = 1L;

    // RFC 6265
    private final MessageBytes name=MessageBytes.newInstance();
    private final MessageBytes value=MessageBytes.newInstance();

    public ServerCookie() {
        // NOOP
    }

    public void recycle() {
        name.recycle();
        value.recycle();
    }

    public MessageBytes getName() {
        return name;
    }

    public MessageBytes getValue() {
        return value;
    }


    // -------------------- utils --------------------

    @Override
    public String toString() {
        return "Cookie " + getName() + "=" + getValue();
    }
}

