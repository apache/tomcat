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

package org.apache.jk.common;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import javax.management.MBeanServer;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.Attribute;
import javax.management.ObjectName;

import org.apache.jk.core.JkHandler;
import org.apache.commons.modeler.Registry;
import org.apache.commons.modeler.BaseModelMBean;
import org.apache.commons.modeler.ManagedBean;
import org.apache.commons.modeler.AttributeInfo;
import org.apache.commons.modeler.OperationInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A small mbean that will act as a proxy for mod_jk2.
 *
 * For efficiency, it'll get bulk results and cache them - you
 * can force an update by calling the refreshAttributes and refreshMetadata
 * operations on this mbean.
 *
 * TODO: implement the user/pass auth ( right now you must use IP based security )
 * TODO: eventually support https
 * TODO: support for metadata ( mbean-descriptors ) for description and type conversions
 * TODO: filter out trivial components ( mutexes, etc )
 *
 * @author Costin Manolache
 */
public class ModJkMX extends JkHandler
{
    private static Log log = LogFactory.getLog(ModJkMX.class);

    MBeanServer mserver;
    String webServerHost="localhost";
    int webServerPort=80;
    String statusPath="/jkstatus";
    String user;
    String pass;
    Registry reg;

    HashMap mbeans=new HashMap();
    long lastRefresh=0;
    long updateInterval=5000; // 5 sec - it's min time between updates

    public ModJkMX()
    {
    }

    /* -------------------- Public methods -------------------- */

    public String getWebServerHost() {
        return webServerHost;
    }

    public void setWebServerHost(String webServerHost) {
        this.webServerHost = webServerHost;
    }

    public int getWebServerPort() {
        return webServerPort;
    }

    public void setWebServerPort(int webServerPort) {
        this.webServerPort = webServerPort;
    }

