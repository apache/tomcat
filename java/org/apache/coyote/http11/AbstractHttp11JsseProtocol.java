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
package org.apache.coyote.http11;

import org.apache.tomcat.util.net.AbstractEndpoint;

public abstract class AbstractHttp11JsseProtocol<S>
        extends AbstractHttp11Protocol<S> {

    public AbstractHttp11JsseProtocol(AbstractEndpoint<S> endpoint) {
        super(endpoint);
    }

    public String getSslProtocol() { return getEndpoint().getSslProtocol();}
    public void setSslProtocol(String s) { getEndpoint().setSslProtocol(s);}

    public void setSessionCacheSize(String s){getEndpoint().setSessionCacheSize(s);}
    public String getSessionCacheSize(){ return getEndpoint().getSessionCacheSize();}

    public void setSessionTimeout(String s){getEndpoint().setSessionTimeout(s);}
    public String getSessionTimeout(){ return getEndpoint().getSessionTimeout();}

    public String getSslImplementationName() { return getEndpoint().getSslImplementationName(); }
    public void setSslImplementationName(String s) { getEndpoint().setSslImplementationName(s); }
}
