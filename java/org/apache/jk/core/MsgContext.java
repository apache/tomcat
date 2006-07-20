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

package org.apache.jk.core;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

import org.apache.tomcat.util.buf.C2BConverter;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.jk.common.JkInputStream;


/**
 *
 * @author Henri Gomez [hgomez@apache.org]
 * @author Dan Milstein [danmil@shore.net]
 * @author Keith Wannamaker [Keith@Wannamaker.org]
 * @author Kevin Seguin
 * @author Costin Manolache
 */
public class MsgContext implements ActionHook {
    private static org.apache.commons.logging.Log log =
        org.apache.commons.logging.LogFactory.getLog(MsgContext.class);
    private static org.apache.commons.logging.Log logTime=
        org.apache.commons.logging.LogFactory.getLog( "org.apache.jk.REQ_TIME" );

    private int type;
    private Object notes[]=new Object[32];
    private JkHandler next;
    private JkChannel source;
    private JkInputStream jkIS = new JkInputStream(this);
    private C2BConverter c2b;
    private Request req;
    private WorkerEnv wEnv;
    private Msg msgs[]=new Msg[10];
    private int status=0;
    // Control object
    private Object control;

    // Application managed, like notes
    private long timers[]=new long[20];
    
    // The context can be used by JNI components as well
    private long jkEndpointP;
    private long xEnvP;

    // Temp: use notes and dynamic strings
    public static final int TIMER_RECEIVED=0;
    public static final int TIMER_PRE_REQUEST=1;
    public static final int TIMER_POST_REQUEST=2;

    // Status codes
    public static final int JK_STATUS_NEW=0;
    public static final int JK_STATUS_HEAD=1;
    public static final int JK_STATUS_CLOSED=2;
    public static final int JK_STATUS_ERROR=3;

    public MsgContext() {
        try {
            c2b = new C2BConverter("iso-8859-1");
        } catch(IOException iex) {
            log.warn("Can't happen", iex);
        }
    }
    
    public final Object getNote( int id ) {
        return notes[id];
    }

    public final void setNote( int id, Object o ) {
        notes[id]=o;
    }

    /** The id of the chain */
    public final int getType() {
        return type;
    }

    public final void setType(int i) {
        type=i;
    }

    public final void setLong( int i, long l) {
        timers[i]=l;
    }
    
    public final long getLong( int i) {
        return timers[i];
    }
    
    // Common attributes ( XXX should be notes for flexibility ? )

    public final WorkerEnv getWorkerEnv() {
        return wEnv;
    }

    public final void setWorkerEnv( WorkerEnv we ) {
        this.wEnv=we;
    }
    
    public final JkChannel getSource() {
        return source;
    }
    
    public final void setSource(JkChannel ch) {
        this.source=ch;
    }

    public final int getStatus() {
        return status;
    }

    public final void setStatus( int s ) {
        status=s;
    }
    
    public final JkHandler getNext() {
        return next;
    }
    
    public final void setNext(JkHandler ch) {
        this.next=ch;
    }

    /** The high level request object associated with this context
     */
    public final void setRequest( Request req ) {
        this.req=req;
        req.setInputBuffer(jkIS);
        Response res = req.getResponse();
        res.setOutputBuffer(jkIS);
        res.setHook(this);
    }

    public final Request getRequest() {
        return req;
    }

    /** The context may store a number of messages ( buffers + marshalling )
     */
    public final Msg getMsg(int i) {
        return msgs[i];
    }

    public final void setMsg(int i, Msg msg) {
        this.msgs[i]=msg;
    }

    public final C2BConverter getConverter() {
        return c2b;
    }

    public final void setConverter(C2BConverter c2b) {
        this.c2b = c2b;
    }
    
    public final boolean isLogTimeEnabled() {
        return logTime.isDebugEnabled();
    }

    public JkInputStream getInputStream() {
        return jkIS;
    }

    /** Each context contains a number of byte[] buffers used for communication.
     *  The C side will contain a char * equivalent - both buffers are long-lived
     *  and recycled.
     *
     *  This will be called at init time. A long-lived global reference to the byte[]
     *  will be stored in the C context.
     */
    public byte[] getBuffer( int id ) {
        // We use a single buffer right now. 
        if( msgs[id]==null ) {
            return null;
        }
        return msgs[id].getBuffer();
    }

    /** Invoke a java hook. The xEnv is the representation of the current execution
     *  environment ( the jni_env_t * )
     */
    public int execute() throws IOException {
        int status=next.invoke(msgs[0], this);
        return status;
    }

    // -------------------- Jni support --------------------
    
    /** Store native execution context data when this handler is called
     *  from JNI. This will change on each call, represent temproary
     *  call data.
     */
    public void setJniEnv( long xEnvP ) {
            this.xEnvP=xEnvP;
    }

    public long getJniEnv() {
        return xEnvP;
    }
    
    /** The long-lived JNI context associated with this java context.
     *  The 2 share pointers to buffers and cache data to avoid expensive
     *  jni calls.
     */
    public void setJniContext( long cContext ) {
        this.jkEndpointP=cContext;
    }

    public long getJniContext() {
        return jkEndpointP;
    }

    public Object getControl() {
        return control;
    }

