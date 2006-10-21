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

package org.apache.jk.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.CharConversionException;
import java.net.InetAddress;
import java.util.Properties;

import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.coyote.Constants;
import org.apache.jk.core.JkHandler;
import org.apache.jk.core.Msg;
import org.apache.jk.core.MsgContext;
import org.apache.jk.core.WorkerEnv;
import org.apache.jk.core.JkChannel;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.threads.ThreadWithAttributes;

/**
 * Handle messages related with basic request information.
 *
 * This object can handle the following incoming messages:
 * - "FORWARD_REQUEST" input message ( sent when a request is passed from the
 *   web server )
 * - "RECEIVE_BODY_CHUNK" input ( sent by container to pass more body, in
 *   response to GET_BODY_CHUNK )
 *
 * It can handle the following outgoing messages:
 * - SEND_HEADERS. Pass the status code and headers.
 * - SEND_BODY_CHUNK. Send a chunk of body
 * - GET_BODY_CHUNK. Request a chunk of body data
 * - END_RESPONSE. Notify the end of a request processing.
 *
 * @author Henri Gomez [hgomez@apache.org]
 * @author Dan Milstein [danmil@shore.net]
 * @author Keith Wannamaker [Keith@Wannamaker.org]
 * @author Costin Manolache
 */
public class HandlerRequest extends JkHandler
{
    private static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( HandlerRequest.class );

    /*
     * Note for Host parsing.
     */
    public static final int HOSTBUFFER = 10;

    /**
     * Thread lock.
     */
    private static Object lock = new Object();

    private HandlerDispatch dispatch;
    private String ajpidDir="conf";
    

    public HandlerRequest() {
    }

    public void init() {
        dispatch=(HandlerDispatch)wEnv.getHandler( "dispatch" );
        if( dispatch != null ) {
            // register incoming message handlers
            dispatch.registerMessageType( AjpConstants.JK_AJP13_FORWARD_REQUEST,
                                          "JK_AJP13_FORWARD_REQUEST",
                                          this, null); // 2
            
            dispatch.registerMessageType( AjpConstants.JK_AJP13_SHUTDOWN,
                                          "JK_AJP13_SHUTDOWN",
                                          this, null); // 7
            
            dispatch.registerMessageType( AjpConstants.JK_AJP13_CPING_REQUEST,
                                          "JK_AJP13_CPING_REQUEST",
                                           this, null); // 10
            dispatch.registerMessageType( HANDLE_THREAD_END,
                                         "HANDLE_THREAD_END",
                                         this, null);
            // register outgoing messages handler
            dispatch.registerMessageType( AjpConstants.JK_AJP13_SEND_BODY_CHUNK, // 3
                                          "JK_AJP13_SEND_BODY_CHUNK",
                                          this,null );
        }

        tmpBufNote=wEnv.getNoteId( WorkerEnv.ENDPOINT_NOTE, "tmpBuf" );
        secretNote=wEnv.getNoteId( WorkerEnv.ENDPOINT_NOTE, "secret" );
        
        if( next==null )
            next=wEnv.getHandler( "container" );
        if( log.isDebugEnabled() )
            log.debug( "Container handler " + next + " " + next.getName() +
                       " " + next.getClass().getName());

        // should happen on start()
        generateAjp13Id();
    }

    public void setSecret( String s ) {
        requiredSecret=s;
    }

    public void setUseSecret( boolean b ) {
        if(b) {
            requiredSecret=Double.toString(Math.random());
        }
    }

    public void setDecodedUri( boolean b ) {
        decoded=b;
    }

    public boolean isTomcatAuthentication() {
        return tomcatAuthentication;
    }

    public void setShutdownEnabled(boolean se) {
        shutdownEnabled = se;
    }

    public boolean getShutdownEnabled() {
        return shutdownEnabled;
    }

    public void setTomcatAuthentication(boolean newTomcatAuthentication) {
        tomcatAuthentication = newTomcatAuthentication;
    }
    
