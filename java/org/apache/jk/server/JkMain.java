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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.jk.core.JkHandler;
import org.apache.jk.core.WorkerEnv;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.modeler.Registry;

/** Main class used to startup and configure jk. It manages the conf/jk2.properties file
 *  and is the target of JMX proxy.
 *
 *  It implements a policy of save-on-change - whenever a property is changed at
 *  runtime the jk2.properties file will be overriden. 
 *
 *  You can edit the config file when tomcat is stoped ( or if you don't use JMX or
 *  other admin tools ).
 *
 *  The format of jk2.properties:
 *  <dl>
 *   <dt>TYPE[.LOCALNAME].PROPERTY_NAME=VALUE
 *   <dd>Set a property on the associated component. TYPE will be used to
 *   find the class name and instantiate the component. LOCALNAME allows
 *   multiple instances. In JMX mode, TYPE and LOCALNAME will form the
 *   JMX name ( eventually combined with a 'jk2' component )
 *
 *   <dt>NAME=VALUE
 *   <dd>Define global properties to be used in ${} substitutions
 *
 *   <dt>class.COMPONENT_TYPE=JAVA_CLASS_NAME
 *   <dd>Adds a new 'type' of component. We predefine all known types.
 * </dl>
 *
 * Instances are created the first time a component name is found. In addition,
 * 'handler.list' property will override the list of 'default' components that are
 * loaded automatically.
 *
 *  Note that the properties file is just one (simplistic) way to configure jk. We hope
 *  to see configs based on registry, LDAP, db, etc. ( XML is not necesarily better )
 * 
 * @author Costin Manolache
 */
public class JkMain implements MBeanRegistration
{
    WorkerEnv wEnv;
    String propFile;
    Properties props=new Properties();

    Properties modules=new Properties();
    boolean modified=false;
    boolean started=false;
    boolean saveProperties=false;

    public JkMain()
    {
        JkMain.jkMain=this;
        modules.put("channelSocket", "org.apache.jk.common.ChannelSocket");
        modules.put("channelNioSocket", "org.apache.jk.common.ChannelNioSocket");
        modules.put("channelUnix", "org.apache.jk.common.ChannelUn");
        modules.put("channelJni", "org.apache.jk.common.ChannelJni");
        modules.put("apr", "org.apache.jk.apr.AprImpl");
        modules.put("mx", "org.apache.jk.common.JkMX");
        modules.put("modeler", "org.apache.jk.common.JkModeler");
        modules.put("shm", "org.apache.jk.common.Shm");
        modules.put("request","org.apache.jk.common.HandlerRequest");
        modules.put("container","org.apache.jk.common.HandlerRequest");
        modules.put("modjk","org.apache.jk.common.ModJkMX");

    }

    public static JkMain getJkMain() {
        return jkMain;
    }

    private static String DEFAULT_HTTPS="com.sun.net.ssl.internal.www.protocol";
    private void initHTTPSUrls() {
        try {
            // 11657: if only ajp is used, https: redirects need to work ( at least for 1.3+)
            String value = System.getProperty("java.protocol.handler.pkgs");
            if (value == null) {
                value = DEFAULT_HTTPS;
            } else if (value.indexOf(DEFAULT_HTTPS) >= 0  ) {
                return; // already set
            } else {
                value += "|" + DEFAULT_HTTPS;
            }
            System.setProperty("java.protocol.handler.pkgs", value);
        } catch(Exception ex ) {
            log.info("Error adding SSL Protocol Handler",ex);
        }
    }

    // -------------------- Setting --------------------
    
    /** Load a .properties file into and set the values
     *  into jk2 configuration.
     */
    public void setPropertiesFile( String p  ) {
        propFile=p;
        if( started ) {
            loadPropertiesFile();
        }
    }

    public String getPropertiesFile() {
        return propFile;
    }

    public void setSaveProperties( boolean b ) {
        saveProperties=b;
    }

    /** Set a name/value as a jk2 property
     */
    public void setProperty( String n, String v ) {
        if( "jkHome".equals( n ) ) {
            setJkHome( v );
        } 
        if( "propertiesFile".equals( n ) ) {
            setPropertiesFile( v );
        }
        props.put( n, v );
        if( started ) {
            processProperty( n, v );
            saveProperties();
        }
    }
    /**
     * Retrieve a property.
     */
    public Object getProperty(String name) {
        String alias = (String)replacements.get(name);
        Object result = null;
        if(alias != null) {
            result = props.get(alias);
        }
        if(result == null) {
            result = props.get(name);
        }
        return result;
    }
    /**
     * Set the <code>channelClassName</code> that will used to connect to
     * httpd.
     */
    public void setChannelClassName(String name) {
        props.put( "handler.channel.className",name);
    }

