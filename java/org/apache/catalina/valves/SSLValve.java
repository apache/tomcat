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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.servlet.ServletException;

import org.apache.catalina.Globals;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * When using mod_proxy_http, the client SSL information is not included in the
 * protocol (unlike mod_jk and mod_proxy_ajp). To make the client SSL
 * information available to Tomcat, some additional configuration is required.
 * In httpd, mod_headers is used to add the SSL information as HTTP headers. In
 * Tomcat, this valve is used to read the information from the HTTP headers and
 * insert it into the request.<p>
 *
 * <b>Note: Ensure that the headers are always set by httpd for all requests to
 * prevent a client spoofing SSL information by sending fake headers. </b><p>
 *
 * In httpd.conf add the following:
 * <pre>
 * &lt;IfModule ssl_module&gt;
 *   RequestHeader set SSL_CLIENT_CERT "%{SSL_CLIENT_CERT}s"
 *   RequestHeader set SSL_CIPHER "%{SSL_CIPHER}s"
 *   RequestHeader set SSL_SESSION_ID "%{SSL_SESSION_ID}s"
 *   RequestHeader set SSL_CIPHER_USEKEYSIZE "%{SSL_CIPHER_USEKEYSIZE}s"
 * &lt;/IfModule&gt;
 * </pre>
 *
 * In server.xml, configure this valve under the Engine element in server.xml:
 * <pre>
 * &lt;Engine ...&gt;
 *   &lt;Valve className="org.apache.catalina.valves.SSLValve" /&gt;
 *   &lt;Host ... /&gt;
 * &lt;/Engine&gt;
 * </pre>
 */
public class SSLValve extends ValveBase {

    private static final Log log = LogFactory.getLog(SSLValve.class);


    //------------------------------------------------------ Constructor
    public SSLValve() {
        super(true);
    }



    public String mygetHeader(Request request, String header) {
        String strcert0 = request.getHeader(header);
        if (strcert0 == null) {
            return null;
        }
        /* mod_header writes "(null)" when the ssl variable is no filled */
        if ("(null)".equals(strcert0)) {
            return null;
        }
        return strcert0;
    }
    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        /* mod_header converts the '\n' into ' ' so we have to rebuild the client certificate */
        String strcert0 = mygetHeader(request, "ssl_client_cert");
        if (strcert0 != null && strcert0.length()>28) {
            String strcert1 = strcert0.replace(' ', '\n');
            String strcert2 = strcert1.substring(28, strcert1.length()-26);
            String strcert3 = "-----BEGIN CERTIFICATE-----\n";
            String strcert4 = strcert3.concat(strcert2);
            String strcerts = strcert4.concat("\n-----END CERTIFICATE-----\n");
            // ByteArrayInputStream bais = new ByteArrayInputStream(strcerts.getBytes("UTF-8"));
            ByteArrayInputStream bais = new ByteArrayInputStream(
                    strcerts.getBytes(Charset.defaultCharset()));
            X509Certificate jsseCerts[] = null;
            String providerName = (String) request.getConnector().getProperty(
                    "clientCertProvider");
            try {
                CertificateFactory cf;
                if (providerName == null) {
                    cf = CertificateFactory.getInstance("X.509");
                } else {
                    cf = CertificateFactory.getInstance("X.509", providerName);
                }
                X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);
                jsseCerts = new X509Certificate[1];
                jsseCerts[0] = cert;
            } catch (java.security.cert.CertificateException e) {
                log.warn(sm.getString("sslValve.certError", strcerts), e);
            } catch (NoSuchProviderException e) {
                log.error(sm.getString(
                        "sslValve.invalidProvider", providerName), e);
            }
            request.setAttribute(Globals.CERTIFICATES_ATTR, jsseCerts);
        }
        strcert0 = mygetHeader(request, "ssl_cipher");
        if (strcert0 != null) {
            request.setAttribute(Globals.CIPHER_SUITE_ATTR, strcert0);
        }
        strcert0 = mygetHeader(request, "ssl_session_id");
        if (strcert0 != null) {
            request.setAttribute(Globals.SSL_SESSION_ID_ATTR, strcert0);
            request.setAttribute(Globals.SSL_SESSION_ID_TOMCAT_ATTR, strcert0);
        }
        strcert0 = mygetHeader(request, "ssl_cipher_usekeysize");
        if (strcert0 != null) {
            request.setAttribute(Globals.KEY_SIZE_ATTR,
                    Integer.valueOf(strcert0));
        }
        getNext().invoke(request, response);
    }
}