    public void setAjpidDir( String path ) {
        if( "".equals( path ) ) path=null;
        ajpidDir=path;
    }

    /**
     * Set the flag to tell if we JMX register requests.
     */
    public void setRegisterRequests(boolean srr) {
        registerRequests = srr;
    }

    /**
     * Get the flag to tell if we JMX register requests.
     */
    public boolean getRegisterRequests() {
        return registerRequests;
    }

    /**
     * Set the flag to delay the initial body read
     */
    public void setDelayInitialRead(boolean dir) {
	delayInitialRead = dir;
    }

    /**
     * Get the flag to tell if we delay the initial body read
     */
    public boolean getDelayInitialRead() {
	return delayInitialRead;
    }

    // -------------------- Ajp13.id --------------------

    private void generateAjp13Id() {
        int portInt=8009; // tcpCon.getPort();
        InetAddress address=null; // tcpCon.getAddress();

        if( requiredSecret == null || !shutdownEnabled )
            return;
        
        File f1=new File( wEnv.getJkHome() );
        File f2=new File( f1, "conf" );
        
        if( ! f2.exists() ) {
            log.error( "No conf dir for ajp13.id " + f2 );
            return;
        }
        
        File sf=new File( f2, "ajp13.id");
        
        if( log.isDebugEnabled())
            log.debug( "Using stop file: "+sf);

        try {
            Properties props=new Properties();

            props.put( "port", Integer.toString( portInt ));
            if( address!=null ) {
                props.put( "address", address.getHostAddress() );
            }
            if( requiredSecret !=null ) {
                props.put( "secret", requiredSecret );
            }

            FileOutputStream stopF=new FileOutputStream( sf );
            props.store( stopF, "Automatically generated, don't edit" );
        } catch( IOException ex ) {
            if(log.isDebugEnabled())
                log.debug( "Can't create stop file: "+sf,ex );
        }
    }
    
    // -------------------- Incoming message --------------------
    private String requiredSecret=null;
    private int secretNote;
    private int tmpBufNote;

    private boolean decoded=true;
    private boolean tomcatAuthentication=true;
    private boolean registerRequests=true;
    private boolean shutdownEnabled=false;
    private boolean delayInitialRead = true;
    
