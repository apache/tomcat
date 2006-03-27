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

package org.apache.tomcat.util.net.jsse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.security.cert.X509Certificate;

import org.apache.tomcat.util.net.SSLSupport;

/* JSSESupport

   Concrete implementation class for JSSE
   Support classes.

   This will only work with JDK 1.2 and up since it
   depends on JDK 1.2's certificate support

   @author EKR
   @author Craig R. McClanahan
   Parts cribbed from JSSECertCompat       
   Parts cribbed from CertificatesValve
*/

class JSSESupport implements SSLSupport {
    private static org.apache.commons.logging.Log log =
	org.apache.commons.logging.LogFactory.getLog(JSSESupport.class);

    protected SSLSocket ssl;


    JSSESupport(SSLSocket sock){
        ssl=sock;
    }

    public String getCipherSuite() throws IOException {
        // Look up the current SSLSession
        SSLSession session = ssl.getSession();
        if (session == null)
            return null;
        return session.getCipherSuite();
    }

    public Object[] getPeerCertificateChain() 
        throws IOException {
        return getPeerCertificateChain(false);
    }

    protected java.security.cert.X509Certificate [] 
	getX509Certificates(SSLSession session) throws IOException {
        X509Certificate jsseCerts[] = null;
    try{
	    jsseCerts = session.getPeerCertificateChain();
    } catch (Throwable ex){
       // Get rid of the warning in the logs when no Client-Cert is
       // available
    }

	if(jsseCerts == null)
	    jsseCerts = new X509Certificate[0];
	java.security.cert.X509Certificate [] x509Certs =
	    new java.security.cert.X509Certificate[jsseCerts.length];
	for (int i = 0; i < x509Certs.length; i++) {
	    try {
		byte buffer[] = jsseCerts[i].getEncoded();
		CertificateFactory cf =
		    CertificateFactory.getInstance("X.509");
		ByteArrayInputStream stream =
		    new ByteArrayInputStream(buffer);
		x509Certs[i] = (java.security.cert.X509Certificate)
		    cf.generateCertificate(stream);
		if(log.isTraceEnabled())
		    log.trace("Cert #" + i + " = " + x509Certs[i]);
	    } catch(Exception ex) {
		log.info("Error translating " + jsseCerts[i], ex);
		return null;
	    }
	}
	
	if ( x509Certs.length < 1 )
	    return null;
	return x509Certs;
    }
    public Object[] getPeerCertificateChain(boolean force)
        throws IOException {
        // Look up the current SSLSession
	SSLSession session = ssl.getSession();
        if (session == null)
            return null;

        // Convert JSSE's certificate format to the ones we need
	X509Certificate [] jsseCerts = null;
	try {
	    jsseCerts = session.getPeerCertificateChain();
	} catch(Exception bex) {
	    // ignore.
	}
	if (jsseCerts == null)
	    jsseCerts = new X509Certificate[0];
	if(jsseCerts.length <= 0 && force) {
	    session.invalidate();
	    handShake();
	    session = ssl.getSession();
	}
        return getX509Certificates(session);
    }

    protected void handShake() throws IOException {
        ssl.setNeedClientAuth(true);
        ssl.startHandshake();
    }
    /**
     * Copied from <code>org.apache.catalina.valves.CertificateValve</code>
     */
    public Integer getKeySize() 
        throws IOException {
        // Look up the current SSLSession
        SSLSession session = ssl.getSession();
        SSLSupport.CipherData c_aux[]=ciphers;
        if (session == null)
            return null;
        Integer keySize = (Integer) session.getValue(KEY_SIZE_KEY);
        if (keySize == null) {
            int size = 0;
            String cipherSuite = session.getCipherSuite();
            for (int i = 0; i < c_aux.length; i++) {
                if (cipherSuite.indexOf(c_aux[i].phrase) >= 0) {
                    size = c_aux[i].keySize;
                    break;
                }
            }
            keySize = new Integer(size);
            session.putValue(KEY_SIZE_KEY, keySize);
        }
        return keySize;
    }

    public String getSessionId()
        throws IOException {
        // Look up the current SSLSession
        SSLSession session = ssl.getSession();
        if (session == null)
            return null;
        // Expose ssl_session (getId)
        byte [] ssl_session = session.getId();
        if ( ssl_session == null) 
            return null;
        StringBuffer buf=new StringBuffer("");
        for(int x=0; x<ssl_session.length; x++) {
            String digit=Integer.toHexString((int)ssl_session[x]);
            if (digit.length()<2) buf.append('0');
            if (digit.length()>2) digit=digit.substring(digit.length()-2);
            buf.append(digit);
        }
        return buf.toString();
    }


}