    public String getChannelClassName() {
        return (String)props.get( "handler.channel.className");
    }

    /**
     * Set the <code>workerClassName</code> that will handle the request.
     * ( sort of 'pivot' in axis :-)
     */
    public void setWorkerClassName(String name) {
        props.put( "handler.container.className",name);
    }

    public String getWorkerClassName() {
        return (String)props.get( "handler.container.className");
    }

    /** Set the base dir of jk2. ( including WEB-INF if in a webapp ).
     *  We'll try to guess it from classpath if none is set ( for
     *  example on command line ), but if in a servlet environment
     *  you need to use Context.getRealPath or a system property or
     *  set it expliciltey.
     */
    public void setJkHome( String s ) {
        getWorkerEnv().setJkHome(s);
    }

    public String getJkHome() {
        return getWorkerEnv().getJkHome();
    }

    String out;
    String err;
    File propsF;
    
    public void setOut( String s ) {
        this.out=s;
    }

    public String getOut() {
        return this.out;
    }

    public void setErr( String s ) {
        this.err=s;
    }
    
    public String getErr() {
        return this.err;
    }
    
    // -------------------- Initialization --------------------
    
    public void init() throws IOException
    {
        long t1=System.currentTimeMillis();
        if(null != out) {
            PrintStream outS=new PrintStream(new FileOutputStream(out));
            System.setOut(outS);
        }
        if(null != err) {
            PrintStream errS=new PrintStream(new FileOutputStream(err));
            System.setErr(errS);
        }

        String home=getWorkerEnv().getJkHome();
        if( home==null ) {
            // XXX use IntrospectionUtil to find myself
            this.guessHome();
        }
        home=getWorkerEnv().getJkHome();
        if( home==null ) {
            log.info( "Can't find home, jk2.properties not loaded");
        }
        if(log.isDebugEnabled())
            log.debug("Starting Jk2, base dir= " + home  );
        loadPropertiesFile();

        String initHTTPS = (String)props.get("class.initHTTPS");
        if("true".equalsIgnoreCase(initHTTPS)) {
            initHTTPSUrls();
        }

        long t2=System.currentTimeMillis();
        initTime=t2-t1;
    }
    
    static String defaultHandlers[]= { "request",
                                       "container",
                                       "channelSocket"};
    /*
     static String defaultHandlers[]= { "apr",
                                       "shm",
                                       "request",
                                       "container",
                                       "channelSocket",
                                       "channelJni",
                                       "channelUnix"};
    */
    
    public void stop() 
    {
        for( int i=0; i<wEnv.getHandlerCount(); i++ ) {
            if( wEnv.getHandler(i) != null ) {
                try {
                    wEnv.getHandler(i).destroy();
                } catch( IOException ex) {
                    log.error("Error stopping " + wEnv.getHandler(i).getName(), ex);
                }
            }
        }

        started=false;
    }
    
    public void start() throws IOException
    {
        long t1=System.currentTimeMillis();
        // We must have at least 3 handlers:
        // channel is the 'transport'
        // request is the request processor or 'global' chain
        // container is the 'provider'
        // Additional handlers may exist and be used internally
        // or be chained to create one of the standard handlers 

        String handlers[]=defaultHandlers;
        // backward compat
        String workers=props.getProperty( "handler.list", null );
        if( workers!=null ) {
            handlers= split( workers, ",");
        }

        // Load additional component declarations
        processModules();
        
        for( int i=0; i<handlers.length; i++ ) {
            String name= handlers[i];
            JkHandler w=getWorkerEnv().getHandler( name );
            if( w==null ) {
                newHandler( name, "", name );
            }
        }

        // Process properties - and add aditional handlers.
        processProperties();

        for( int i=0; i<wEnv.getHandlerCount(); i++ ) {
            if( wEnv.getHandler(i) != null ) {
                try {
                    wEnv.getHandler(i).init();
                } catch( IOException ex) {
                    if( "apr".equals(wEnv.getHandler(i).getName() )) {
                        log.info( "APR not loaded, disabling jni components: " + ex.toString());
                    } else {
                        log.error( "error initializing " + wEnv.getHandler(i).getName(), ex );
                    }
                }
            }
        }

        started=true;
        long t2=System.currentTimeMillis();
        startTime=t2-t1;

        this.saveProperties();
        log.info("Jk running ID=" + wEnv.getLocalId() + " time=" + initTime + "/" + startTime +
                 "  config=" + propFile);
    }

