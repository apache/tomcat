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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.jk.core.JkHandler;
import org.apache.jk.core.Msg;
import org.apache.jk.core.MsgContext;
import org.apache.jk.core.JkChannel;
import org.apache.jk.core.WorkerEnv;
import org.apache.coyote.Request;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.threads.ThreadPool;
import org.apache.tomcat.util.threads.ThreadPoolRunnable;

/** 
 * Accept ( and send ) TCP messages.
 *
 * @author Costin Manolache
 * @author Bill Barker
 * jmx:mbean name="jk:service=ChannelNioSocket"
 *            description="Accept socket connections"
 * jmx:notification name="org.apache.coyote.INVOKE
 * jmx:notification-handler name="org.apache.jk.JK_SEND_PACKET
 * jmx:notification-handler name="org.apache.jk.JK_RECEIVE_PACKET
 * jmx:notification-handler name="org.apache.jk.JK_FLUSH
 *
 * Jk can use multiple protocols/transports.
 * Various container adapters should load this object ( as a bean ),
 * set configurations and use it. Note that the connector will handle
 * all incoming protocols - it's not specific to ajp1x. The protocol
 * is abstracted by MsgContext/Message/Channel.
 *
 * A lot of the 'original' behavior is hardcoded - this uses Ajp13 wire protocol,
 * TCP, Ajp14 API etc.
 * As we add other protocols/transports/APIs this will change, the current goal
 * is to get the same level of functionality as in the original jk connector.
 *
 * XXX Make the 'message type' pluggable
 */
