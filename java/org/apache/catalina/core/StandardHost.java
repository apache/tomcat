/*
 * Copyright 1999,2004 The Apache Software Foundation.
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


import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.modeler.Registry;


/**
 * Standard implementation of the <b>Host</b> interface.  Each
 * child container must be a Context implementation to process the
 * requests directed to a particular web application.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision: 303666 $ $Date: 2005-01-29 20:38:37 +0100 (sam., 29 janv. 2005) $
 */

public class StandardHost
    extends ContainerBase
    implements Host  
 {
    /* Why do we implement deployer and delegate to deployer ??? */

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( StandardHost.class );
    
    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardHost component with the default basic Valve.
     */
    public StandardHost() {

        super();
        pipeline.setBasic(new StandardHostValve());

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The set of aliases for this Host.
     */
    private String[] aliases = new String[0];


    /**
     * The application root for this Host.
     */
    private String appBase = ".";


    /**
     * The auto deploy flag for this Host.
     */
    private boolean autoDeploy = true;


    /**
     * The Java class name of the default context configuration class
     * for deployed web applications.
     */
    private String configClass =
        "org.apache.catalina.startup.ContextConfig";


    /**
     * The Java class name of the default Context implementation class for
     * deployed web applications.
     */
    private String contextClass =
        "org.apache.catalina.core.StandardContext";


    /**
     * The deploy on startup flag for this Host.
     */
    private boolean deployOnStartup = true;


    /**
     * deploy Context XML config files property.
     */
    private boolean deployXML = true;


    /**
     * The Java class name of the default error reporter implementation class 
     * for deployed web applications.
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";

    /**
     * The object name for the errorReportValve.
     */
    private ObjectName errorReportValveObjectName = null;

    /**
     * The descriptive information string for this implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardHost/1.0";


    /**
     * The live deploy flag for this Host.
     */
    private boolean liveDeploy = true;


    /**
     * Unpack WARs property.
     */
    private boolean unpackWARs = true;


    /**
     * Work Directory base for applications.
     */
    private String workDir = null;


    /**
     * Attribute value used to turn on/off XML validation
     */
     private boolean xmlValidation = false;


    /**
     * Attribute value used to turn on/off XML namespace awarenes.
     */
     private boolean xmlNamespaceAware = false;


    // ------------------------------------------------------------- Properties


    /**
     * Return the application root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     */
    public String getAppBase() {

        return (this.appBase);

    }


    /**
     * Set the application root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param appBase The new application root
     */
    public void setAppBase(String appBase) {

        String oldAppBase = this.appBase;
        this.appBase = appBase;
        support.firePropertyChange("appBase", oldAppBase, this.appBase);

    }


    /**
     * Return the value of the auto deploy flag.  If true, it indicates that 
     * this host's child webapps will be dynamically deployed.
     */
    public boolean getAutoDeploy() {

        return (this.autoDeploy);

    }


    /**
     * Set the auto deploy flag value for this host.
     * 
     * @param autoDeploy The new auto deploy flag
     */
    public void setAutoDeploy(boolean autoDeploy) {

        boolean oldAutoDeploy = this.autoDeploy;
        this.autoDeploy = autoDeploy;
        support.firePropertyChange("autoDeploy", oldAutoDeploy, 
                                   this.autoDeploy);

    }


    /**
     * Return the Java class name of the context configuration class
     * for new web applications.
     */
    public String getConfigClass() {

        return (this.configClass);

    }


    /**
     * Set the Java class name of the context configuration class
     * for new web applications.
     *
     * @param configClass The new context configuration class
     */
    public void setConfigClass(String configClass) {

        String oldConfigClass = this.configClass;
        this.configClass = configClass;
        support.firePropertyChange("configClass",
                                   oldConfigClass, this.configClass);

    }


    /**
     * Return the Java class name of the Context implementation class
     * for new web applications.
     */
    public String getContextClass() {

        return (this.contextClass);

    }


    /**
     * Set the Java class name of the Context implementation class
     * for new web applications.
     *
     * @param contextClass The new context implementation class
     */
    public void setContextClass(String contextClass) {

        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        support.firePropertyChange("contextClass",
                                   oldContextClass, this.contextClass);

    }


    /**
     * Return the value of the deploy on startup flag.  If true, it indicates 
     * that this host's child webapps should be discovred and automatically 
     * deployed at startup time.
     */
    public boolean getDeployOnStartup() {

        return (this.deployOnStartup);

    }


    /**
     * Set the deploy on startup flag value for this host.
     * 
     * @param deployOnStartup The new deploy on startup flag
     */
    public void setDeployOnStartup(boolean deployOnStartup) {

        boolean oldDeployOnStartup = this.deployOnStartup;
        this.deployOnStartup = deployOnStartup;
        support.firePropertyChange("deployOnStartup", oldDeployOnStartup, 
                                   this.deployOnStartup);

    }


    /**
     * Deploy XML Context config files flag accessor.
     */
    public boolean isDeployXML() {

        return (deployXML);

    }


    /**
     * Deploy XML Context config files flag mutator.
     */
    public void setDeployXML(boolean deployXML) {

        this.deployXML = deployXML;

    }


    /**
     * Return the value of the live deploy flag.  If true, it indicates that 
     * a background thread should be started that looks for web application
     * context files, WAR files, or unpacked directories being dropped in to
     * the <code>appBase</code> directory, and deploys new ones as they are
     * encountered.
     */
    public boolean getLiveDeploy() {
        return (this.autoDeploy);
    }


    /**
     * Set the live deploy flag value for this host.
     * 
     * @param liveDeploy The new live deploy flag
     */
    public void setLiveDeploy(boolean liveDeploy) {
        setAutoDeploy(liveDeploy);
    }


    /**
     * Return the Java class name of the error report valve class
     * for new web applications.
     */
    public String getErrorReportValveClass() {

        return (this.errorReportValveClass);

    }


    /**
     * Set the Java class name of the error report valve class
     * for new web applications.
     *
     * @param errorReportValveClass The new error report valve class
     */
    public void setErrorReportValveClass(String errorReportValveClass) {

        String oldErrorReportValveClassClass = this.errorReportValveClass;
        this.errorReportValveClass = errorReportValveClass;
        support.firePropertyChange("errorReportValveClass",
                                   oldErrorReportValveClassClass, 
                                   this.errorReportValveClass);

    }


    /**
     * Return the canonical, fully qualified, name of the virtual host
     * this Container represents.
     */
    public String getName() {

        return (name);

    }


    /**
     * Set the canonical, fully qualified, name of the virtual host
     * this Container represents.
     *
     * @param name Virtual host name
     *
     * @exception IllegalArgumentException if name is null
     */
    public void setName(String name) {

        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardHost.nullName"));

        name = name.toLowerCase();      // Internally all names are lower case

        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);

    }


    /**
     * Unpack WARs flag accessor.
     */
    public boolean isUnpackWARs() {

        return (unpackWARs);

    }


    /**
     * Unpack WARs flag mutator.
     */
    public void setUnpackWARs(boolean unpackWARs) {

        this.unpackWARs = unpackWARs;

    }

     /**
     * Set the validation feature of the XML parser used when
     * parsing xml instances.
     * @param xmlValidation true to enable xml instance validation
     */
    public void setXmlValidation(boolean xmlValidation){
        
        this.xmlValidation = xmlValidation;

    }

    /**
     * Get the server.xml <host> attribute's xmlValidation.
     * @return true if validation is enabled.
     *
     */
    public boolean getXmlValidation(){
        return xmlValidation;
    }

    /**
     * Get the server.xml <host> attribute's xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     *
     */
    public boolean getXmlNamespaceAware(){
        return xmlNamespaceAware;
    }


    /**
     * Set the namespace aware feature of the XML parser used when
     * parsing xml instances.
     * @param xmlNamespaceAware true to enable namespace awareness
     */
    public void setXmlNamespaceAware(boolean xmlNamespaceAware){
        this.xmlNamespaceAware=xmlNamespaceAware;
    }    
    
    /**
     * Host work directory base.
     */
    public String getWorkDir() {

        return (workDir);
    }


    /**
     * Host work directory base.
     */
    public void setWorkDir(String workDir) {

        this.workDir = workDir;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add an alias name that should be mapped to this same Host.
     *
     * @param alias The alias to be added
     */
    public void addAlias(String alias) {

        alias = alias.toLowerCase();

        // Skip duplicate aliases
        for (int i = 0; i < aliases.length; i++) {
            if (aliases[i].equals(alias))
                return;
        }

        // Add this alias to the list
        String newAliases[] = new String[aliases.length + 1];
        for (int i = 0; i < aliases.length; i++)
            newAliases[i] = aliases[i];
        newAliases[aliases.length] = alias;

        aliases = newAliases;

        // Inform interested listeners
        fireContainerEvent(ADD_ALIAS_EVENT, alias);

    }


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Context.
     *
     * @param child Child container to be added
     */
    public void addChild(Container child) {

        if (!(child instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardHost.notContext"));
        super.addChild(child);

    }


    /**
     * Return the set of alias names for this Host.  If none are defined,
     * a zero length array is returned.
     */
    public String[] findAliases() {

        return (this.aliases);

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
     * Return the Context that would be used to process the specified
     * host-relative request URI, if any; otherwise return <code>null</code>.
     *
     * @param uri Request URI to be mapped
     */
    public Context map(String uri) {

        if (log.isDebugEnabled())
            log.debug("Mapping request URI '" + uri + "'");
        if (uri == null)
            return (null);

        // Match on the longest possible context path prefix
        if (log.isTraceEnabled())
            log.trace("  Trying the longest context path prefix");
        Context context = null;
        String mapuri = uri;
        while (true) {
            context = (Context) findChild(mapuri);
            if (context != null)
                break;
            int slash = mapuri.lastIndexOf('/');
            if (slash < 0)
                break;
            mapuri = mapuri.substring(0, slash);
        }

        // If no Context matches, select the default Context
        if (context == null) {
            if (log.isTraceEnabled())
                log.trace("  Trying the default context");
            context = (Context) findChild("");
        }

        // Complain if no Context has been selected
        if (context == null) {
            log.error(sm.getString("standardHost.mappingError", uri));
            return (null);
        }

        // Return the mapped Context (if any)
        if (log.isDebugEnabled())
            log.debug(" Mapped to context '" + context.getPath() + "'");
        return (context);

    }


    /**
     * Remove the specified alias name from the aliases for this Host.
     *
     * @param alias Alias name to be removed
     */
    public void removeAlias(String alias) {

        alias = alias.toLowerCase();

        synchronized (aliases) {

            // Make sure this alias is currently present
            int n = -1;
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified alias
            int j = 0;
            String results[] = new String[aliases.length - 1];
            for (int i = 0; i < aliases.length; i++) {
                if (i != n)
                    results[j++] = aliases[i];
            }
            aliases = results;

        }

        // Inform interested listeners
        fireContainerEvent(REMOVE_ALIAS_EVENT, alias);

    }


    /**
     * Return a String representation of this component.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardHost[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    /**
     * Start this host.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents it from being started
     */
    public synchronized void start() throws LifecycleException {
        if( started ) {
            return;
        }
        if( ! initialized )
            init();

        // Look for a realm - that may have been configured earlier. 
        // If the realm is added after context - it'll set itself.
        if( realm == null ) {
            ObjectName realmName=null;
            try {
                realmName=new ObjectName( domain + ":type=Realm,host=" + getName());
                if( mserver.isRegistered(realmName ) ) {
                    mserver.invoke(realmName, "init", 
                            new Object[] {},
                            new String[] {}
                    );            
                }
            } catch( Throwable t ) {
                log.debug("No realm for this host " + realmName);
            }
        }
            
        // Set error report valve
        if ((errorReportValveClass != null)
            && (!errorReportValveClass.equals(""))) {
            try {
                boolean found = false;
                if(errorReportValveObjectName != null) {
                    ObjectName[] names = 
                        ((StandardPipeline)pipeline).getValveObjectNames();
                    for (int i=0; !found && i<names.length; i++)
                        if(errorReportValveObjectName.equals(names[i]))
                            found = true ;
                    }
                    if(!found) {          	
                        Valve valve = (Valve) Class.forName(errorReportValveClass)
                        .newInstance();
                        addValve(valve);
                        errorReportValveObjectName = ((ValveBase)valve).getObjectName() ;
                    }
            } catch (Throwable t) {
                log.error(sm.getString
                    ("standardHost.invalidErrorReportValveClass", 
                     errorReportValveClass));
            }
        }
        if(log.isInfoEnabled()) {
            if (xmlValidation)
                log.info( sm.getString("standardHost.validationEnabled"));
            else
                log.info( sm.getString("standardHost.validationDisabled"));
        }
        super.start();

    }


    // -------------------- JMX  --------------------
    /**
      * Return the MBean Names of the Valves assoicated with this Host
      *
      * @exception Exception if an MBean cannot be created or registered
      */
     public String [] getValveNames()
         throws Exception
    {
         Valve [] valves = this.getValves();
         String [] mbeanNames = new String[valves.length];
         for (int i = 0; i < valves.length; i++) {
             if( valves[i] == null ) continue;
             if( ((ValveBase)valves[i]).getObjectName() == null ) continue;
             mbeanNames[i] = ((ValveBase)valves[i]).getObjectName().toString();
         }

         return mbeanNames;

     }

    public String[] getAliases() {
        return aliases;
    }

    private boolean initialized=false;
    
    public void init() {
        if( initialized ) return;
        initialized=true;
        
        // already registered.
        if( getParent() == null ) {
            try {
                // Register with the Engine
                ObjectName serviceName=new ObjectName(domain + 
                                        ":type=Engine");

                HostConfig deployer = new HostConfig();
                addLifecycleListener(deployer);                
                if( mserver.isRegistered( serviceName )) {
                    if(log.isDebugEnabled())
                        log.debug("Registering "+ serviceName +" with the Engine");
                    mserver.invoke( serviceName, "addChild",
                            new Object[] { this },
                            new String[] { "org.apache.catalina.Container" } );
                }
            } catch( Exception ex ) {
                log.error("Host registering failed!",ex);
            }
        }
        
        if( oname==null ) {
            // not registered in JMX yet - standalone mode
            try {
                StandardEngine engine=(StandardEngine)parent;
                domain=engine.getName();
                if(log.isDebugEnabled())
                    log.debug( "Register host " + getName() + " with domain "+ domain );
                oname=new ObjectName(domain + ":type=Host,host=" +
                        this.getName());
                controller = oname;
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
            } catch( Throwable t ) {
                log.error("Host registering failed!", t );
            }
        }
    }

    public void destroy() throws Exception {
        // destroy our child containers, if any
        Container children[] = findChildren();
        super.destroy();
        for (int i = 0; i < children.length; i++) {
            if(children[i] instanceof StandardContext)
                ((StandardContext)children[i]).destroy();
        }
      
    }
    
    public ObjectName preRegister(MBeanServer server, ObjectName oname ) 
        throws Exception
    {
        ObjectName res=super.preRegister(server, oname);
        String name=oname.getKeyProperty("host");
        if( name != null )
            setName( name );
        return res;        
    }
    
    public ObjectName createObjectName(String domain, ObjectName parent)
        throws Exception
    {
        if( log.isDebugEnabled())
            log.debug("Create ObjectName " + domain + " " + parent );
        return new ObjectName( domain + ":type=Host,host=" + getName());
    }

}
