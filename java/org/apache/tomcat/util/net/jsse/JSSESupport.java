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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.net.SSLSessionManager;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.res.StringManager;

/**
 * JSSESupport.
 *
 * Concrete implementation class for JSSE Support classes.
 *
 * @author EKR
 * @author Craig R. McClanahan
 * Parts cribbed from JSSECertCompat
 * Parts cribbed from CertificatesValve
 */
public class JSSESupport implements SSLSupport, SSLSessionManager {

    private static final Log log = LogFactory.getLog(JSSESupport.class);

    private static final StringManager sm = StringManager.getManager(JSSESupport.class);

    private static final Map<String,Integer> keySizeCache = new HashMap<>();

    static {
        for (Cipher cipher : Cipher.values()) {
            for (String jsseName : cipher.getJsseNames()) {
                keySizeCache.put(jsseName, Integer.valueOf(cipher.getStrength_bits()));
            }
        }
    }

    /*
     * NO-OP method provided to make it easy for other classes in this package
     * to trigger the loading of this class and the population of the
     * keySizeCache.
     */
    static void init() {
        // NO-OP
    }

    private SSLSession session;
    private Map<String,List<String>> additionalAttributes;

    public JSSESupport(SSLSession session, Map<String,List<String>> additionalAttributes) {
        this.session = session;
        this.additionalAttributes = additionalAttributes;
    }

    @Override
    public String getCipherSuite() throws IOException {
        // Look up the current SSLSession
        if (session == null) {
            return null;
        }
        return session.getCipherSuite();
    }

    @Override
    public X509Certificate[] getLocalCertificateChain() {
        if (session == null) {
            return null;
        }
        return convertCertificates(session.getLocalCertificates());
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws IOException {
        // Look up the current SSLSession
        if (session == null) {
            return null;
        }

        Certificate [] certs=null;
        try {
            certs = session.getPeerCertificates();
        } catch( Throwable t ) {
            log.debug(sm.getString("jsseSupport.clientCertError"), t);
            return null;
        }

        return convertCertificates(certs);
    }


    private static X509Certificate[] convertCertificates(Certificate[] certs) {
        if( certs==null ) {
            return null;
        }

        X509Certificate [] x509Certs = new X509Certificate[certs.length];
        for(int i=0; i < certs.length; i++) {
            if (certs[i] instanceof X509Certificate ) {
                // always currently true with the JSSE 1.1.x
                x509Certs[i] = (X509Certificate) certs[i];
            } else {
                try {
                    byte [] buffer = certs[i].getEncoded();
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    ByteArrayInputStream stream = new ByteArrayInputStream(buffer);
                    x509Certs[i] = (X509Certificate) cf.generateCertificate(stream);
                } catch(Exception ex) {
                    log.info(sm.getString("jsseSupport.certTranslationError", certs[i]), ex);
                    return null;
                }
            }
            if(log.isTraceEnabled()) {
                log.trace("Cert #" + i + " = " + x509Certs[i]);
            }
        }
        if(x509Certs.length < 1) {
            return null;
        }
        return x509Certs;
    }


    /**
     * {@inheritDoc}
     * <p>
     * This returns the effective bits for the current cipher suite.
     */
    @Override
    public Integer getKeySize() throws IOException {
        // Look up the current SSLSession
        if (session == null) {
            return null;
        }

        return keySizeCache.get(session.getCipherSuite());
    }

    @Override
    public String getSessionId()
        throws IOException {
        // Look up the current SSLSession
        if (session == null) {
            return null;
        }
        // Expose ssl_session (getId)
        byte [] ssl_session = session.getId();
        if (ssl_session == null || ssl_session.length == 0) {
            return null;
        }
        StringBuilder buf=new StringBuilder();
        for (byte b : ssl_session) {
            String digit = Integer.toHexString(b);
            if (digit.length() < 2) {
                buf.append('0');
            }
            if (digit.length() > 2) {
                digit = digit.substring(digit.length() - 2);
            }
            buf.append(digit);
        }
        return buf.toString();
    }


    public void setSession(SSLSession session) {
        this.session = session;
    }


    /**
     * Invalidate the session this support object is associated with.
     */
    @Override
    public void invalidateSession() {
        session.invalidate();
    }

    @Override
    public String getProtocol() throws IOException {
        if (session == null) {
           return null;
        }
       return session.getProtocol();
    }

    @Override
    public String getRequestedProtocols() throws IOException {
        if (additionalAttributes == null) {
            return null;
        }
        return StringUtils.join(additionalAttributes.get(REQUESTED_PROTOCOL_VERSIONS_KEY));
    }

    @Override
    public String getRequestedCiphers() throws IOException {
        if (additionalAttributes == null) {
            return null;
        }
        return StringUtils.join(additionalAttributes.get(REQUESTED_CIPHERS_KEY));
    }
}

