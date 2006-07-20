/*
 * Copyright 1999-2001,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.core;


import java.io.File;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.Service;
import org.apache.catalina.realm.JAASRealm;
import org.apache.catalina.util.ServerInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.modeler.modules.MbeansSource;

/**
 * Standard implementation of the <b>Engine</b> interface.  Each
 * child container must be a Host implementation to process the specific
 * fully qualified host name of that virtual host. <br/>
 * You can set the jvmRoute direct or with the System.property <b>jvmRoute</b>.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 303667 $ $Date: 2005-01-29 20:41:16 +0100 (sam., 29 janv. 2005) $
 */

public class StandardEngine
    extends ContainerBase
    implements Engine {

    private static Log log = LogFactory.getLog(StandardEngine.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardEngine component with the default basic Valve.
     */
    public StandardEngine() {

        super();
        pipeline.setBasic(new StandardEngineValve());
        /* Set the jmvRoute using the system property jvmRoute */
        try {
            setJvmRoute(System.getProperty("jvmRoute"));
        } catch(Exception ex) {
        }
        // By default, the engine will hold the reloading thread
        backgroundProcessorDelay = 10;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Host name to use when no server host, or an unknown host,
     * is specified in the request.
     */
    private String defaultHost = null;


    /**
     * The descriptive information string for this implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardEngine/1.0";


    /**
     * The <code>Service</code> that owns this Engine, if any.
     */
    private Service service = null;

    /** Allow the base dir to be specified explicitely for
     * each engine. In time we should stop using catalina.base property -
     * otherwise we loose some flexibility.
     */
    private String baseDir = null;

    /** Optional mbeans config file. This will replace the "hacks" in
     * jk and ServerListener. The mbeans file will support (transparent) 
     * persistence - soon. It'll probably replace jk2.properties and could
     * replace server.xml. Of course - the same beans could be loaded and 
     * managed by an external entity - like the embedding app - which
     *  can use a different persistence mechanism.
     */ 
    private String mbeansFile = null;
    
    /** Mbeans loaded by the engine.  
     */ 
    private List mbeans;
    

    /**
     * The JVM Route ID for this Tomcat instance. All Route ID's must be unique
     * across the cluster.
     */
    private String jvmRouteId;


    // ------------------------------------------------------------- Properties

    /** Provide a default in case no explicit configuration is set
     *
     * @return configured realm, or a JAAS realm by default
     */
    public Realm getRealm() {
        Realm configured=super.getRealm();
        // If no set realm has been called - default to JAAS
        // This can be overriden at engine, context and host level  
        if( configured==null ) {
            configured=new JAASRealm();
            this.setRealm( configured );
        }
        return configured;
    }


    /**
     * Return the default host.
     */
    public String getDefaultHost() {

        return (defaultHost);

    }


    /**
     * Set the default host.
     *
     * @param host The new default host
     */
    public void setDefaultHost(String host) {

        String oldDefaultHost = this.defaultHost;
        if (host == null) {
            this.defaultHost = null;
        } else {
            this.defaultHost = host.toLowerCase();
        }
        support.firePropertyChange("defaultHost", oldDefaultHost,
                                   this.defaultHost);

    }
    
    public void setName(String name ) {
        if( domain != null ) {
            // keep name==domain, ignore override
            // we are already registered
            super.setName( domain );
            return;
        }
        // The engine name is used as domain
        domain=name; // XXX should we set it in init() ? It shouldn't matter
        super.setName( name );
    }


    /**
     * Set the cluster-wide unique identifier for this Engine.
     * This value is only useful in a load-balancing scenario.
     * <p>
     * This property should not be changed once it is set.
     */
    public void setJvmRoute(String routeId) {
        jvmRouteId = routeId;
    }


    /**
     * Retrieve the cluster-wide unique identifier for this Engine.
     * This value is only useful in a load-balancing scenario.
     */
    public String getJvmRoute() {
        return jvmRouteId;
    }


    /**
     * Return the <code>Service</code> with which we are associated (if any).
     */
    public Service getService() {

        return (this.service);

    }


    /**
     * Set the <code>Service</code> with which we are associated (if any).
     *
     * @param service The service that owns this Engine
     */
    public void setService(Service service) {
        this.service = service;
    }

    public String getMbeansFile() {
        return mbeansFile;
    }

    public void setMbeansFile(String mbeansFile) {
        this.mbeansFile = mbeansFile;
    }

    public String getBaseDir() {
        if( baseDir==null ) {
            baseDir=System.getProperty("catalina.base");
        }
        if( baseDir==null ) {
            baseDir=System.getProperty("catalina.home");
        }
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Host.
     *
     * @param child Child container to be added
     */
    public void addChild(Container child) {

        if (!(child instanceof Host))
            throw new IllegalArgumentException
                (sm.getString("standardEngine.notHost"));
        super.addChild(child);

    }


    /**
     * Return descriptive information about this Container implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }

    /**
     * Disallow any attempt to set a parent for this Container, since an
     * Engine is supposed to be at the top of the Container hierarchy.
     *
     * @param container Proposed parent Container
     */
    public void setParent(Container container) {

        throw new IllegalArgumentException
            (sm.getString("standardEngine.notParent"));

    }


    private boolean initialized=false;
    
    public void init() {
        if( initialized ) return;
        initialized=true;

        if( oname==null ) {
            // not registered in JMX yet - standalone mode
            try {
                if (domain==null) {
                    domain=getName();
                }
                if(log.isDebugEnabled())
                    log.debug( "Register " + domain );
                oname=new ObjectName(domain + ":type=Engine");
                controller=oname;
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
            } catch( Throwable t ) {
                log.info("Error registering ", t );
            }
        }

        if( mbeansFile == null ) {
            String defaultMBeansFile=getBaseDir() + "/conf/tomcat5-mbeans.xml";
            File f=new File( defaultMBeansFile );
            if( f.exists() ) mbeansFile=f.getAbsolutePath();
        }
        if( mbeansFile != null ) {
            readEngineMbeans();
        }
        if( mbeans != null ) {
            try {
                Registry.getRegistry(null, null).invoke(mbeans, "init", false);
            } catch (Exception e) {
                log.error("Error in init() for " + mbeansFile, e);
            }
        }
        
        // not needed since the following if statement does the same thing the right way
        // remove later after checking
        //if( service==null ) {
        //    try {
        //        ObjectName serviceName=getParentName();        
        //        if( mserver.isRegistered( serviceName )) {
        //            log.info("Registering with the service ");
        //            try {
        //                mserver.invoke( serviceName, "setContainer",
        //                        new Object[] { this },
        //                        new String[] { "org.apache.catalina.Container" } );
        //            } catch( Exception ex ) {
        //               ex.printStackTrace();
        //            }
        //        }
        //    } catch( Exception ex ) {
        //        log.error("Error registering with service ");
        //    }
        //}
        
        if( service==null ) {
            // for consistency...: we are probably in embeded mode
            try {
                service=new StandardService();
                service.setContainer( this );
                service.initialize();
            } catch( Throwable t ) {
                log.error(t);
            }
        }
        
    }
    
    public void destroy() throws LifecycleException {
        if( ! initialized ) return;
        initialized=false;
        
        // if we created it, make sure it's also destroyed
        // this call implizit this.stop()
        ((StandardService)service).destroy();

        if( mbeans != null ) {
            try {
                Registry.getRegistry(null, null)
                    .invoke(mbeans, "destroy", false);
            } catch (Exception e) {
                log.error(sm.getString("standardEngine.unregister.mbeans.failed" ,mbeansFile), e);
            }
        }
        // 
        if( mbeans != null ) {
            try {
                for( int i=0; i<mbeans.size() ; i++ ) {
                    Registry.getRegistry(null, null)
                        .unregisterComponent((ObjectName)mbeans.get(i));
                }
            } catch (Exception e) {
                log.error(sm.getString("standardEngine.unregister.mbeans.failed", mbeansFile), e);
            }
        }
        
        // force all metadata to be reloaded.
        // That doesn't affect existing beans. We should make it per
        // registry - and stop using the static.
        Registry.getRegistry(null, null).resetMetadata();
        
    }
    
    /**
     * Start this Engine component.
     *
     * @exception LifecycleException if a startup error occurs
     */
    public void start() throws LifecycleException {
        if( started ) {
            return;
        }
        if( !initialized ) {
            init();
        }

        // Look for a realm - that may have been configured earlier. 
        // If the realm is added after context - it'll set itself.
        if( realm == null ) {
            ObjectName realmName=null;
            try {
                realmName=new ObjectName( domain + ":type=Realm");
                if( mserver.isRegistered(realmName ) ) {
                    mserver.invoke(realmName, "init", 
                            new Object[] {},
                            new String[] {}
                    );            
                }
            } catch( Throwable t ) {
                log.debug("No realm for this engine " + realmName);
            }
        }
            
        // Log our server identification information
        //System.out.println(ServerInfo.getServerInfo());
        if(log.isInfoEnabled())
            log.info( "Starting Servlet Engine: " + ServerInfo.getServerInfo());
        if( mbeans != null ) {
            try {
                Registry.getRegistry(null, null)
                    .invoke(mbeans, "start", false);
            } catch (Exception e) {
                log.error("Error in start() for " + mbeansFile, e);
            }
        }

        // Standard container startup
        super.start();

    }
    
    public void stop() throws LifecycleException {
        super.stop();
        if( mbeans != null ) {
            try {
                Registry.getRegistry(null, null).invoke(mbeans, "stop", false);
            } catch (Exception e) {
                log.error("Error in stop() for " + mbeansFile, e);
            }
        }
    }


    /**
     * Return a String representation of this component.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("StandardEngine[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    // ------------------------------------------------------ Protected Methods


    // -------------------- JMX registration  --------------------

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception
    {
        super.preRegister(server,name);

        this.setName( name.getDomain());

        return name;
    }

    // FIXME Remove -- not used 
    public ObjectName getParentName() throws MalformedObjectNameException {
        if (getService()==null) {
            return null;
        }
        String name = getService().getName();
        ObjectName serviceName=new ObjectName(domain +
                        ":type=Service,serviceName="+name);
        return serviceName;                
    }
    
    public ObjectName createObjectName(String domain, ObjectName parent)
        throws Exception
    {
        if( log.isDebugEnabled())
            log.debug("Create ObjectName " + domain + " " + parent );
        return new ObjectName( domain + ":type=Engine");
    }

    
    private void readEngineMbeans() {
        try {
            MbeansSource mbeansMB=new MbeansSource();
            File mbeansF=new File( mbeansFile );
            mbeansMB.setSource(mbeansF);
            
            Registry.getRegistry(null, null).registerComponent
                (mbeansMB, domain + ":type=MbeansFile", null);
            mbeansMB.load();
            mbeansMB.init();
            mbeansMB.setRegistry(Registry.getRegistry(null, null));
            mbeans=mbeansMB.getMBeans();
            
        } catch( Throwable t ) {
            log.error( "Error loading " + mbeansFile, t );
        }
        
    }
    
    public String getDomain() {
        if (domain!=null) {
            return domain;
        } else { 
            return getName();
        }
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
}
