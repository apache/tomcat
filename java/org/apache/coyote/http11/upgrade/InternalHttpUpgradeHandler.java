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
package org.apache.coyote.http11.upgrade;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;


/**
 * Currently just a marker interface to enable Tomcat to identify
 * implementations that expect/require concurrent read/write support.
 *
 * Note that concurrent read/write support is being phased out and this
 * interface is expected to evolve into an interface internal handlers use to
 * gain direct access to Tomcat's I/O layer rather than going through the
 * Servlet API.
 */
public interface InternalHttpUpgradeHandler extends HttpUpgradeHandler {

    SocketState upgradeDispatch(SocketStatus status);

    void setSocketWrapper(SocketWrapperBase<?> wrapper);
}