    // -------------------- Usefull methods --------------------
    
    public WorkerEnv getWorkerEnv() {
        if( wEnv==null ) { 
            wEnv=new WorkerEnv();
        }
        return wEnv;
    }

    public void setWorkerEnv(WorkerEnv wEnv) {
        this.wEnv = wEnv;
    }

    /* A bit of magic to support workers.properties without giving
       up the clean get/set
    */
    public void setBeanProperty( Object target, String name, String val ) {
        if( val!=null )
            val=IntrospectionUtils.replaceProperties( val, props, null );
        if( log.isDebugEnabled())
            log.debug( "setProperty " + target + " " + name + "=" + val );
        
        IntrospectionUtils.setProperty( target, name, val );
    }

    /* 
     * Set a handler property
     */
    public void setPropertyString( String handlerN, String name, String val ) {
        if( log.isDebugEnabled() )
            log.debug( "setProperty " + handlerN + " " + name + "=" + val );
        Object target=getWorkerEnv().getHandler( handlerN );

        setBeanProperty( target, name, val );
        if( started ) {
            saveProperties();
        }

    }

    /** The time it took to initialize jk ( ms)
     */
    public long getInitTime() {
        return initTime;
    }

    /** The time it took to start jk ( ms )
     */
    public long getStartTime() {
        return startTime;
    }
    
    // -------------------- Main --------------------

    long initTime;
    long startTime;
    static JkMain jkMain=null;

    public static void main(String args[]) {
        try {
            if( args.length == 1 &&
                ( "-?".equals(args[0]) || "-h".equals( args[0])) ) {
                System.out.println("Usage: ");
                System.out.println("  JkMain [args]");
                System.out.println();
                System.out.println("  Each bean setter corresponds to an arg ( like -debug 10 )");
                System.out.println("  System properties:");
                System.out.println("    jk2.home    Base dir of jk2");
                return;
            }

            jkMain=new JkMain();

            IntrospectionUtils.processArgs( jkMain, args, new String[] {},
                                            null, new Hashtable());

            jkMain.init();
            jkMain.start();
        } catch( Exception ex ) {
            log.warn("Error running",ex);
        }
    }

    // -------------------- Private methods --------------------


    private boolean checkPropertiesFile() {
        if(propFile == null) {
            return false;
        }
        propsF = new File(propFile);
        if(!propsF.isAbsolute()) {
            String home = getWorkerEnv().getJkHome();
            if( home == null ) {
                return false;
            }
            propsF = new File(home, propFile);
        }
        return propsF.exists();
    }
            
    private void loadPropertiesFile() {
        if(!checkPropertiesFile()) {
            return;
        }

        try {
            props.load( new FileInputStream(propsF) );
        } catch(IOException ex ){
            log.warn("Unable to load properties from "+propsF,ex);
        }
    }

    public  void saveProperties() {
        if( !saveProperties) return;
        
        if(propsF == null) {
            log.warn("No properties file specified. Unable to save");
            return;
        }
        // Temp - to check if it works
        File outFile= new File(propsF.getParentFile(), propsF.getName()+".save");
        log.debug("Saving properties " + outFile );
        try {
            props.store( new FileOutputStream(outFile), "AUTOMATICALLY GENERATED" );
        } catch(IOException ex ){
            log.warn("Unable to save to "+outFile,ex);
        }
    }

    // translate top-level keys ( from coyote or generic ) into component keys
    static Hashtable replacements=new Hashtable();
    static {
        replacements.put("port","channelSocket.port");
        replacements.put("maxThreads", "channelSocket.maxThreads");   
        replacements.put("minSpareThreads", "channelSocket.minSpareThreads");   
        replacements.put("maxSpareThreads", "channelSocket.maxSpareThreads");   
        replacements.put("backlog", "channelSocket.backlog");   
        replacements.put("tcpNoDelay", "channelSocket.tcpNoDelay");
        replacements.put("soTimeout", "channelSocket.soTimeout");
        replacements.put("timeout", "channelSocket.timeout");
        replacements.put("address", "channelSocket.address");            
        replacements.put("bufferSize", "channelSocket.bufferSize");
        replacements.put("tomcatAuthentication", "request.tomcatAuthentication");
        replacements.put("packetSize", "channelSocket.packetSize");
    }

