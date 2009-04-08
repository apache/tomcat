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

package org.apache.jk.server;

import java.io.IOException;
import java.util.Iterator;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Constants;
import org.apache.jk.core.JkHandler;
import org.apache.jk.core.Msg;
import org.apache.jk.core.MsgContext;
import org.apache.tomcat.util.modeler.Registry;

/** Plugs Jk into Coyote. Must be named "type=JkHandler,name=container"
 *
 * jmx:notification-handler name="org.apache.jk.SEND_PACKET
 * jmx:notification-handler name="org.apache.coyote.ACTION_COMMIT
 */
public class JkCoyoteHandler extends JkHandler implements ProtocolHandler {
    protected static org.apache.juli.logging.Log log 
        = org.apache.juli.logging.LogFactory.getLog(JkCoyoteHandler.class);
    // Set debug on this logger to see the container request time

    // ----------------------------------------------------------- DoPrivileged
    private boolean paused = false;
    int epNote;
    Adapter adapter;
    protected JkMain jkMain=null;

    /** Set a property. Name is a "component.property". JMX should
     * be used instead.
     */
    public void setProperty( String name, String value ) {
        if( log.isTraceEnabled())
            log.trace("setProperty " + name + " " + value );
        getJkMain().setProperty( name, value );
        properties.put( name, value );
    }

    public String getProperty( String name ) {
        return properties.getProperty(name) ;
    }

    public Iterator getAttributeNames() {
       return properties.keySet().iterator();
    }

    /** Pass config info
     */
    public void setAttribute( String name, Object value ) {
        if( log.isDebugEnabled())
            log.debug("setAttribute " + name + " " + value );
        if( value instanceof String )
            this.setProperty( name, (String)value );
    }

    /**
     * Retrieve config info.
     * Primarily for use with the admin webapp.
     */   
    public Object getAttribute( String name ) {
        return getJkMain().getProperty(name);
    }

    /** The adapter, used to call the connector 
     */
    public void setAdapter(Adapter adapter) {
        this.adapter=adapter;
    }

    public Adapter getAdapter() {
        return adapter;
    }

    public JkMain getJkMain() {
        if( jkMain == null ) {
            jkMain=new JkMain();
            jkMain.setWorkerEnv(wEnv);
            
        }
        return jkMain;
    }
    
    boolean started=false;
    
    /** Start the protocol
     */
    public void init() {
        if( started ) return;

        started=true;
        
        if( wEnv==null ) {
            // we are probably not registered - not very good.
            wEnv=getJkMain().getWorkerEnv();
            wEnv.addHandler("container", this );
        }

        try {
            // jkMain.setJkHome() XXX;
            
            getJkMain().init();

        } catch( Exception ex ) {
            log.error("Error during init",ex);
        }
    }

    public void start() {
        try {
            if( oname != null && getJkMain().getDomain() == null) {
                try {
                    ObjectName jkmainOname = 
                        new ObjectName(oname.getDomain() + ":type=JkMain");
                    Registry.getRegistry(null, null)
                        .registerComponent(getJkMain(), jkmainOname, "JkMain");
                } catch (Exception e) {
                    log.error( "Error registering jkmain " + e );
                }
            }
            getJkMain().start();
        } catch( Exception ex ) {
            log.error("Error during startup",ex);
        }
    }

    public void pause() throws Exception {
        if(!paused) {
            paused = true;
            getJkMain().pause();
        }
    }

    public void resume() throws Exception {
        if(paused) {
            paused = false;
            getJkMain().resume();
        }
    }

    public void destroy() {
        if( !started ) return;

        started = false;
        getJkMain().stop();
    }

    
    // -------------------- Jk handler implementation --------------------
    // Jk Handler mehod
    public int invoke( Msg msg, MsgContext ep ) 
        throws IOException {
        if( ep.isLogTimeEnabled() ) 
            ep.setLong( MsgContext.TIMER_PRE_REQUEST, System.currentTimeMillis());
        
        Request req=ep.getRequest();
        Response res=req.getResponse();

        if( log.isDebugEnabled() )
            log.debug( "Invoke " + req + " " + res + " " + req.requestURI().toString());
        
        res.setNote( epNote, ep );
        ep.setStatus( MsgContext.JK_STATUS_HEAD );
        RequestInfo rp = req.getRequestProcessor();
        rp.setStage(Constants.STAGE_SERVICE);
        try {
            adapter.service( req, res );
        } catch( Exception ex ) {
            log.info("Error servicing request " + req,ex);
        }
        if(ep.getStatus() != MsgContext.JK_STATUS_CLOSED) {
            res.finish();
        }

        req.updateCounters();
        req.recycle();
        res.recycle();
        ep.recycle();
        if( ep.getStatus() == MsgContext.JK_STATUS_ERROR ) {
            return ERROR;
        }
        ep.setStatus( MsgContext.JK_STATUS_NEW );
        rp.setStage(Constants.STAGE_KEEPALIVE);
        return OK;
    }


    public ObjectName preRegister(MBeanServer server,
                                  ObjectName oname) throws Exception
    {
        // override - we must be registered as "container"
        this.name="container";        
        return super.preRegister(server, oname);
    }
}
