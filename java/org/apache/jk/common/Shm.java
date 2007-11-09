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

import java.io.IOException;
import java.util.Vector;

import org.apache.jk.apr.AprImpl;
import org.apache.jk.core.Msg;
import org.apache.jk.core.MsgContext;
import org.apache.jk.core.WorkerEnv;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.C2BConverter;

/* The code is a bit confusing at this moment - the class is used as
   a Bean, or ant Task, or CLI - i.e. you set properties and call execute.

   That's different from the rest of jk handlers wich are stateless ( but
   similar with Coyote-http ).
*/


/** Handle the shared memory objects.
 *
 * @author Costin Manolache
 */
public class Shm extends JniHandler {
    String file="/tmp/shm.file";
    int size;
    String host="localhost";
    int port=8009;
    String unixSocket;

    boolean help=false;
    boolean unregister=false;
    boolean reset=false;
    String dumpFile=null;

    Vector groups=new Vector();
    
    // Will be dynamic ( getMethodId() ) after things are stable 
    static final int SHM_WRITE_SLOT=2;
    static final int SHM_RESET=5;
    static final int SHM_DUMP=6;
    
    public Shm() {
    }

    /** Scoreboard location
     */
    public void setFile( String f ) {
        file=f;
    }

    /** Copy the scoreboard in a file for debugging
     *  Will also log a lot of information about what's in the scoreboard.
     */
    public void setDump( String dumpFile ) {
        this.dumpFile=dumpFile;
    }
    
    /** Size. Used only if the scoreboard is to be created.
     */
    public void setSize( int size ) {
        this.size=size;
    }

    /** Set this to get the scoreboard reset.
     *  The shm segment will be destroyed and a new one created,
     *  with the provided size.
     *
     *  Requires "file" and "size".
     */
    public void setReset(boolean b) {
        reset=true;
    }

    /** Ajp13 host
     */
    public void setHost( String host ) {
        this.host=host;
    }

    /** Mark this instance as belonging to a group
     */
    public void setGroup( String grp ) {
        groups.addElement( grp );
    }

    /** Ajp13 port
     */
    public void setPort( int port ) {
        this.port=port;
    }

    /** Unix socket where tomcat is listening.
     *  Use it only if tomcat is on the same host, of course
     */
    public void setUnixSocket( String unixSocket  ) {
        this.unixSocket=unixSocket;
    }

    /** Set this option to mark the tomcat instance as
        'down', so apache will no longer forward messages to it.
        Note that requests with a session will still try this
        host first.

        This can be used to implement gracefull shutdown.

        Host and port are still required, since they are used
        to identify tomcat.
    */
    public void setUnregister( boolean unregister  ) {
        this.unregister=true;
    }
    
    public void init() throws IOException {
        super.initNative( "shm" );
        if( apr==null ) return;
        if( file==null ) {
            log.error("No shm file, disabling shared memory");
            apr=null;
            return;
        }

        // Set properties and call init.
        setNativeAttribute( "file", file );
        if( size > 0 )
            setNativeAttribute( "size", Integer.toString( size ) );
        
        initJkComponent();
    }

    public void resetScoreboard() throws IOException {
        if( apr==null ) return;
        MsgContext mCtx=createMsgContext();
        Msg msg=(Msg)mCtx.getMsg(0);
        msg.reset();

        msg.appendByte( SHM_RESET );
        
        this.invoke( msg, mCtx );
    }

    public void dumpScoreboard(String fname) throws IOException {
        if( apr==null ) return;
        MsgContext mCtx=createMsgContext();
        Msg msg=(Msg)mCtx.getMsg(0);
        C2BConverter c2b=mCtx.getConverter();
        msg.reset();

        msg.appendByte( SHM_DUMP );

        appendString( msg, fname, c2b);
        
        this.invoke( msg, mCtx );
    }

