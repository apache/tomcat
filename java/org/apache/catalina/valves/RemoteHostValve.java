/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.valves;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Concrete implementation of <code>RequestFilterValve</code> that filters
 * based on the remote client's host name optionally combined with the
 * server connector port number.
 *
 * @author Craig R. McClanahan
 */
public final class RemoteHostValve extends RequestFilterValve {

    private static final Log log = LogFactory.getLog(RemoteHostValve.class);


    // --------------------------------------------------------- Public Methods

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        String property;
        if (getAddConnectorPort()) {
            property = request.getRequest().getRemoteHost() + ";" +
                    request.getConnector().getPortWithOffset();
        } else {
            property = request.getRequest().getRemoteHost();
        }
        process(property, request, response);
    }


    @Override
    protected Log getLog() {
        return log;
    }
}
