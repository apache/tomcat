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
package org.apache.catalina.authenticator.jaspic.provider.modules;

import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.module.ServerAuthModule;

import org.apache.catalina.authenticator.jaspic.MessageInfoImpl;
import org.apache.tomcat.util.res.StringManager;

public abstract class TomcatAuthModule implements ServerAuthModule {

    protected static final String AUTH_HEADER_NAME = "WWW-Authenticate";
    protected static final String AUTHORIZATION_HEADER = "authorization";
    /**
     * Default authentication realm name.
     */
    protected static final String REALM_NAME = "Authentication required";
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(TomcatAuthModule.class);


    public abstract String getAuthenticationType();


    protected boolean isMandatory(MessageInfo messageInfo) {
        String mandatory = (String) messageInfo.getMap().get(MessageInfoImpl.IS_MANDATORY);
        return Boolean.parseBoolean(mandatory);
    }


    protected static String getRealmName(MessageInfo messageInfo) {
        if (messageInfo == null) {
            return REALM_NAME;
        }
        // TODO get realm name from message
        return REALM_NAME;
    }
}