    /** Register a tomcat instance
     *  XXX make it more flexible
     */
    public void registerTomcat(String host, int port, String unixDomain)
        throws IOException
    {
        String instanceId=host+":" + port;

        String slotName="TOMCAT:" + instanceId;
        MsgContext mCtx=createMsgContext();
        Msg msg=(Msg)mCtx.getMsg(0);
        msg.reset();
        C2BConverter c2b=mCtx.getConverter();
        
        msg.appendByte( SHM_WRITE_SLOT );
        appendString( msg, slotName, c2b );

        int channelCnt=1;
        if( unixDomain != null ) channelCnt++;

        // number of groups. 0 means the default lb.
        msg.appendInt( groups.size() );
        for( int i=0; i<groups.size(); i++ ) {
            appendString( msg, (String)groups.elementAt( i ), c2b);
            appendString( msg, instanceId, c2b);
        }
        
        // number of channels for this instance
        msg.appendInt( channelCnt );
        
        // The body:
        appendString(msg, "channel.socket:" + host + ":" + port, c2b );
        msg.appendInt( 1 );
        appendString(msg, "tomcatId", c2b);
        appendString(msg, instanceId, c2b);

        if( unixDomain != null ) {
            appendString(msg, "channel.apr:" + unixDomain, c2b );
            msg.appendInt(1);
            appendString(msg, "tomcatId", c2b);
            appendString(msg, instanceId, c2b);
        }

        if (log.isDebugEnabled())
            log.debug("Register " + instanceId );
        this.invoke( msg, mCtx );
    }

    public void unRegisterTomcat(String host, int port)
        throws IOException
    {
        String slotName="TOMCAT:" + host + ":" + port;
        MsgContext mCtx=createMsgContext();
        Msg msg=(Msg)mCtx.getMsg(0);
        msg.reset();
        C2BConverter c2b=mCtx.getConverter();
        
        msg.appendByte( SHM_WRITE_SLOT );
        appendString( msg, slotName, c2b );

        // number of channels for this instance
        msg.appendInt( 0 );
        msg.appendInt( 0 );
        
        if (log.isDebugEnabled())
            log.debug("UnRegister " + slotName );
        this.invoke( msg, mCtx );
    }

    public void destroy() throws IOException {
        destroyJkComponent();
    }

    
    public  int invoke(Msg msg, MsgContext ep )
        throws IOException
    {
        if( apr==null ) return 0;
        log.debug("ChannelShm.invoke: "  + ep );
        super.nativeDispatch( msg, ep, JK_HANDLE_SHM_DISPATCH, 0 );
        return 0;
    }    

    private static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( Shm.class );

    
    //-------------------- Main - use the shm functions from ant or CLI ------

    /** Local initialization - for standalone use
     */
    public void initCli() throws IOException {
        WorkerEnv wEnv=new WorkerEnv();
        AprImpl apr=new AprImpl();
        wEnv.addHandler( "apr", apr );
        wEnv.addHandler( "shm", this );
        apr.init();
        if( ! apr.isLoaded() ) {
            log.error( "No native support. " +
                       "Make sure libapr.so and libjkjni.so are available in LD_LIBRARY_PATH");
            return;
        }
    }
    
    public void execute() {
        try {
            if( help ) return;
            initCli();
            init();

            if( reset ) {
                resetScoreboard();
            } else if( dumpFile!=null ) {
                dumpScoreboard(dumpFile);
            } else if( unregister ) {
                unRegisterTomcat( host, port );
            } else {
                registerTomcat( host, port, unixSocket );
            }
        } catch (Exception ex ) {
            log.error( "Error executing Shm", ex);
        }
    }

    public void setHelp( boolean b ) {
        if (log.isDebugEnabled()) {
            log.debug("Usage: ");
            log.debug("  Shm [OPTIONS]");
            log.debug("");
            log.debug("  -file SHM_FILE");
            log.debug("  -group GROUP ( can be specified multiple times )");
            log.debug("  -host HOST");
            log.debug("  -port PORT");
            log.debug("  -unixSocket UNIX_FILE");
            //        log.debug("  -priority XXX");
            //        log.debug("  -lbFactor XXX");
        }
        help=true;
        return;
    }
    
    public static void main( String args[] ) {
        try {
            Shm shm=new Shm();

            if( args.length == 0 ||
                ( "-?".equals(args[0]) ) ) {
                shm.setHelp( true );
                return;
            }

            IntrospectionUtils.processArgs( shm, args);
            shm.execute();
        } catch( Exception ex ) {
            ex.printStackTrace();
        }
    }
}
