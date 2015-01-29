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

import java.io.IOException;

import javax.net.ssl.SSLEngine;

import org.apache.coyote.ActionCode;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SecureNio2Channel;


/**
 * Processes HTTP requests.
 */
public class Http11Nio2Processor extends AbstractHttp11Processor<Nio2Channel> {

    private static final Log log = LogFactory.getLog(Http11Nio2Processor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    // ----------------------------------------------------------- Constructors

    public Http11Nio2Processor(int maxHttpHeaderSize, AbstractEndpoint<Nio2Channel> endpoint,
            int maxTrailerSize, int maxExtensionSize, int maxSwallowSize) {

        super(maxHttpHeaderSize, endpoint, maxTrailerSize, maxExtensionSize, maxSwallowSize);
    }


    // ----------------------------------------------------- ActionHook Methods

    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @Override
    @SuppressWarnings("incomplete-switch") // Other cases are handled by action()
    public void actionInternal(ActionCode actionCode, Object param) {

        switch (actionCode) {
        case REQ_SSL_CERTIFICATE: {
            if (sslSupport != null && socketWrapper.getSocket() != null) {
                /*
                 * Consume and buffer the request body, so that it does not
                 * interfere with the client's handshake messages
                 */
                InputFilter[] inputFilters = getInputBuffer().getFilters();
                ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER])
                    .setLimit(maxSavePostSize);
                getInputBuffer().addActiveFilter
                    (inputFilters[Constants.BUFFERED_FILTER]);
                SecureNio2Channel sslChannel = (SecureNio2Channel) socketWrapper.getSocket();
                SSLEngine engine = sslChannel.getSslEngine();
                if (!engine.getNeedClientAuth()) {
                    // Need to re-negotiate SSL connection
                    engine.setNeedClientAuth(true);
                    try {
                        sslChannel.rehandshake();
                        sslSupport = ((Nio2Endpoint)endpoint).getHandler()
                                .getSslImplementation().getSSLSupport(
                                        engine.getSession());
                    } catch (IOException ioe) {
                        log.warn(sm.getString("http11processor.socket.sslreneg"), ioe);
                    }
                }

                try {
                    // use force=false since re-negotiation is handled above
                    // (and it is a NO-OP for NIO anyway)
                    Object sslO = sslSupport.getPeerCertificateChain(false);
                    if( sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }
            break;
        }
        }
    }
}