public class ChannelSocket extends JkHandler
    implements NotificationBroadcaster, JkChannel {
    private static org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog( ChannelSocket.class );

    private int startPort=8009;
    private int maxPort=8019; // 0 for backward compat.
    private int port=startPort;
    private InetAddress inet;
    private int serverTimeout;
    private boolean tcpNoDelay=true; // nodelay to true by default
    private int linger=100;
    private int socketTimeout;
    private int bufferSize = -1;
    private int packetSize = 8*1024;

    private long requestCount=0;
    
    ThreadPool tp=ThreadPool.createThreadPool(true);

    /* ==================== Tcp socket options ==================== */

    /**
     * jmx:managed-constructor description="default constructor"
     */
    public ChannelSocket() {
        // This should be integrated with the  domain setup
    }
    
    public ThreadPool getThreadPool() {
        return tp;
    }

    public long getRequestCount() {
        return requestCount;
    }
    
    /** Set the port for the ajp13 channel.
     *  To support seemless load balancing and jni, we treat this
     *  as the 'base' port - we'll try up until we find one that is not
     *  used. We'll also provide the 'difference' to the main coyote
     *  handler - that will be our 'sessionID' and the position in
     *  the scoreboard and the suffix for the unix domain socket.
     *
     * jmx:managed-attribute description="Port to listen" access="READ_WRITE"
     */
    public void setPort( int port ) {
        this.startPort=port;
        this.port=port;
        this.maxPort=port+10;
    }

    public int getPort() {
        return port;
    }

    public void setAddress(InetAddress inet) {
        this.inet=inet;
    }

    /**
     * jmx:managed-attribute description="Bind on a specified address" access="READ_WRITE"
     */
    public void setAddress(String inet) {
        try {
            this.inet= InetAddress.getByName( inet );
        } catch( Exception ex ) {
            log.error("Error parsing "+inet,ex);
        }
    }

    public String getAddress() {
        if( inet!=null)
            return inet.toString();
        return "/0.0.0.0";
    }

    /**
     * Sets the timeout in ms of the server sockets created by this
     * server. This method allows the developer to make servers
     * more or less responsive to having their server sockets
     * shut down.
     *
     * <p>By default this value is 1000ms.
     */
    public void setServerTimeout(int timeout) {
	this.serverTimeout = timeout;
    }
    public int getServerTimeout() {
        return serverTimeout;
    }

    public void setTcpNoDelay( boolean b ) {
	tcpNoDelay=b;
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay;
    }
    
    public void setSoLinger( int i ) {
	linger=i;
    }

    public int getSoLinger() {
        return linger;
    }
    
    public void setSoTimeout( int i ) {
	socketTimeout=i;
    }

    public int getSoTimeout() {
	return socketTimeout;
    }

    public void setMaxPort( int i ) {
        maxPort=i;
    }

    public int getMaxPort() {
        return maxPort;
    }

    public void setBufferSize(int bs) {
        bufferSize = bs;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setPacketSize(int ps) {
        if(ps < 8*1024) {
            ps = 8*1024;
        }
        packetSize = ps;
    }

    public int getPacketSize() {
        return packetSize;
    }

    /** At startup we'll look for the first free port in the range.
        The difference between this port and the beggining of the range
        is the 'id'.
        This is usefull for lb cases ( less config ).
    */
    public int getInstanceId() {
        return port-startPort;
    }

    /** If set to false, the thread pool will be created in
     *  non-daemon mode, and will prevent main from exiting
     */
    public void setDaemon( boolean b ) {
        tp.setDaemon( b );
    }

    public boolean getDaemon() {
        return tp.getDaemon();
    }


    public void setMaxThreads( int i ) {
        if( log.isDebugEnabled()) log.debug("Setting maxThreads " + i);
        tp.setMaxThreads(i);
    }
    
    public void setMinSpareThreads( int i ) {
        if( log.isDebugEnabled()) log.debug("Setting minSpareThreads " + i);
        tp.setMinSpareThreads(i);
    }
    
    public void setMaxSpareThreads( int i ) {
        if( log.isDebugEnabled()) log.debug("Setting maxSpareThreads " + i);
        tp.setMaxSpareThreads(i);
    }

    public int getMaxThreads() {
        return tp.getMaxThreads();   
    }
    
    public int getMinSpareThreads() {
        return tp.getMinSpareThreads();   
    }

    public int getMaxSpareThreads() {
        return tp.getMaxSpareThreads();   
    }

    public void setBacklog(int i) {
    }
    
    
    /* ==================== ==================== */
    ServerSocket sSocket;
    final int socketNote=1;
    final int isNote=2;
    final int osNote=3;
    final int notifNote=4;
    boolean paused = false;

    public void pause() throws Exception {
        synchronized(this) {
            paused = true;
            unLockSocket();
        }
    }

    public void resume() throws Exception {
        synchronized(this) {
            paused = false;
            notify();
        }
    }


    public void accept( MsgContext ep ) throws IOException {
        if( sSocket==null ) return;
        synchronized(this) {
            while(paused) {
                try{ 
                    wait();
                } catch(InterruptedException ie) {
                    //Ignore, since can't happen
                }
            }
        }
        Socket s=sSocket.accept();
        ep.setNote( socketNote, s );
        if(log.isDebugEnabled() )
            log.debug("Accepted socket " + s );

        try {
            setSocketOptions(s);
        } catch(SocketException sex) {
            log.debug("Error initializing Socket Options", sex);
        }
        
        requestCount++;

        InputStream is=new BufferedInputStream(s.getInputStream());
        OutputStream os;
        if( bufferSize > 0 )
            os = new BufferedOutputStream( s.getOutputStream(), bufferSize);
        else
            os = s.getOutputStream();
        ep.setNote( isNote, is );
        ep.setNote( osNote, os );
        ep.setControl( tp );
    }

    private void setSocketOptions(Socket s) throws SocketException {
        if( socketTimeout > 0 ) 
            s.setSoTimeout( socketTimeout );
        
        s.setTcpNoDelay( tcpNoDelay ); // set socket tcpnodelay state

        if( linger > 0 )
            s.setSoLinger( true, linger);
    }

    public void resetCounters() {
        requestCount=0;
    }

    /** Called after you change some fields at runtime using jmx.
        Experimental for now.
    */
    public void reinit() throws IOException {
        destroy();
        init();
    }

    /**
     * jmx:managed-operation
     */
    public void init() throws IOException {
        // Find a port.
        if (startPort == 0) {
            port = 0;
            if(log.isInfoEnabled())
                log.info("JK: ajp13 disabling channelSocket");
            running = true;
            return;
        }
        if (maxPort < startPort)
            maxPort = startPort;
        for( int i=startPort; i<=maxPort; i++ ) {
            try {
                if( inet == null ) {
                    sSocket = new ServerSocket( i, 0 );
                } else {
                    sSocket=new ServerSocket( i, 0, inet );
                }
                port=i;
                break;
            } catch( IOException ex ) {
                if(log.isInfoEnabled())
                    log.info("Port busy " + i + " " + ex.toString());
                continue;
            }
        }

        if( sSocket==null ) {
            log.error("Can't find free port " + startPort + " " + maxPort );
            return;
        }
        if(log.isInfoEnabled())
            log.info("JK: ajp13 listening on " + getAddress() + ":" + port );

        // If this is not the base port and we are the 'main' channleSocket and
        // SHM didn't already set the localId - we'll set the instance id
        if( "channelSocket".equals( name ) &&
            port != startPort &&
            (wEnv.getLocalId()==0) ) {
            wEnv.setLocalId(  port - startPort );
        }
        if( serverTimeout > 0 )
            sSocket.setSoTimeout( serverTimeout );

        // XXX Reverse it -> this is a notification generator !!
        if( next==null && wEnv!=null ) {
            if( nextName!=null )
                setNext( wEnv.getHandler( nextName ) );
            if( next==null )
                next=wEnv.getHandler( "dispatch" );
            if( next==null )
                next=wEnv.getHandler( "request" );
        }
        JMXRequestNote =wEnv.getNoteId( WorkerEnv.ENDPOINT_NOTE, "requestNote");
        running = true;

        // Run a thread that will accept connections.
        // XXX Try to find a thread first - not sure how...
        if( this.domain != null ) {
            try {
                tpOName=new ObjectName(domain + ":type=ThreadPool,name=" + 
                                       getChannelName());

                Registry.getRegistry(null, null)
                    .registerComponent(tp, tpOName, null);

                rgOName = new ObjectName
                    (domain+":type=GlobalRequestProcessor,name=" + getChannelName());
                Registry.getRegistry(null, null)
                    .registerComponent(global, rgOName, null);
            } catch (Exception e) {
                log.error("Can't register threadpool" );
            }
        }

        tp.start();
        SocketAcceptor acceptAjp=new SocketAcceptor(  this );
        tp.runIt( acceptAjp);

    }

    ObjectName tpOName;
    ObjectName rgOName;
    RequestGroupInfo global=new RequestGroupInfo();
    int JMXRequestNote;

    public void start() throws IOException{
        if( sSocket==null )
            init();
    }

    public void stop() throws IOException {
        destroy();
    }

    public void registerRequest(Request req, MsgContext ep, int count) {
        if(this.domain != null) {
            try {
                RequestInfo rp=req.getRequestProcessor();
                rp.setGlobalProcessor(global);
                ObjectName roname = new ObjectName
                    (getDomain() + ":type=RequestProcessor,worker="+
                     getChannelName()+",name=JkRequest" +count);
                ep.setNote(JMXRequestNote, roname);
                        
                Registry.getRegistry(null, null).registerComponent( rp, roname, null);
            } catch( Exception ex ) {
                log.warn("Error registering request");
            }
        }
    }

    public void open(MsgContext ep) throws IOException {
    }

    
    public void close(MsgContext ep) throws IOException {
        Socket s=(Socket)ep.getNote( socketNote );
        s.close();
    }

    private void unLockSocket() throws IOException {
        // Need to create a connection to unlock the accept();
        Socket s;
        InetAddress ladr = inet;

        if(port == 0)
            return;
        if (ladr == null || "0.0.0.0".equals(ladr.getHostAddress())) {
            ladr = InetAddress.getLocalHost();
        }
        s=new Socket(ladr, port );
        // setting soLinger to a small value will help shutdown the
        // connection quicker
        s.setSoLinger(true, 0);

	s.close();
    }

    public void destroy() throws IOException {
        running = false;
        try {
            /* If we disabled the channel return */
            if (port == 0)
                return;
            tp.shutdown();

	    if(!paused) {
		unLockSocket();
	    }

            sSocket.close(); // XXX?
            
            if( tpOName != null )  {
                Registry.getRegistry(null, null).unregisterComponent(tpOName);
            }
            if( rgOName != null ) {
                Registry.getRegistry(null, null).unregisterComponent(rgOName);
            }
        } catch(Exception e) {
            log.info("Error shutting down the channel " + port + " " +
                    e.toString());
            if( log.isDebugEnabled() ) log.debug("Trace", e);
        }
    }

    public int send( Msg msg, MsgContext ep)
        throws IOException    {
        msg.end(); // Write the packet header
        byte buf[]=msg.getBuffer();
        int len=msg.getLen();
        
        if(log.isTraceEnabled() )
            log.trace("send() " + len + " " + buf[4] );

        OutputStream os=(OutputStream)ep.getNote( osNote );
        os.write( buf, 0, len );
        return len;
    }

    public int flush( Msg msg, MsgContext ep)
        throws IOException    {
        if( bufferSize > 0 ) {
            OutputStream os=(OutputStream)ep.getNote( osNote );
            os.flush();
        }
        return 0;
    }

    public int receive( Msg msg, MsgContext ep )
        throws IOException    {
        if (log.isDebugEnabled()) {
            log.debug("receive() ");
        }

        byte buf[]=msg.getBuffer();
        int hlen=msg.getHeaderLength();
        
	// XXX If the length in the packet header doesn't agree with the
	// actual number of bytes read, it should probably return an error
	// value.  Also, callers of this method never use the length
	// returned -- should probably return true/false instead.

        int rd = this.read(ep, buf, 0, hlen );
        
        if(rd < 0) {
            // Most likely normal apache restart.
            // log.warn("Wrong message " + rd );
            return rd;
        }

        msg.processHeader();

        /* After processing the header we know the body
           length
        */
        int blen=msg.getLen();
        
	// XXX check if enough space - it's assert()-ed !!!
        
 	int total_read = 0;
        
        total_read = this.read(ep, buf, hlen, blen);
        
        if ((total_read <= 0) && (blen > 0)) {
            log.warn("can't read body, waited #" + blen);
            return  -1;
        }
        
        if (total_read != blen) {
             log.warn( "incomplete read, waited #" + blen +
                        " got only " + total_read);
            return -2;
        }
        
	return total_read;
    }
    
    /**
     * Read N bytes from the InputStream, and ensure we got them all
     * Under heavy load we could experience many fragmented packets
     * just read Unix Network Programming to recall that a call to
     * read didn't ensure you got all the data you want
     *
     * from read() Linux manual
     *
     * On success, the number of bytes read is returned (zero indicates end
     * of file),and the file position is advanced by this number.
     * It is not an error if this number is smaller than the number of bytes
     * requested; this may happen for example because fewer bytes
     * are actually available right now (maybe because we were close to
     * end-of-file, or because we are reading from a pipe, or  from  a
     * terminal),  or  because  read()  was interrupted by a signal.
     * On error, -1 is returned, and errno is set appropriately. In this
     * case it is left unspecified whether the file position (if any) changes.
     *
     **/
    public int read( MsgContext ep, byte[] b, int offset, int len)
        throws IOException    {
        InputStream is=(InputStream)ep.getNote( isNote );
        int pos = 0;
        int got;

        while(pos < len) {
            try {
                got = is.read(b, pos + offset, len - pos);
            } catch(SocketException sex) {
                if(pos > 0) {
                    log.info("Error reading data after "+pos+"bytes",sex);
                } else {
                    log.debug("Error reading data", sex);
                }
                got = -1;
            }
            if (log.isTraceEnabled()) {
                log.trace("read() " + b + " " + (b==null ? 0: b.length) + " " +
                          offset + " " + len + " = " + got );
            }

            // connection just closed by remote. 
            if (got <= 0) {
                // This happens periodically, as apache restarts
                // periodically.
                // It should be more gracefull ! - another feature for Ajp14
                // log.warn( "server has closed the current connection (-1)" );
                return -3;
            }

            pos += got;
        }
        return pos;
    }
    
    protected boolean running=true;
    
    /** Accept incoming connections, dispatch to the thread pool
     */
    void acceptConnections() {
        if( log.isDebugEnabled() )
            log.debug("Accepting ajp connections on " + port);
        while( running ) {
	    try{
                MsgContext ep=createMsgContext(packetSize);
                ep.setSource(this);
                ep.setWorkerEnv( wEnv );
                this.accept(ep);

                if( !running ) break;
                
                // Since this is a long-running connection, we don't care
                // about the small GC
                SocketConnection ajpConn=
                    new SocketConnection(this, ep);
                tp.runIt( ajpConn );
	    }catch(Exception ex) {
                if (running)
                    log.warn("Exception executing accept" ,ex);
	    }
        }
    }

    /** Process a single ajp connection.
     */
    void processConnection(MsgContext ep) {
        try {
            MsgAjp recv=new MsgAjp(packetSize);
            while( running ) {
                if(paused) { // Drop the connection on pause
                    break;
                }
                int status= this.receive( recv, ep );
                if( status <= 0 ) {
                    if( status==-3)
                        log.debug( "server has been restarted or reset this connection" );
                    else 
                        log.warn("Closing ajp connection " + status );
                    break;
                }
                ep.setLong( MsgContext.TIMER_RECEIVED, System.currentTimeMillis());
                
                ep.setType( 0 );
                // Will call next
                status= this.invoke( recv, ep );
                if( status!= JkHandler.OK ) {
                    log.warn("processCallbacks status " + status );
                    break;
                }
            }
        } catch( Exception ex ) {
            String msg = ex.getMessage();
            if( msg != null && msg.indexOf( "Connection reset" ) >= 0)
                log.debug( "Server has been restarted or reset this connection");
            else if (msg != null && msg.indexOf( "Read timed out" ) >=0 )
                log.debug( "connection timeout reached");            
            else
                log.error( "Error, processing connection", ex);
        } finally {
	    	/*
	    	 * Whatever happened to this connection (remote closed it, timeout, read error)
	    	 * the socket SHOULD be closed, or we may be in situation where the webserver
	    	 * will continue to think the socket is still open and will forward request
	    	 * to tomcat without receiving ever a reply
	    	 */
            try {
                this.close( ep );
            }
            catch( Exception e) {
                log.error( "Error, closing connection", e);
            }
            try{
                Request req = (Request)ep.getRequest();
                if( req != null ) {
                    ObjectName roname = (ObjectName)ep.getNote(JMXRequestNote);
                    if( roname != null ) {
                        Registry.getRegistry(null, null).unregisterComponent(roname);
                    }
                    req.getRequestProcessor().setGlobalProcessor(null);
                }
            } catch( Exception ee) {
                log.error( "Error, releasing connection",ee);
            }
        }
    }

    // XXX This should become handleNotification
    public int invoke( Msg msg, MsgContext ep ) throws IOException {
        int type=ep.getType();

        switch( type ) {
        case JkHandler.HANDLE_RECEIVE_PACKET:
            if( log.isDebugEnabled()) log.debug("RECEIVE_PACKET ?? ");
            return receive( msg, ep );
        case JkHandler.HANDLE_SEND_PACKET:
            return send( msg, ep );
        case JkHandler.HANDLE_FLUSH:
            return flush( msg, ep );
        }

        if( log.isDebugEnabled() )
            log.debug("Call next " + type + " " + next);

        // Send notification
        if( nSupport!=null ) {
            Notification notif=(Notification)ep.getNote(notifNote);
            if( notif==null ) {
                notif=new Notification("channelSocket.message", ep, requestCount );
                ep.setNote( notifNote, notif);
            }
            nSupport.sendNotification(notif);
        }

        if( next != null ) {
            return next.invoke( msg, ep );
        } else {
            log.info("No next ");
        }

        return OK;
    }
    
    public boolean isSameAddress(MsgContext ep) {
        Socket s=(Socket)ep.getNote( socketNote );
        return isSameAddress( s.getLocalAddress(), s.getInetAddress());
    }
    
    public String getChannelName() {
        String encodedAddr = "";
        if (inet != null && !"0.0.0.0".equals(inet.getHostAddress())) {
            encodedAddr = getAddress();
            if (encodedAddr.startsWith("/"))
                encodedAddr = encodedAddr.substring(1);
	    encodedAddr = URLEncoder.encode(encodedAddr) + "-";
        }
        return ("jk-" + encodedAddr + port);
    }
    
    /**
     * Return <code>true</code> if the specified client and server addresses
     * are the same.  This method works around a bug in the IBM 1.1.8 JVM on
     * Linux, where the address bytes are returned reversed in some
     * circumstances.
     *
     * @param server The server's InetAddress
     * @param client The client's InetAddress
     */
    public static boolean isSameAddress(InetAddress server, InetAddress client)
    {
	// Compare the byte array versions of the two addresses
	byte serverAddr[] = server.getAddress();
	byte clientAddr[] = client.getAddress();
	if (serverAddr.length != clientAddr.length)
	    return (false);
	boolean match = true;
	for (int i = 0; i < serverAddr.length; i++) {
	    if (serverAddr[i] != clientAddr[i]) {
		match = false;
		break;
	    }
	}
	if (match)
	    return (true);

	// Compare the reversed form of the two addresses
	for (int i = 0; i < serverAddr.length; i++) {
	    if (serverAddr[i] != clientAddr[(serverAddr.length-1)-i])
		return (false);
	}
	return (true);
    }

    public void sendNewMessageNotification(Notification notification) {
        if( nSupport!= null )
            nSupport.sendNotification(notification);
    }

    private NotificationBroadcasterSupport nSupport= null;

    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
            throws IllegalArgumentException
    {
        if( nSupport==null ) nSupport=new NotificationBroadcasterSupport();
        nSupport.addNotificationListener(listener, filter, handback);
    }

    public void removeNotificationListener(NotificationListener listener)
            throws ListenerNotFoundException
    {
        if( nSupport!=null)
            nSupport.removeNotificationListener(listener);
    }

    MBeanNotificationInfo notifInfo[]=new MBeanNotificationInfo[0];

    public void setNotificationInfo( MBeanNotificationInfo info[]) {
        this.notifInfo=info;
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return notifInfo;
    }

    static class SocketAcceptor implements ThreadPoolRunnable {
	ChannelSocket wajp;
    
	SocketAcceptor(ChannelSocket wajp ) {
	    this.wajp=wajp;
	}
	
	public Object[] getInitData() {
	    return null;
	}
	
	public void runIt(Object thD[]) {
	    wajp.acceptConnections();
	}
    }

    static class SocketConnection implements ThreadPoolRunnable {
	ChannelSocket wajp;
	MsgContext ep;

	SocketConnection(ChannelSocket wajp, MsgContext ep) {
	    this.wajp=wajp;
	    this.ep=ep;
	}


	public Object[] getInitData() {
	    return null;
	}
	
	public void runIt(Object perTh[]) {
	    wajp.processConnection(ep);
	    ep = null;
	}
    }

}

