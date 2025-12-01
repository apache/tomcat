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
package org.apache.catalina.storeconfig;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.tomcat.util.net.openssl.OpenSSLConf;
import org.apache.tomcat.util.net.openssl.OpenSSLConfCmd;

/**
 * Store OpenSSLConf
 */
public class OpenSSLConfSF extends StoreFactoryBase {

    private static final Set<String> INTERNAL_COMMANDS = new HashSet<>(Arrays.asList(OpenSSLConfCmd.NO_OCSP_CHECK));

    /**
     * Store nested OpenSSLConfCmd elements.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aOpenSSLConf, StoreDescription parentDesc)
            throws Exception {
        if (aOpenSSLConf instanceof OpenSSLConf) {
            OpenSSLConf openSslConf = (OpenSSLConf) aOpenSSLConf;
            // Store nested <OpenSSLConfCmd> elements
            for (OpenSSLConfCmd openSSLConfCmd : openSslConf.getCommands()) {
                // Don't store the internal commands that are created from other SslHostConfig attributes.
                if (INTERNAL_COMMANDS.contains(openSSLConfCmd.getName())) {
                    continue;
                }
                storeElement(aWriter, indent + 2, openSSLConfCmd);
            }
        }
    }
}
