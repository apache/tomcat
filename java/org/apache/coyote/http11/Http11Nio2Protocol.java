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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.Nio2Endpoint.Handler;


/**
 * HTTP/1.1 protocol implementation using NIO2.
 */
public class Http11Nio2Protocol extends AbstractHttp11JsseProtocol<Nio2Channel> {

    private static final Log log = LogFactory.getLog(Http11Nio2Protocol.class);


    public Http11Nio2Protocol() {
        super(new Nio2Endpoint());
        Http11ConnectionHandler cHandler = new Http11ConnectionHandler(this);
        setHandler(cHandler);
        ((Nio2Endpoint) getEndpoint()).setHandler(cHandler);
    }


    @Override
    protected Log getLog() { return log; }


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return ("https-nio2");
        } else {
            return ("http-nio2");
        }
    }


    // --------------------  Connection handler --------------------

    protected static class Http11ConnectionHandler
            extends AbstractHttp11ConnectionHandler<Nio2Channel>
            implements Handler {

        Http11ConnectionHandler(Http11Nio2Protocol proto) {
            super(proto);
        }

        @Override
        protected Log getLog() {
            return log;
        }
    }
}