    public int invoke(Msg msg, MsgContext ep ) 
        throws IOException    {
        int type=msg.getByte();
        ThreadWithAttributes twa = null;
        if (Thread.currentThread() instanceof ThreadWithAttributes) {
            twa = (ThreadWithAttributes) Thread.currentThread();
        }
        Object control=ep.getControl();
        MessageBytes tmpMB=(MessageBytes)ep.getNote( tmpBufNote );
        if( tmpMB==null ) {
            tmpMB= MessageBytes.newInstance();
            ep.setNote( tmpBufNote, tmpMB);
        }

        if( log.isDebugEnabled() )
            log.debug( "Handling " + type );
        
        switch( type ) {
        case AjpConstants.JK_AJP13_FORWARD_REQUEST:
            try {
                if (twa != null) {
                    twa.setCurrentStage(control, "JkDecode");
                }
                decodeRequest( msg, ep, tmpMB );
                if (twa != null) {
                    twa.setCurrentStage(control, "JkService");
                    twa.setParam(control,
                                 ((Request)ep.getRequest()).unparsedURI());
                }
            } catch( Exception ex ) {
                log.error( "Error decoding request ", ex );
                msg.dump( "Incomming message");
                return ERROR;
            }

            if( requiredSecret != null ) {
                String epSecret=(String)ep.getNote( secretNote );
                if( epSecret==null || ! requiredSecret.equals( epSecret ) )
                    return ERROR;
            }
            /* XXX it should be computed from request, by workerEnv */
            if(log.isDebugEnabled() )
                log.debug("Calling next " + next.getName() + " " +
                  next.getClass().getName());

            int err= next.invoke( msg, ep );
            if (twa != null) {
                twa.setCurrentStage(control, "JkDone");
            }

            if( log.isDebugEnabled() )
                log.debug( "Invoke returned " + err );
            return err;
        case AjpConstants.JK_AJP13_SHUTDOWN:
            String epSecret=null;
            if( msg.getLen() > 3 ) {
                // we have a secret
                msg.getBytes( tmpMB );
                epSecret=tmpMB.toString();
            }
            
            if( requiredSecret != null &&
                requiredSecret.equals( epSecret ) ) {
                if( log.isDebugEnabled() )
                    log.debug("Received wrong secret, no shutdown ");
                return ERROR;
            }

            // XXX add isSameAddress check
            JkChannel ch=ep.getSource();
            if( !ch.isSameAddress(ep) ) {
                log.error("Shutdown request not from 'same address' ");
                return ERROR;
            }

            if( !shutdownEnabled ) {
                log.warn("Ignoring shutdown request: shutdown not enabled");
                return ERROR;
            }
            // forward to the default handler - it'll do the shutdown
            checkRequest(ep);
            next.invoke( msg, ep );

            if(log.isInfoEnabled())
                log.info("Exiting");
            System.exit(0);
            
            return OK;

            // We got a PING REQUEST, quickly respond with a PONG
        case AjpConstants.JK_AJP13_CPING_REQUEST:
            msg.reset();
            msg.appendByte(AjpConstants.JK_AJP13_CPONG_REPLY);
            ep.getSource().send( msg, ep );
            ep.getSource().flush( msg, ep ); // Server needs to get it
            return OK;

        case HANDLE_THREAD_END:
            return OK;

        default:
            if(log.isInfoEnabled())
                log.info("Unknown message " + type);
        }

        return OK;
    }

    static int count = 0;

    private Request checkRequest(MsgContext ep) {
        Request req=ep.getRequest();
        if( req==null ) {
            req=new Request();
            Response res=new Response();
            req.setResponse(res);
            ep.setRequest( req );
            if( registerRequests ) {
                synchronized(lock) {
                    ep.getSource().registerRequest(req, ep, count++);
                }
            }
        }
        return req;
    }

    private int decodeRequest( Msg msg, MsgContext ep, MessageBytes tmpMB )
        throws IOException    {
        // FORWARD_REQUEST handler
        Request req = checkRequest(ep);

        RequestInfo rp = req.getRequestProcessor();
        rp.setStage(Constants.STAGE_PARSE);
        MessageBytes tmpMB2 = (MessageBytes)req.getNote(WorkerEnv.SSL_CERT_NOTE);
        if(tmpMB2 != null) {
            tmpMB2.recycle();
        }
        req.setStartTime(System.currentTimeMillis());
        
        // Translate the HTTP method code to a String.
        byte methodCode = msg.getByte();
        if (methodCode != AjpConstants.SC_M_JK_STORED) {
            String mName=AjpConstants.methodTransArray[(int)methodCode - 1];
            req.method().setString(mName);
        }

        msg.getBytes(req.protocol()); 
        msg.getBytes(req.requestURI());

        msg.getBytes(req.remoteAddr());
        msg.getBytes(req.remoteHost());
        msg.getBytes(req.localName());
        req.setLocalPort(msg.getInt());

        boolean isSSL = msg.getByte() != 0;
        if( isSSL ) {
            // XXX req.setSecure( true );
            req.scheme().setString("https");
        }

        decodeHeaders( ep, msg, req, tmpMB );

        decodeAttributes( ep, msg, req, tmpMB );

        rp.setStage(Constants.STAGE_PREPARE);
        MessageBytes valueMB = req.getMimeHeaders().getValue("host");
        parseHost(valueMB, req);
        // set cookies on request now that we have all headers
        req.getCookies().setHeaders(req.getMimeHeaders());

        // Check to see if there should be a body packet coming along
        // immediately after
        int cl=req.getContentLength();
        if(cl > 0) {
            JkInputStream jkIS = ep.getInputStream();
            jkIS.setIsReadRequired(true);
            if(!delayInitialRead) {
                jkIS.receive();
            }
        }
    
        if (log.isTraceEnabled()) {
            log.trace(req.toString());
         }

        return OK;
    }
        
