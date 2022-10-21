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

import java.net.Socket;

import org.apache.coyote.Processor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.BioLoomEndpoint;

public class Http11BioLoomProtocol extends AbstractHttp11Protocol<Socket> {

    private static final Log log = LogFactory.getLog(Http11BioLoomProtocol.class);

    public Http11BioLoomProtocol() {
        super(new BioLoomEndpoint());
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return "https-" + getSslImplementationShortName()+ "-bio-loom";
        } else {
            return "http-bio-loom";
        }
    }


    @Override
    protected Processor createProcessor() {
        return new Http11LoomProcessor(this, adapter);
    }
}
