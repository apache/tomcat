/**
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
import java.util.ArrayList;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.openssl.OpenSSLConf;

/**
 * Store SSLHostConfig
 */
public class SSLHostConfigSF extends StoreFactoryBase {

    private static Log log = LogFactory.getLog(SSLHostConfigSF.class);

    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement) throws Exception {
        StoreDescription elementDesc = getRegistry().findDescription(aElement.getClass());
        if (elementDesc != null) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("factory.storeTag", elementDesc.getTag(), aElement));
            }
            getStoreAppender().printIndent(aWriter, indent + 2);
            aWriter.print("<");
            aWriter.print(elementDesc.getTag());
            if (elementDesc.isAttributes()) {
                // Add protocols attribute
                SSLHostConfig bean2 = (SSLHostConfig) getStoreAppender().defaultInstance(aElement);
                SSLHostConfig sslHostConfig = (SSLHostConfig) aElement;
                if (!bean2.getProtocols().equals(sslHostConfig.getProtocols())) {
                    StringBuffer protocolsValue = new StringBuffer();
                    for (String protocol : sslHostConfig.getProtocols()) {
                        protocolsValue.append('+').append(protocol);
                    }
                    getStoreAppender().printValue(aWriter, indent, "protocols", protocolsValue.toString());
                }
                getStoreAppender().printAttributes(aWriter, indent, aElement, elementDesc);
            }
            aWriter.println(">");
            storeChildren(aWriter, indent + 2, aElement, elementDesc);
            getStoreAppender().printIndent(aWriter, indent + 2);
            getStoreAppender().printCloseTag(aWriter, elementDesc);
        } else {
            if (log.isWarnEnabled()) {
                log.warn(sm.getString("factory.storeNoDescriptor", aElement.getClass()));
            }
        }
    }

    /**
     * Store nested SSLHostConfigCertificate elements.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aSSLHostConfig, StoreDescription parentDesc)
            throws Exception {
        if (aSSLHostConfig instanceof SSLHostConfig) {
            SSLHostConfig sslHostConfig = (SSLHostConfig) aSSLHostConfig;
            // Store nested <SSLHostConfigCertificate> elements
            SSLHostConfigCertificate[] hostConfigsCertificates =
                    sslHostConfig.getCertificates().toArray(new SSLHostConfigCertificate[0]);
            // Remove a possible default UNDEFINED certificate
            if (hostConfigsCertificates.length > 1) {
                ArrayList<SSLHostConfigCertificate> certificates = new ArrayList<>();
                for (SSLHostConfigCertificate certificate : hostConfigsCertificates) {
                    if (Type.UNDEFINED != certificate.getType()) {
                        certificates.add(certificate);
                    }
                }
                hostConfigsCertificates = certificates.toArray(new SSLHostConfigCertificate[0]);
            }
            storeElementArray(aWriter, indent, hostConfigsCertificates);
            // Store nested <OpenSSLConf> element
            OpenSSLConf openSslConf = sslHostConfig.getOpenSslConf();
            storeElement(aWriter, indent, openSslConf);
        }
    }

}
