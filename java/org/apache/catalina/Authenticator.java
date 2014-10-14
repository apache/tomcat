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
package org.apache.catalina;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;


/**
 * An <b>Authenticator</b> is a component (usually a Valve or Container) that
 * provides some sort of authentication service.
 *
 * @author Craig R. McClanahan
 */
public interface Authenticator {

    /**
     * Authenticate the user making this request, based on the login
     * configuration of the {@link Context} with which this Authenticator is
     * associated.
     *
     * @param request Request we are processing
     * @param response Response we are populating
     *
     * @return <code>true</code> if any specified constraints have been
     *         satisfied, or <code>false</code> if one more constraints were not
     *         satisfied (in which case an authentication challenge will have
     *         been written to the response).
     *
     * @exception IOException if an input/output error occurs
     */
    public boolean authenticate(Request request, HttpServletResponse response)
            throws IOException;

    public void login(String userName, String password, Request request)
            throws ServletException;

    public void logout(Request request);
}
