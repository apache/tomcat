/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.authenticator.jaspic;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.message.AuthException;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;

import org.junit.Assert;
import org.junit.Test;

public class TestSimpleServerAuthConfig {

    private static final String SERVER_AUTH_MODULE_KEY_PREFIX =
            "org.apache.catalina.authenticator.jaspic.ServerAuthModule.";

    private static final Map<String,String> CONFIG_PROPERTIES;
    static {
        CONFIG_PROPERTIES = new HashMap<>();
        CONFIG_PROPERTIES.put(SERVER_AUTH_MODULE_KEY_PREFIX + "1",
                TesterServerAuthModuleA.class.getName());
    }

    @Test
    public void testConfigOnServerAuthConfig() throws Exception {
        ServerAuthConfig serverAuthConfig =
                new SimpleServerAuthConfig(null,  null, null, CONFIG_PROPERTIES);
        ServerAuthContext serverAuthContext = serverAuthConfig.getAuthContext(null, null, null);

        validateServerAuthContext(serverAuthContext);
    }


    @Test
    public void testConfigOnGetAuthContext() throws Exception {
        ServerAuthConfig serverAuthConfig = new SimpleServerAuthConfig(null,  null, null, null);
        ServerAuthContext serverAuthContext =
                serverAuthConfig.getAuthContext(null, null, CONFIG_PROPERTIES);

        validateServerAuthContext(serverAuthContext);
    }


    @Test(expected=AuthException.class)
    public void testConfigNone() throws Exception {
        ServerAuthConfig serverAuthConfig = new SimpleServerAuthConfig(null,  null, null, null);
        serverAuthConfig.getAuthContext(null, null, null);
    }


    private void validateServerAuthContext(ServerAuthContext serverAuthContext) throws Exception {
        MessageInfo msgInfo = new TesterMessageInfo();
        serverAuthContext.cleanSubject(msgInfo, null);
        Assert.assertEquals("init()-cleanSubject()-", msgInfo.getMap().get("trace"));
    }
}
