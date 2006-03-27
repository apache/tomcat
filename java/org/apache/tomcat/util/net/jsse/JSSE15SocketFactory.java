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

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.security.KeyStore;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.CRL;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertPathParameters;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.CertPathTrustManagerParameters;

/**
 * SSL Socket Factory for JDK 1.5
 *
 * @author Bill Barker
 */
public class JSSE15SocketFactory  extends JSSE14SocketFactory {

    private static org.apache.commons.logging.Log log =
        org.apache.commons.logging.LogFactory.getLog(JSSE15SocketFactory.class);

    public JSSE15SocketFactory() {
        super();
    }


    /**
     * Gets the intialized trust managers.
     */
    protected TrustManager[] getTrustManagers(String keystoreType, String algorithm)
        throws Exception {
        if(attributes.get("truststoreAlgorithm") == null) {
            // in 1.5, the Trust default isn't the same as the Key default.
            algorithm = TrustManagerFactory.getDefaultAlgorithm();
        }
        String crlf = (String)attributes.get("crlFile");
        if(crlf == null) {
            return super.getTrustManagers(keystoreType, algorithm);
        }

        TrustManager[] tms = null;

        String truststoreType = (String)attributes.get("truststoreType");
        if(truststoreType == null) {
            truststoreType = keystoreType;
        }
        KeyStore trustStore = getTrustStore(truststoreType);
        if (trustStore != null) {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
            CertPathParameters params = getParameters(algorithm, crlf, trustStore);
            ManagerFactoryParameters mfp = new CertPathTrustManagerParameters(params);
            tmf.init(mfp);
            tms = tmf.getTrustManagers();
        }

        return tms;
    }


    /**
     * Return the initialization parameters for the TrustManager.
     * Currently, only the default <code>PKIX</code> is supported.
     * 
     * @param algorithm The algorithm to get parameters for.
     * @param crlf The path to the CRL file.
     * @param trustStore The configured TrustStore.
     * @return The parameters including the CRLs and TrustStore.
     */
    protected CertPathParameters getParameters(String algorithm, 
                                                String crlf, 
                                                KeyStore trustStore)
        throws Exception {
        CertPathParameters params = null;
        if("PKIX".equalsIgnoreCase(algorithm)) {
            PKIXBuilderParameters xparams = new PKIXBuilderParameters(trustStore, 
                                                                     new X509CertSelector());
            Collection crls = getCRLs(crlf);
            CertStoreParameters csp = new CollectionCertStoreParameters(crls);
            CertStore store = CertStore.getInstance("Collection", csp);
            xparams.addCertStore(store);
            xparams.setRevocationEnabled(true);
            String trustLength = (String)attributes.get("trustMaxCertLength");
            if(trustLength != null) {
                try {
                    xparams.setMaxPathLength(Integer.parseInt(trustLength));
                } catch(Exception ex) {
                    log.warn("Bad maxCertLength: "+trustLength);
                }
            }

            params = xparams;
        } else {
            throw new CRLException("CRLs not supported for type: "+algorithm);
        }
        return params;
    }


    /**
     * Load the collection of CRLs.
     * 
     */
    protected Collection<? extends CRL> getCRLs(String crlf) 
        throws IOException, CRLException, CertificateException {

        File crlFile = new File(crlf);
        if( !crlFile.isAbsolute() ) {
            crlFile = new File(System.getProperty("catalina.base"), crlf);
        }
        Collection<? extends CRL> crls = null;
        InputStream is = null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            is = new FileInputStream(crlFile);
            crls = cf.generateCRLs(is);
        } catch(IOException iex) {
            throw iex;
        } catch(CRLException crle) {
            throw crle;
        } catch(CertificateException ce) {
            throw ce;
        } finally { 
            if(is != null) {
                try{
                    is.close();
                } catch(Exception ex) {
                }
            }
        }
        return crls;
    }

}
