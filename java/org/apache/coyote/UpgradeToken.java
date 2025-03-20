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
package org.apache.coyote;

import jakarta.servlet.http.HttpUpgradeHandler;

import org.apache.tomcat.ContextBind;
import org.apache.tomcat.InstanceManager;

/**
 * Token used during the upgrade process.
 *
 * @param httpUpgradeHandler The handler for the HTTP upgrade
 * @param contextBind        The object to use to bind/unbind the current thread to/from the web application class
 *                               loader
 * @param instanceManager    The instance manager to use to create new Servlets, Filters, Listeners etc
 * @param protocol           The desired protocol to upgrade to
 */
public record UpgradeToken(HttpUpgradeHandler httpUpgradeHandler, ContextBind contextBind,
                           InstanceManager instanceManager, String protocol) {

    public ContextBind getContextBind() {
        return contextBind;
    }

    public HttpUpgradeHandler getHttpUpgradeHandler() {
        return httpUpgradeHandler;
    }

    public InstanceManager getInstanceManager() {
        return instanceManager;
    }

    public String getProtocol() {
        return protocol;
    }

}