    private int decodeAttributes( MsgContext ep, Msg msg, Request req,
                                  MessageBytes tmpMB) {
        boolean moreAttr=true;

        while( moreAttr ) {
            byte attributeCode=msg.getByte();
            if( attributeCode == AjpConstants.SC_A_ARE_DONE )
                return 200;

            /* Special case ( XXX in future API make it separate type !)
             */
            if( attributeCode == AjpConstants.SC_A_SSL_KEY_SIZE ) {
                // Bug 1326: it's an Integer.
                req.setAttribute(SSLSupport.KEY_SIZE_KEY,
                                 new Integer( msg.getInt()));
               //Integer.toString(msg.getInt()));
            }

            if( attributeCode == AjpConstants.SC_A_REQ_ATTRIBUTE ) {
                // 2 strings ???...
                msg.getBytes( tmpMB );
                String n=tmpMB.toString();
                msg.getBytes( tmpMB );
                String v=tmpMB.toString();
                req.setAttribute(n, v );
                if(log.isTraceEnabled())
                    log.trace("jk Attribute set " + n + "=" + v);
            }


            // 1 string attributes
            switch(attributeCode) {
            case AjpConstants.SC_A_CONTEXT      :
                msg.getBytes( tmpMB );
                // nothing
                break;
                
            case AjpConstants.SC_A_SERVLET_PATH :
                msg.getBytes( tmpMB );
                // nothing 
                break;
                
            case AjpConstants.SC_A_REMOTE_USER  :
                if( tomcatAuthentication ) {
                    // ignore server
                    msg.getBytes( tmpMB );
                } else {
                    msg.getBytes(req.getRemoteUser());
                }
                break;
                
            case AjpConstants.SC_A_AUTH_TYPE    :
                if( tomcatAuthentication ) {
                    // ignore server
                    msg.getBytes( tmpMB );
                } else {
                    msg.getBytes(req.getAuthType());
                }
                break;
                
            case AjpConstants.SC_A_QUERY_STRING :
                msg.getBytes(req.queryString());
                break;
                
            case AjpConstants.SC_A_JVM_ROUTE    :
                msg.getBytes(req.instanceId());
                break;
                
            case AjpConstants.SC_A_SSL_CERT     :
                req.scheme().setString( "https" );
                // Transform the string into certificate.
                MessageBytes tmpMB2 = (MessageBytes)req.getNote(WorkerEnv.SSL_CERT_NOTE);
                if(tmpMB2 == null) {
                    tmpMB2 = MessageBytes.newInstance();
                    req.setNote(WorkerEnv.SSL_CERT_NOTE, tmpMB2);
                }
                // SSL certificate extraction is costy, moved to JkCoyoteHandler
                msg.getBytes(tmpMB2);
                break;
                
            case AjpConstants.SC_A_SSL_CIPHER   :
                req.scheme().setString( "https" );
                msg.getBytes(tmpMB);
                req.setAttribute(SSLSupport.CIPHER_SUITE_KEY,
                                 tmpMB.toString());
                break;
                
            case AjpConstants.SC_A_SSL_SESSION  :
                req.scheme().setString( "https" );
                msg.getBytes(tmpMB);
                req.setAttribute(SSLSupport.SESSION_ID_KEY, 
                                  tmpMB.toString());
                break;
                
            case AjpConstants.SC_A_SECRET  :
                msg.getBytes(tmpMB);
                String secret=tmpMB.toString();
                if(log.isTraceEnabled())
                    log.trace("Secret: " + secret );
                // endpoint note
                ep.setNote( secretNote, secret );
                break;
                
            case AjpConstants.SC_A_STORED_METHOD:
                msg.getBytes(req.method()); 
                break;
                
            default:
                break; // ignore, we don't know about it - backward compat
            }
        }
        return 200;
    }
    
