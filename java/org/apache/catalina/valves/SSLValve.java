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
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.servlet.ServletException;

import org.apache.catalina.Globals;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

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
 * @version $Id$
 */

public class SSLValve extends ValveBase {
    
    
    //------------------------------------------------------ Constructor
    public SSLValve() {
        super(true);
    }



    public String mygetHeader(Request request, String header) {
        String strcert0 = request.getHeader(header);
        if (strcert0 == null)
            return null;
        /* mod_header writes "(null)" when the ssl variable is no filled */
        if ("(null)".equals(strcert0))
            return null;
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
            request.setAttribute(Globals.CERTIFICATES_ATTR, jsseCerts);
        }
        strcert0 = mygetHeader(request, "ssl_cipher");
        if (strcert0 != null) {
            request.setAttribute(Globals.CIPHER_SUITE_ATTR, strcert0);
        }
        strcert0 = mygetHeader(request, "ssl_session_id");
        if (strcert0 != null) {
            request.setAttribute(Globals.SSL_SESSION_ID_ATTR, strcert0);
        }
        strcert0 = mygetHeader(request, "ssl_cipher_usekeysize");
        if (strcert0 != null) {
            request.setAttribute(Globals.KEY_SIZE_ATTR, strcert0);
        }
        getNext().invoke(request, response);
    }
}
