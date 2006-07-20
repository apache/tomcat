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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Vector;

import COM.claymoresystems.ptls.SSLContext;
import COM.claymoresystems.ptls.SSLException;
import COM.claymoresystems.ptls.SSLServerSocket;
import COM.claymoresystems.ptls.SSLSocket;
import COM.claymoresystems.sslg.SSLPolicyInt;

/**
 * SSL server socket factory--wraps PureTLS
 *
 * @author Eric Rescorla
 *
 * some sections of this file cribbed from SSLSocketFactory
 * (the JSSE socket factory)
 *
 */
 
public class PureTLSSocketFactory
    extends org.apache.tomcat.util.net.ServerSocketFactory
{
    static org.apache.commons.logging.Log logger =
	org.apache.commons.logging.LogFactory.getLog(PureTLSSocketFactory.class);
    static String defaultProtocol = "TLS";
    static boolean defaultClientAuth = false;
    static String defaultKeyStoreFile = "server.pem";
    static String defaultKeyPass = "password";    
    static String defaultRootFile = "root.pem";
    static String defaultRandomFile = "random.pem";
    
    private COM.claymoresystems.ptls.SSLContext context=null;
    
    public PureTLSSocketFactory() {
    }

    public ServerSocket createSocket(int port)
	throws IOException
    {
	init();
	return new SSLServerSocket(context,port);
    }

    public ServerSocket createSocket(int port, int backlog)
	throws IOException
    {
	init();
	ServerSocket tmp;
	
	try {
	    tmp=new SSLServerSocket(context,port,backlog);
	}
	catch (IOException e){
	    throw e;
	}
	return tmp;
    }

    public ServerSocket createSocket(int port, int backlog,
				     InetAddress ifAddress)
	throws IOException
    {
	init();
	return new SSLServerSocket(context,port,backlog,ifAddress);
    }

    private void init()
	throws IOException
    {
	if(context!=null)
	    return;
	
	boolean clientAuth=defaultClientAuth;

	try {
	    String keyStoreFile=(String)attributes.get("keystore");
	    if(keyStoreFile==null) keyStoreFile=defaultKeyStoreFile;
	    
	    String keyPass=(String)attributes.get("keypass");
	    if(keyPass==null) keyPass=defaultKeyPass;
	    
	    String rootFile=(String)attributes.get("rootfile");
	    if(rootFile==null) rootFile=defaultRootFile;

	    String randomFile=(String)attributes.get("randomfile");
	    if(randomFile==null) randomFile=defaultRandomFile;
	    
	    String protocol=(String)attributes.get("protocol");
	    if(protocol==null) protocol=defaultProtocol;

	    String clientAuthStr=(String)attributes.get("clientauth");
	    if(clientAuthStr != null){
		if(clientAuthStr.equals("true")){
		    clientAuth=true;
		} else if(clientAuthStr.equals("false")) {
		    clientAuth=false;
		} else {
		    throw new IOException("Invalid value '" +
					  clientAuthStr + 
					  "' for 'clientauth' parameter:");
		}
	    }

            SSLContext tmpContext=new SSLContext();
            try {
                tmpContext.loadRootCertificates(rootFile);
            } catch(IOException iex) {
                if(logger.isDebugEnabled())
                    logger.debug("Error loading Client Root Store: " + 
                                 rootFile,iex);
            }
            tmpContext.loadEAYKeyFile(keyStoreFile,keyPass);
	    tmpContext.useRandomnessFile(randomFile,keyPass);
	    
	    SSLPolicyInt policy=new SSLPolicyInt();
	    policy.requireClientAuth(clientAuth);
            policy.handshakeOnConnect(false);
            policy.waitOnClose(false);
            short [] enabledCiphers = getEnabledCiphers(policy.getCipherSuites());
            if( enabledCiphers != null ) {
                policy.setCipherSuites(enabledCiphers);
            }
            tmpContext.setPolicy(policy);
	    context=tmpContext;
	} catch (Exception e){
	    logger.info("Error initializing SocketFactory",e);
	    throw new IOException(e.getMessage());
	}
    }

    /*
     * Determines the SSL cipher suites to be enabled.
     *
     * @return Array of SSL cipher suites to be enabled, or null if the
     * cipherSuites property was not specified (meaning that all supported
     * cipher suites are to be enabled)
     */
    private short [] getEnabledCiphers(short [] supportedCiphers) {

        short [] enabledCiphers = null;

        String attrValue = (String)attributes.get("ciphers");
        if (attrValue != null) {
            Vector vec = null;
            int fromIndex = 0;
            int index = attrValue.indexOf(',', fromIndex);
            while (index != -1) {
                String cipher = attrValue.substring(fromIndex, index).trim();
                int cipherValue = SSLPolicyInt.getCipherSuiteNumber(cipher);                
                /*
                 * Check to see if the requested cipher is among the supported
                 * ciphers, i.e., may be enabled
                 */
                if( cipherValue >= 0) {
                    for (int i=0; supportedCiphers != null
                             && i<supportedCiphers.length; i++) {

                        if (cipherValue == supportedCiphers[i]) {
                            if (vec == null) {
                                vec = new Vector();
                            }
                            vec.addElement(new Integer(cipherValue));
                            break;
                        }
                    }
                }
                fromIndex = index+1;
                index = attrValue.indexOf(',', fromIndex);
            }

            if (vec != null) {
                int nCipher = vec.size();
                enabledCiphers = new short[nCipher];
                for(int i=0; i < nCipher; i++) {
                    Integer value = (Integer)vec.elementAt(i);
                    enabledCiphers[i] = value.shortValue();
                }
            }
        }

        return enabledCiphers;

    }

    public Socket acceptSocket(ServerSocket socket)
	throws IOException
    {
	try {
	    Socket sock=socket.accept();
	    return sock;
	} catch (SSLException e){
            logger.debug("SSL handshake error",e);
            throw new SocketException("SSL handshake error" + e.toString());
	}
    }

    public void handshake(Socket sock)
	 throws IOException
    {
	((SSLSocket)sock).handshake();
    }
}

    
    