    private void preProcessProperties() {
        Enumeration keys=props.keys();
        Vector v=new Vector();
        
        while( keys.hasMoreElements() ) {
            String key=(String)keys.nextElement();          
            Object newName=replacements.get(key);
            if( newName !=null ) {
                v.addElement(key);
            }
        }
        keys=v.elements();
        while( keys.hasMoreElements() ) {
            String key=(String)keys.nextElement();
            Object propValue=props.getProperty( key );
            String replacement=(String)replacements.get(key);
            props.put(replacement, propValue);
            if( log.isDebugEnabled()) 
                log.debug("Substituting " + key + " " + replacement + " " + 
                    propValue);
        }
    }
    
    private void processProperties() {
        preProcessProperties();
        Enumeration keys=props.keys();

        while( keys.hasMoreElements() ) {
            String name=(String)keys.nextElement();
            String propValue=props.getProperty( name );

            processProperty( name, propValue );
        }
    }

    private void processProperty(String name, String propValue) {
        String type=name;
        String fullName=name;
        String localName="";
        String propName="";
        // ignore
        if( name.startsWith("key.")) return;

        int dot=name.indexOf(".");
        int lastDot=name.lastIndexOf(".");
        if( dot > 0 ) {
            type=name.substring(0, dot );
            if( dot != lastDot ) {
                localName=name.substring( dot + 1, lastDot );
                fullName=type + "." + localName;
            } else {
                fullName=type;
            }
            propName=name.substring( lastDot+1);
        } else {
            return;
        }
        
        if( log.isDebugEnabled() )
            log.debug( "Processing " + type + ":" + localName + ":" + fullName + " " + propName );
        if( "class".equals( type ) || "handler".equals( type ) ) {
            return;
        }
        
        JkHandler comp=getWorkerEnv().getHandler( fullName );
        if( comp==null ) {
            comp=newHandler( type, localName, fullName );
        }
        if( comp==null )
            return;
        
        if( log.isDebugEnabled() ) 
            log.debug("Setting " + propName + " on " + fullName + " " + comp);
        this.setBeanProperty( comp, propName, propValue );
    }

    private JkHandler newHandler( String type, String localName, String fullName )
    {
        JkHandler handler;
        String classN=modules.getProperty(type);
        if( classN == null ) {
            log.error("No class name for " + fullName + " " + type );
            return null;
        }
        try {
            Class channelclass = Class.forName(classN);
            handler=(JkHandler)channelclass.newInstance();
        } catch (Throwable ex) {
            handler=null;
            log.error( "Can't create " + fullName, ex );
            return null;
        }
        if( this.domain != null ) {
            try {
                ObjectName handlerOname = new ObjectName
                    (this.domain + ":" + "type=JkHandler,name=" + fullName);
                Registry.getRegistry(null, null).registerComponent(handler, handlerOname, classN);
            } catch (Exception e) {
                log.error( "Error registering " + fullName, e );
            }

        }
        wEnv.addHandler( fullName, handler );
        return handler;
    }

    private void processModules() {
        Enumeration keys=props.keys();
        int plen=6;
        
        while( keys.hasMoreElements() ) {
            String k=(String)keys.nextElement();
            if( ! k.startsWith( "class." ) )
                continue;

            String name= k.substring( plen );
            String propValue=props.getProperty( k );

            if( log.isDebugEnabled()) log.debug("Register " + name + " " + propValue );
            modules.put( name, propValue );
        }
    }

    private String[] split(String s, String delim ) {
         Vector v=new Vector();
        StringTokenizer st=new StringTokenizer(s, delim );
        while( st.hasMoreTokens() ) {
            v.addElement( st.nextToken());
        }
        String res[]=new String[ v.size() ];
        for( int i=0; i<res.length; i++ ) {
            res[i]=(String)v.elementAt(i);
        }
        return res;
    }

    // guessing home
    private static String CNAME="org/apache/jk/server/JkMain.class";

    private void guessHome() {
        String home= wEnv.getJkHome();
        if( home != null )
            return;
        home=IntrospectionUtils.guessInstall( "jk2.home","jk2.home",
                                              "tomcat-jk2.jar", CNAME );
        if( home != null ) {
            log.info("Guessed home " + home );
            wEnv.setJkHome( home );
        }
    }

    static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( JkMain.class );

    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }

    public void pause() throws Exception {
        // wEnv sometime null at shutdown - bug45591
        if (wEnv != null) {
            for( int i=0; i<wEnv.getHandlerCount(); i++ ) {
                if( wEnv.getHandler(i) != null ) {
                    wEnv.getHandler(i).pause();
                }
            }
        }
    }

    public void resume() throws Exception {
        for( int i=0; i<wEnv.getHandlerCount(); i++ ) {
            if( wEnv.getHandler(i) != null ) {
                wEnv.getHandler(i).resume();
            }
        }
    }


}