    public void setControl(Object control) {
        this.control = control;
    }

    // -------------------- Coyote Action implementation --------------------
    
    public void action(ActionCode actionCode, Object param) {
        if( actionCode==ActionCode.ACTION_COMMIT ) {
            if( log.isDebugEnabled() ) log.debug("COMMIT " );
            Response res=(Response)param;

            if(  res.isCommitted() ) {
                if( log.isDebugEnabled() )
                    log.debug("Response already committed " );
            } else {
                try {
                    jkIS.appendHead( res );
                } catch(IOException iex) {
                    log.warn("Unable to send headers",iex);
                    setStatus(JK_STATUS_ERROR);
                }
            }
        } else if( actionCode==ActionCode.ACTION_RESET ) {
            if( log.isDebugEnabled() )
                log.debug("RESET " );
            
        } else if( actionCode==ActionCode.ACTION_CLIENT_FLUSH ) {
            if( log.isDebugEnabled() ) log.debug("CLIENT_FLUSH " );
            try {
                source.flush( null, this );
            } catch(IOException iex) {
                // This is logged elsewhere, so debug only here
                log.debug("Error during flush",iex);
                Response res = (Response)param;
                res.setErrorException(iex);
                setStatus(JK_STATUS_ERROR);
            }
            
        } else if( actionCode==ActionCode.ACTION_CLOSE ) {
            if( log.isDebugEnabled() ) log.debug("CLOSE " );
            
            Response res=(Response)param;
            if( getStatus()== JK_STATUS_CLOSED || getStatus() == JK_STATUS_ERROR) {
                // Double close - it may happen with forward 
                if( log.isDebugEnabled() ) log.debug("Double CLOSE - forward ? " + res.getRequest().requestURI() );
                return;
            }
                 
            if( !res.isCommitted() )
                this.action( ActionCode.ACTION_COMMIT, param );
            try {            
                jkIS.endMessage();
            } catch(IOException iex) {
                log.warn("Error sending end packet",iex);
                setStatus(JK_STATUS_ERROR);
            }
            if(getStatus() != JK_STATUS_ERROR) {
                setStatus(JK_STATUS_CLOSED );
            }

            if( logTime.isDebugEnabled() ) 
                logTime(res.getRequest(), res);
        } else if( actionCode==ActionCode.ACTION_REQ_SSL_ATTRIBUTE ) {
            Request req=(Request)param;

            // Extract SSL certificate information (if requested)
            MessageBytes certString = (MessageBytes)req.getNote(WorkerEnv.SSL_CERT_NOTE);
            if( certString != null && !certString.isNull() ) {
                ByteChunk certData = certString.getByteChunk();
                ByteArrayInputStream bais = 
                    new ByteArrayInputStream(certData.getBytes(),
                                             certData.getStart(),
                                             certData.getLength());
 
                // Fill the first element.
                X509Certificate jsseCerts[] = null;
                try {
                    CertificateFactory cf =
                        CertificateFactory.getInstance("X.509");
                    X509Certificate cert = (X509Certificate)
                        cf.generateCertificate(bais);
                    jsseCerts =  new X509Certificate[1];
                    jsseCerts[0] = cert;
                } catch(java.security.cert.CertificateException e) {
                    log.error("Certificate convertion failed" , e );
                    return;
                }
 
                req.setAttribute(SSLSupport.CERTIFICATE_KEY, 
                                 jsseCerts);
            }
                
        } else if( actionCode==ActionCode.ACTION_REQ_HOST_ATTRIBUTE ) {
            Request req=(Request)param;

            // If remoteHost not set by JK, get it's name from it's remoteAddr
            if( req.remoteHost().isNull()) {
                try {
                    req.remoteHost().setString(InetAddress.getByName(
                                               req.remoteAddr().toString()).
                                               getHostName());
                } catch(IOException iex) {
                    if(log.isDebugEnabled())
                        log.debug("Unable to resolve "+req.remoteAddr());
                }
            }
        } else if( actionCode==ActionCode.ACTION_ACK ) {
            if( log.isTraceEnabled() )
                log.trace("ACK " );
        } else if ( actionCode == ActionCode.ACTION_REQ_SET_BODY_REPLAY ) {
            if( log.isTraceEnabled() )
                log.trace("Replay ");
            ByteChunk bc = (ByteChunk)param;
            jkIS.setReplay(bc);
        }
    }
    

    private void logTime(Request req, Response res ) {
        // called after the request
        //            org.apache.coyote.Request req=(org.apache.coyote.Request)param;
        //            Response res=req.getResponse();
        String uri=req.requestURI().toString();
        if( uri.indexOf( ".gif" ) >0 ) return;
        
        setLong( MsgContext.TIMER_POST_REQUEST, System.currentTimeMillis());
        long t1= getLong( MsgContext.TIMER_PRE_REQUEST ) -
            getLong( MsgContext.TIMER_RECEIVED );
        long t2= getLong( MsgContext.TIMER_POST_REQUEST ) -
            getLong( MsgContext.TIMER_PRE_REQUEST );
        
        logTime.debug("Time pre=" + t1 + "/ service=" + t2 + " " +
                      res.getContentLength() + " " + 
                      uri );
    }

    public void recycle() {
        jkIS.recycle();
    }
}
