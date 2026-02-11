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
import org.apache.tomcat.util.net.AprEndpoint;


/**
 * HTTP/1.1 protocol implementation using APR/native.
 *
 * @deprecated The APR/Native Connector will be removed in Tomcat 9.1.x onwards and has been removed from Tomcat 10.1.x
 *                 onwards.
 */
@Deprecated
public class Http11AprProtocol extends AbstractHttp11Protocol<Long> {

    private static final Log log = LogFactory.getLog(Http11AprProtocol.class);

    @Deprecated
    public Http11AprProtocol() {
        this(new AprEndpoint());
    }


    @Deprecated
    public Http11AprProtocol(AprEndpoint endpoint) {
        super(endpoint);
    }


    @Deprecated
    @Override
    protected Log getLog() {
        return log;
    }

    @Deprecated
    @Override
    public boolean isAprRequired() {
        // Override since this protocol implementation requires the APR/native
        // library
        return true;
    }

    @Deprecated
    public int getPollTime() {
        return ((AprEndpoint) getEndpoint()).getPollTime();
    }

    @Deprecated
    public void setPollTime(int pollTime) {
        ((AprEndpoint) getEndpoint()).setPollTime(pollTime);
    }

    @Deprecated
    public int getSendfileSize() {
        return ((AprEndpoint) getEndpoint()).getSendfileSize();
    }

    @Deprecated
    public void setSendfileSize(int sendfileSize) {
        ((AprEndpoint) getEndpoint()).setSendfileSize(sendfileSize);
    }

    @Deprecated
    public boolean getDeferAccept() {
        return ((AprEndpoint) getEndpoint()).getDeferAccept();
    }

    @Deprecated
    public void setDeferAccept(boolean deferAccept) {
        ((AprEndpoint) getEndpoint()).setDeferAccept(deferAccept);
    }



    @Override
    public void init() throws Exception {
        super.init();

        if (isSSLEnabled()) {
            log.info(sm.getString("ajpHttpsProtocol.deprecated", getName()));
        } else {
            log.info(sm.getString("ajpHttpProtocol.deprecated", getName()));
        }
    }


    @Deprecated
    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return "https-openssl-apr";
        } else {
            return "http-apr";
        }
    }
}
