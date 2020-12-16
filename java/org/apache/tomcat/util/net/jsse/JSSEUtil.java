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
package org.apache.tomcat.util.net.jsse;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.compat.JreVendor;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtilBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * SSLUtil implementation for JSSE.
 *
 * @author Harish Prabandham
 * @author Costin Manolache
 * @author Stefan Freyr Stefansson
 * @author EKR
 * @author Jan Luehe
 */
public class JSSEUtil extends SSLUtilBase {

    private static final Log log = LogFactory.getLog(JSSEUtil.class);
    private static final StringManager sm = StringManager.getManager(JSSEUtil.class);

    private volatile boolean initialized = false;

    private volatile Set<String> implementedProtocols;
    private volatile Set<String> implementedCiphers;


    public JSSEUtil (SSLHostConfigCertificate certificate) {
        this(certificate, true);
    }


    public JSSEUtil (SSLHostConfigCertificate certificate, boolean warnOnSkip) {
        super(certificate, warnOnSkip);
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected Set<String> getImplementedProtocols() {
        initialise();
        return implementedProtocols;
    }


    @Override
    protected Set<String> getImplementedCiphers() {
        initialise();
        return implementedCiphers;
    }


    @Override
    protected boolean isTls13RenegAuthAvailable() {
        // TLS 1.3 does not support authentication after the initial handshake
        return false;
    }


    @Override
    public SSLContext createSSLContextInternal(List<String> negotiableProtocols)
            throws NoSuchAlgorithmException {
        return new JSSESSLContext(sslHostConfig.getSslProtocol());
    }


    private void initialise() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    SSLContext context;
                    try {
                        context = new JSSESSLContext(sslHostConfig.getSslProtocol());
                        context.init(null,  null,  null);
                    } catch (NoSuchAlgorithmException | KeyManagementException e) {
                        // This is fatal for the connector so throw an exception to prevent
                        // it from starting
                        throw new IllegalArgumentException(e);
                    }

                    String[] implementedProtocolsArray = context.getSupportedSSLParameters().getProtocols();
                    implementedProtocols = new HashSet<>(implementedProtocolsArray.length);

                    // Filter out SSLv2 from the list of implemented protocols (just in case
                    // we are running on a JVM that supports it) since it is no longer
                    // considered secure but allow SSLv2Hello.
                    // Note SSLv3 is allowed despite known insecurities because some users
                    // still have a requirement for it.
                    for (String protocol : implementedProtocolsArray) {
                        String protocolUpper = protocol.toUpperCase(Locale.ENGLISH);
                        if (!"SSLV2HELLO".equals(protocolUpper) && !"SSLV3".equals(protocolUpper)) {
                            if (protocolUpper.contains("SSL")) {
                                log.debug(sm.getString("jsseUtil.excludeProtocol", protocol));
                                continue;
                            }
                        }
                        implementedProtocols.add(protocol);
                    }

                    if (implementedProtocols.size() == 0) {
                        log.warn(sm.getString("jsseUtil.noDefaultProtocols"));
                    }

                    String[] implementedCipherSuiteArray = context.getSupportedSSLParameters().getCipherSuites();
                    // The IBM JRE will accept cipher suites names SSL_xxx or TLS_xxx but
                    // only returns the SSL_xxx form for supported cipher suites. Therefore
                    // need to filter the requested cipher suites using both forms with an
                    // IBM JRE.
                    if (JreVendor.IS_IBM_JVM) {
                        implementedCiphers = new HashSet<>(implementedCipherSuiteArray.length * 2);
                        for (String name : implementedCipherSuiteArray) {
                            implementedCiphers.add(name);
                            if (name.startsWith("SSL")) {
                                implementedCiphers.add("TLS" + name.substring(3));
                            }
                        }
                    } else {
                        implementedCiphers = new HashSet<>(Arrays.asList(implementedCipherSuiteArray));
                    }
                    initialized = true;
                }
            }
        }
    }
}
