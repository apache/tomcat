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

import java.io.IOException;
import java.io.ByteArrayInputStream;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.servlet.ServletException;

import org.apache.catalina.valves.ValveBase;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.StringManager;

/*
 * Valve to fill the SSL informations in the request
 * mod_header is used to fill the headers and the valve
 * will fill the parameters of the request.
 * In httpd.conf add the following:
 * <IfModule ssl_module>
 *   RequestHeader set SSL_CLIENT_CERT "%{SSL_CLIENT_CERT}s"
 *   RequestHeader set SSL_CIPHER "%{SSL_CIPHER}s"
 *   RequestHeader set SSL_SESSION_ID "%{SSL_SESSION_ID}s"
 *   RequestHeader set SSL_CIPHER_USEKEYSIZE "%{SSL_CIPHER_USEKEYSIZE}s"
 * </IfModule>
 *
 * @author Jean-Frederic Clere
 * @version $Revision: 420067 $, $Date: 2006-07-08 09:16:58 +0200 (sub, 08 srp 2006) $
 */

public class SSLValve
    extends ValveBase {
/*
    private static final String info =
        "SSLValve/1.0";
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);
    public String getInfo() {
        return (info);
    }
    public String toString() {
        StringBuffer sb = new StringBuffer("SSLValve[");
                if (container != null)
            sb.append(container.getName());
        sb.append("]");
        return (sb.toString());
    }
 */
    public String mygetHeader(Request request, String header) {
        String strcert0 = request.getHeader(header);
        if (strcert0 == null)
            return null;
        /* mod_header writes "(null)" when the ssl variable is no filled */
        if ("(null)".equals(strcert0))
            return null;
        return strcert0;
    } 
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        /* mod_header converts the '\n' into ' ' so we have to rebuild the client certificate */
        String strcert0 = mygetHeader(request, "ssl_client_cert");
        if (strcert0 != null && strcert0.length()>28) {
            String strcert1 = strcert0.replace(' ', '\n');
            String strcert2 = strcert1.substring(28, strcert1.length()-26);
            String strcert3 = new String("-----BEGIN CERTIFICATE-----\n");
            String strcert4 = strcert3.concat(strcert2);
            String strcerts = strcert4.concat("\n-----END CERTIFICATE-----\n");
            // ByteArrayInputStream bais = new ByteArrayInputStream(strcerts.getBytes("UTF-8"));
            ByteArrayInputStream bais = new ByteArrayInputStream(strcerts.getBytes());
            X509Certificate jsseCerts[] = null;
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);
                jsseCerts = new X509Certificate[1];
                jsseCerts[0] = cert;
            } catch (java.security.cert.CertificateException e) {
                System.out.println("SSLValve failed " + strcerts);
                System.out.println("SSLValve failed " + e);
            }
            request.setAttribute("javax.servlet.request.X509Certificate", jsseCerts);
        }
        strcert0 = mygetHeader(request, "ssl_cipher");
        if (strcert0 != null) {
            request.setAttribute("javax.servlet.request.cipher_suite", strcert0);
        }
        strcert0 = mygetHeader(request, "ssl_session_id");
        if (strcert0 != null) {
            request.setAttribute("javax.servlet.request.ssl_session", strcert0);
        }
        strcert0 = mygetHeader(request, "ssl_cipher_usekeysize");
        if (strcert0 != null) {
            request.setAttribute("javax.servlet.request.key_size", strcert0);
        }
        getNext().invoke(request, response);
    }
}
