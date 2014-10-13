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

    // Version 0 (Netscape) attributes
    private final MessageBytes name=MessageBytes.newInstance();
    private final MessageBytes value=MessageBytes.newInstance();
    // Expires - Not stored explicitly. Generated from Max-Age (see V1)
    private final MessageBytes path=MessageBytes.newInstance();
    private final MessageBytes domain=MessageBytes.newInstance();

    // Version 1 (RFC2109) attributes
    private final MessageBytes comment=MessageBytes.newInstance();
    private int version = 0;

    // Note: Servlet Spec =< 3.0 only refers to Netscape and RFC2109, not RFC2965

    // Version 2 (RFC2965) attributes that would need to be added to support v2 cookies
    // CommentURL
    // Discard - implied by maxAge <0
    // Port

    public ServerCookie() {
        // NOOP
    }

    public void recycle() {
        path.recycle();
        name.recycle();
        value.recycle();
        comment.recycle();
        path.recycle();
        domain.recycle();
        version=0;
    }

    public MessageBytes getComment() {
        return comment;
    }

    public MessageBytes getDomain() {
        return domain;
    }

    public MessageBytes getPath() {
        return path;
    }

    public MessageBytes getName() {
        return name;
    }

    public MessageBytes getValue() {
        return value;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int v) {
        version = v;
    }


    // -------------------- utils --------------------

    @Override
    public String toString() {
        return "Cookie " + getName() + "=" + getValue() + " ; "
            + getVersion() + " " + getPath() + " " + getDomain();
    }
}

