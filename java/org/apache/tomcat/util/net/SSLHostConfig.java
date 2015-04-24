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
package org.apache.tomcat.util.net;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class SSLHostConfig {

    private static final Log log = LogFactory.getLog(SSLHostConfig.class);
    private static final StringManager sm = StringManager.getManager(SSLHostConfig.class);

    public static final String DEFAULT_SSL_HOST_NAME = "_default_";

    private Type configType = null;
    private Map<Type,Set<String>> configuredProperties = new HashMap<>();

    private String hostName = DEFAULT_SSL_HOST_NAME;

    // Common
    private Set<String> protocols = new HashSet<>();
    // JSSE
    private String keystoreFile = System.getProperty("user.home")+"/.keystore";
    // OpenSSL
    private String certificateFile;
    private String certificateKeyFile;

    public SSLHostConfig() {
        // Set defaults that can't be (easily) set when defining the fields.
        setProtocols("all");
    }


    public void setConfigType(Type configType) {
        this.configType = configType;
        configuredProperties.remove(configType);
        for (Map.Entry<Type,Set<String>> entry : configuredProperties.entrySet()) {
            for (String property : entry.getValue()) {
                log.warn(sm.getString("sslHostConfig.mismatch",
                        property, getHostName(), entry.getKey(), configType));
            }
        }
    }


    private void setProperty(String name, Type configType) {
        if (this.configType == null) {
            Set<String> properties = configuredProperties.get(configType);
            if (properties == null) {
                properties = new HashSet<>();
                configuredProperties.put(configType, properties);
            }
            properties.add(name);
        } else {
            if (configType != this.configType) {
                log.warn(sm.getString("sslHostConfig.mismatch",
                        name, getHostName(), configType, this.configType));
            }
        }
    }


    // ----------------------------------------- Common configuration properties

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }


    public String getHostName() {
        return hostName;
    }


    public void setProtocols(String input) {
        // OpenSSL and JSSE use the same names.
        String[] values = input.split(",|\\+");

        protocols.clear();

        for (String value: values) {
            String trimmed = value.trim();
            if (trimmed.length() > 0) {
                if (input.trim().equalsIgnoreCase("all")) {
                    protocols.add("TLSv1");
                    protocols.add("TLSv1.1");
                    protocols.add("TLSv1.2");
                } else {
                    protocols.add(trimmed);
                }
            }
        }
    }


    public Set<String> getProtocols() {
        return protocols;
    }


    // ---------------------------------- JSSE specific configuration properties

    public void setKeystoreFile(String keystoreFile) {
        setProperty("keystoreFile", Type.JSSE);
        this.keystoreFile = keystoreFile;
    }


    public String getKeystoreFile() {
        return keystoreFile;
    }


    // ------------------------------- OpenSSL specific configuration properties

    public void setCertificateFile(String certificateFile) {
        setProperty("certificateFile", Type.OPENSSL);
        this.certificateFile = certificateFile;
    }


    public String getCertificateFile() {
        return certificateFile;
    }


    public void setCertificateKeyFile(String certificateKeyFile) {
        setProperty("certificateKeyFile", Type.OPENSSL);
        this.certificateKeyFile = certificateKeyFile;
    }


    public String getCertificateKeyFile() {
        return certificateKeyFile;
    }


    // ----------------------------------------------------------- Inner classes

    public static enum Type {
        JSSE,
        OPENSSL
    }
}
