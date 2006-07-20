/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.net.puretls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Vector;

import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.net.SSLSupport;

import COM.claymoresystems.cert.X509Cert;
import COM.claymoresystems.ptls.SSLSocket;
import COM.claymoresystems.sslg.SSLPolicyInt;


/* PureTLSSupport

   Concrete implementation class for PureTLS
   Support classes.

   This will only work with JDK 1.2 and up since it
   depends on JDK 1.2's certificate support

   @author EKR
*/

class PureTLSSupport implements SSLSupport {
    static org.apache.commons.logging.Log logger =
	org.apache.commons.logging.LogFactory.getLog(PureTLSSupport.class);

    private COM.claymoresystems.ptls.SSLSocket ssl;

    PureTLSSupport(SSLSocket sock){
        ssl=sock;
    }

    public String getCipherSuite() throws IOException {
        int cs=ssl.getCipherSuite();
        return SSLPolicyInt.getCipherSuiteName(cs);
    }

    public Object[] getPeerCertificateChain()
        throws IOException {
	return getPeerCertificateChain(false);
    }

    public Object[] getPeerCertificateChain(boolean force)
        throws IOException {
        Vector v=ssl.getCertificateChain();

	if(v == null && force) {
	    SSLPolicyInt policy=new SSLPolicyInt();
	    policy.requireClientAuth(true);
	    policy.handshakeOnConnect(false);
	    policy.waitOnClose(false);
	    ssl.renegotiate(policy);
	    v = ssl.getCertificateChain();
	}

        if(v==null)
            return null;
        
        java.security.cert.X509Certificate[] chain=
            new java.security.cert.X509Certificate[v.size()];

        try {
          for(int i=1;i<=v.size();i++){
            // PureTLS provides cert chains with the peer
            // cert last but the Servlet 2.3 spec (S 4.7) requires
            // the opposite order so we reverse the chain as we go
            byte buffer[]=((X509Cert)v.elementAt(
                 v.size()-i)).getDER();
            
            CertificateFactory cf =
              CertificateFactory.getInstance("X.509");
            ByteArrayInputStream stream =
              new ByteArrayInputStream(buffer);

            X509Certificate xCert = (X509Certificate)cf.generateCertificate(stream);
            chain[i-1]= xCert;
            if(logger.isTraceEnabled()) {
		logger.trace("Cert # " + i + " = " + xCert);
	    }
          }
        } catch (java.security.cert.CertificateException e) {
	    logger.info("JDK's broken cert handling can't parse this certificate (which PureTLS likes)",e);
            throw new IOException("JDK's broken cert handling can't parse this certificate (which PureTLS likes)");
        }
        return chain;
    }

    /**
     * Lookup the symmetric key size.
     */
    public Integer getKeySize() 
        throws IOException {

        int cs=ssl.getCipherSuite();
        String cipherSuite = SSLPolicyInt.getCipherSuiteName(cs);
        int size = 0;
        for (int i = 0; i < ciphers.length; i++) {
            if (cipherSuite.indexOf(ciphers[i].phrase) >= 0) {
                size = ciphers[i].keySize;
                break;
            }
        }
        Integer keySize = new Integer(size);
        return keySize;
    }

    public String getSessionId()
        throws IOException {
        byte [] ssl_session = ssl.getSessionID();
        if(ssl_session == null)
            return null;
        return HexUtils.convert(ssl_session);
    }

}