    public long getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(long updateInterval) {
        this.updateInterval = updateInterval;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getStatusPath() {
        return statusPath;
    }

    public void setStatusPath(String statusPath) {
        this.statusPath = statusPath;
    }
    /* ==================== Start/stop ==================== */

    public void destroy() {
        try {
            // We should keep track of loaded beans and call stop.
            // Modeler should do it...
            Iterator mbeansIt=mbeans.values().iterator();
            MBeanServer mbserver = Registry.getRegistry(null, null).getMBeanServer();
            while( mbeansIt.hasNext()) {
                MBeanProxy proxy=(MBeanProxy)mbeansIt.next();
                Object ooname = proxy.getObjectName();
                if( ooname != null ) {
                    ObjectName oname = null;
                    if(ooname instanceof ObjectName) {
                        oname = (ObjectName)ooname;
                    } else if(ooname instanceof String) {
                        oname = new ObjectName((String)ooname);
                    }
                    if( oname != null ) {
                        mbserver.unregisterMBean(oname);
                    }
                }
            }
        } catch( Throwable t ) {
            log.error( "Destroy error", t );
        }
    }

    public void init() throws IOException {
        try {
            //if( log.isDebugEnabled() )
            log.info("init " + webServerHost + " " + webServerPort);
            reg=Registry.getRegistry(null, null);
            refreshMetadata();
            refreshAttributes();
        } catch( Throwable t ) {
            log.error( "Init error", t );
        }
    }

    public void start() throws IOException {
        if( reg==null)
            init();
    }

    /** Refresh the proxies, if updateInterval passed
     *
     */
    public void refresh()  {
        long time=System.currentTimeMillis();
        if( time - lastRefresh < updateInterval ) {
            return;
        }
        lastRefresh=time;
        refreshMetadata();
        refreshAttributes();
    }

    public void refreshAttributes()  {
        try {
            int cnt=0;
            // connect to apache, get a list of mbeans
            BufferedReader is=getStream( "dmp=*");
            if( is==null ) return;

            String name=null;
            String att=null;
            String val=null;
            while(true) {
                String line=is.readLine();
                if( line==null ) break;
                line=line.trim();
                if( "".equals(line) || line.startsWith("#") ) continue;

                // for each mbean, create a proxy
                if(log.isDebugEnabled())
                    log.debug("Read " + line);

                if(line.startsWith( "[")) {
                    name=line.substring(1);
                    if( name.endsWith("]")) {
                        name=name.substring(0, name.length()-1);
                    }
                }
                // Name/value pair
                int idx=line.indexOf('=');
                if( idx < 0 ) continue;
                att=line.substring(0, idx );
                val=line.substring(idx+1);

                if( log.isDebugEnabled())
                    log.debug("name: " + name + " att=" + att +
                            " val=" + val);

                MBeanProxy proxy=(MBeanProxy)mbeans.get(name);
                if( proxy==null ) {
                    log.info( "Unknown object " + name);
                } else {
                    proxy.update(att, val);
                    cnt++;
                }
            }
            log.info( "Refreshing attributes " + cnt);
        } catch( Exception ex ) {
            log.info("Error ", ex);
        }
    }

    /** connect to apache, get a list of mbeans
      */
    BufferedReader getStream(String qry) throws Exception {
        try {
            String path=statusPath + "?" + qry;
            URL url=new URL( "http", webServerHost, webServerPort, path);
            URLConnection urlc=url.openConnection();
            BufferedReader is=new BufferedReader(new InputStreamReader(urlc.getInputStream()));
            return is;
        } catch (IOException e) {
            log.info( "Can't connect to jkstatus " + webServerHost + ":" + webServerPort
            + " " + e.toString());
            return null;
        }
    }

    public void refreshMetadata() {
        try {
            int cnt=0;
            int newCnt=0;
            BufferedReader is=getStream("lst=*");
            if( is==null ) return;
            String name=null;
            String type=null;
            ArrayList getters=new ArrayList();
            ArrayList setters=new ArrayList();
            ArrayList methods=new ArrayList();
            while(true) {
                String line=is.readLine();
                if( log.isDebugEnabled())
                    log.debug("Read " + line);

                // end of section
                if( line == null || line.startsWith("[") ) {
                    if( name != null ) {
                        cnt++;
                        if( mbeans.get( name ) ==null ) {
                            // New component
                            newCnt++;
                            MBeanProxy mproxy=new MBeanProxy(this);
                            mproxy.init( name, getters, setters, methods);
                            mbeans.put( name, mproxy );
                        }
                        if( log.isDebugEnabled())
                            log.debug("mbean name: " + name + " type=" + type);

                        getters.clear();
                        setters.clear();
                        methods.clear();
                    }
                }
                // end of data
                if( line==null ) break;

                line=line.trim();
                if( "".equals( line ) || line.startsWith("#"))  continue;

                // for each mbean, create a proxy

                if(line.startsWith( "[") && line.endsWith("]")) {
                    name=line.substring(1, line.length()-1);
                }
                if(line.startsWith( "T=")) {
                    type=line.substring(2);
                }
                if( line.startsWith("G=")) {
                    getters.add(line.substring(2));
                }
                if( line.startsWith("S=")) {
                    setters.add(line.substring(2));
                }
                if( line.startsWith("M=")) {
                    methods.add(line.substring(2));
                }
            }
            log.info( "Refreshing metadata " + cnt + " " +  newCnt);
        } catch( Exception ex ) {
            log.info("Error ", ex);
        }
    }

    /** Use the same metadata, except that we replace the attribute
     * get/set methods.
     */
    static class MBeanProxy extends BaseModelMBean {
        private static Log log = LogFactory.getLog(MBeanProxy.class);

        String jkName;
        List getAttNames;
        List setAttNames;
        HashMap atts=new HashMap();
        ModJkMX jkmx;

        public MBeanProxy(ModJkMX jkmx) throws Exception {
            this.jkmx=jkmx;
        }

        void init( String name, List getters, List setters, List methods )
            throws Exception
        {
            if(log.isDebugEnabled())
                log.debug("Register " + name );
            int col=name.indexOf( ':' );
            this.jkName=name;
            String type=name.substring(0, col );
            String id=name.substring(col+1);
            id=id.replace('*','%');
            id=id.replace(':', '%');
            if( id.length() == 0 ) {
                id="default";
            }
            ManagedBean mbean= new ManagedBean();

            AttributeInfo ai=new AttributeInfo();
            ai.setName( "jkName" );
            ai.setType( "java.lang.String");
            ai.setWriteable(false);
            mbean.addAttribute(ai);

            for( int i=0; i<getters.size(); i++ ) {
                String att=(String)getters.get(i);
                // Register metadata
                ai=new AttributeInfo();
                ai.setName( att );
                ai.setType( "java.lang.String");
                if( ! setters.contains(att))
                    ai.setWriteable(false);
                mbean.addAttribute(ai);
            }
            for( int i=0; i<setters.size(); i++ ) {
                String att=(String)setters.get(i);
                if( getters.contains(att))
                    continue;
                // Register metadata
                ai=new AttributeInfo();
                ai.setName( att );
                ai.setType( "java.lang.String");
                ai.setReadable(false);
                mbean.addAttribute(ai);
            }
            for( int i=0; i<methods.size(); i++ ) {
                String att=(String)methods.get(i);
                // Register metadata
                OperationInfo oi=new OperationInfo();
                oi.setName( att );
                oi.setReturnType("void");
                mbean.addOperation(oi);
            }

            this.setModelMBeanInfo(mbean.createMBeanInfo());

            MBeanServer mserver=Registry.getRegistry(null, null).getMBeanServer();
            oname=new ObjectName("apache:type=" + type + ",id=" + id);
            mserver.registerMBean(this, oname);
        }

        private void update( String name, String val ) {
            log.debug( "Updating " + jkName + " " + name + " " + val);
            atts.put( name, val);
        }

        public Object getAttribute(String name)
            throws AttributeNotFoundException, MBeanException,
                ReflectionException {
            if( "jkName".equals( name )) {
                return jkName;
            }
            jkmx.refresh();
            return atts.get(name);
        }

        public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, MBeanException,
            ReflectionException
        {
            try {
                // we support only string values
                String val=(String)attribute.getValue();
                String name=attribute.getName();
                BufferedReader is=jkmx.getStream("set=" + jkName + "|" +
                        name + "|" + val);
                if( is==null ) return;
                String res=is.readLine();
                if( log.isDebugEnabled())
                    log.debug( "Setting " + jkName + " " + name + " result " + res);

                jkmx.refreshMetadata();
                jkmx.refreshAttributes();
            } catch( Exception ex ) {
                throw new MBeanException(ex);
            }
        }

        public Object invoke(String name, Object params[], String signature[])
            throws MBeanException, ReflectionException {
            try {
                // we support only string values
                BufferedReader is=jkmx.getStream("inv=" + jkName + "|" +
                        name );
                if( is==null ) return null;
                String res=is.readLine();
                if( log.isDebugEnabled())
                    log.debug( "Invoking " + jkName + " " + name + " result " + res);

                jkmx.refreshMetadata();
                jkmx.refreshAttributes();
            } catch( Exception ex ) {
                throw new MBeanException(ex);
            }
            return null;
        }

    }


}