    private void decodeHeaders( MsgContext ep, Msg msg, Request req,
                                MessageBytes tmpMB ) {
        // Decode headers
        MimeHeaders headers = req.getMimeHeaders();

        int hCount = msg.getInt();
        for(int i = 0 ; i < hCount ; i++) {
            String hName = null;

            // Header names are encoded as either an integer code starting
            // with 0xA0, or as a normal string (in which case the first
            // two bytes are the length).
            int isc = msg.peekInt();
            int hId = isc & 0xFF;

            MessageBytes vMB=null;
            isc &= 0xFF00;
            if(0xA000 == isc) {
                msg.getInt(); // To advance the read position
                hName = AjpConstants.headerTransArray[hId - 1];
                vMB=headers.addValue( hName );
            } else {
                // reset hId -- if the header currently being read
                // happens to be 7 or 8 bytes long, the code below
                // will think it's the content-type header or the
                // content-length header - SC_REQ_CONTENT_TYPE=7,
                // SC_REQ_CONTENT_LENGTH=8 - leading to unexpected
                // behaviour.  see bug 5861 for more information.
                hId = -1;
                msg.getBytes( tmpMB );
                ByteChunk bc=tmpMB.getByteChunk();
                vMB=headers.addValue( bc.getBuffer(),
                                      bc.getStart(), bc.getLength() );
            }

            msg.getBytes(vMB);

            if (hId == AjpConstants.SC_REQ_CONTENT_LENGTH ||
                (hId == -1 && tmpMB.equalsIgnoreCase("Content-Length"))) {
                // just read the content-length header, so set it
                req.setContentLength( vMB.getInt() );
            } else if (hId == AjpConstants.SC_REQ_CONTENT_TYPE ||
                (hId == -1 && tmpMB.equalsIgnoreCase("Content-Type"))) {
                // just read the content-type header, so set it
                ByteChunk bchunk = vMB.getByteChunk();
                req.contentType().setBytes(bchunk.getBytes(),
                                           bchunk.getOffset(),
                                           bchunk.getLength());
            }
        }
    }

    /**
     * Parse host.
     */
    private void parseHost(MessageBytes valueMB, Request request) 
        throws IOException {

        if (valueMB == null || valueMB.isNull()) {
            // HTTP/1.0
            // Default is what the socket tells us. Overriden if a host is 
            // found/parsed
            request.setServerPort(request.getLocalPort());
            request.serverName().duplicate(request.localName());
            return;
        }

        ByteChunk valueBC = valueMB.getByteChunk();
        byte[] valueB = valueBC.getBytes();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();
        int colonPos = -1;
        CharChunk hostNameC = (CharChunk)request.getNote(HOSTBUFFER);
        if(hostNameC == null) {
            hostNameC = new CharChunk(valueL);
            request.setNote(HOSTBUFFER, hostNameC);
        }
        hostNameC.recycle();

        boolean ipv6 = (valueB[valueS] == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            char b = (char) valueB[i + valueS];
            hostNameC.append(b);
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }

        if (colonPos < 0) {
            if (request.scheme().equalsIgnoreCase("https")) {
                // 80 - Default HTTTP port
                request.setServerPort(443);
            } else {
                // 443 - Default HTTPS port
                request.setServerPort(80);
            }
            request.serverName().setChars(hostNameC.getChars(), 
                                          hostNameC.getStart(), 
                                          hostNameC.getLength());
        } else {

            request.serverName().setChars(hostNameC.getChars(), 
                                          hostNameC.getStart(), colonPos);

            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = HexUtils.DEC[(int) valueB[i + valueS]];
                if (charValue == -1) {
                    // Invalid character
                    throw new CharConversionException("Invalid char in port: " + valueB[i + valueS]); 
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            request.setServerPort(port);

        }

    }

}
