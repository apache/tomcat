/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.catalina.mbeans;

import java.io.File;
import java.net.InetAddress;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.realm.DataSourceRealm;
import org.apache.catalina.realm.JDBCRealm;
import org.apache.catalina.realm.JNDIRealm;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.UserDatabaseRealm;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.HostConfig;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * @author Amy Roh
 */
public class MBeanFactory {

    private static final Log log = LogFactory.getLog(MBeanFactory.class);

    protected static final StringManager sm = StringManager.getManager(MBeanFactory.class);

    /**
     * The <code>MBeanServer</code> for this application.
     */
    private static final MBeanServer mserver = MBeanUtils.createServer();


    // ------------------------------------------------------------- Attributes

    /**
     * The container (Server/Service) for which this factory was created.
     */
    private Object container;


    // ------------------------------------------------------------- Operations

    /**
     * Set the container that this factory was created for.
     * @param container The associated container
     */
    public void setContainer(Object container) {
        this.container = container;
    }


    /**
     * Little convenience method to remove redundant code
     * when retrieving the path string
     *
     * @param t path string
     * @return empty string if t==null || t.equals("/")
     */
    private final String getPathStr(String t) {
        if (t == null || t.equals("/")) {
            return "";
        }
        return t;
    }

   /**
     * Get Parent Container to add its child component
     * from parent's ObjectName
     */
    private Container getParentContainerFromParent(ObjectName pname)
        throws Exception {

        String type = pname.getKeyProperty("type");
        String j2eeType = pname.getKeyProperty("j2eeType");
        Service service = getService(pname);
        StandardEngine engine = (StandardEngine) service.getContainer();
        if ((j2eeType!=null) && (j2eeType.equals("WebModule"))) {
            String name = pname.getKeyProperty("name");
            name = name.substring(2);
            int i = name.indexOf('/');
            String hostName = name.substring(0,i);
            String path = name.substring(i);
            Container host = engine.findChild(hostName);
            String pathStr = getPathStr(path);
            Container context = host.findChild(pathStr);
            return context;
        } else if (type != null) {
            if (type.equals("Engine")) {
                return engine;
            } else if (type.equals("Host")) {
                String hostName = pname.getKeyProperty("host");
                Container host = engine.findChild(hostName);
                return host;
            }
        }
        return null;

    }


    /**
     * Get Parent ContainerBase to add its child component
     * from child component's ObjectName  as a String
     */
    private Container getParentContainerFromChild(ObjectName oname)
        throws Exception {

        String hostName = oname.getKeyProperty("host");
        String path = oname.getKeyProperty("path");
        Service service = getService(oname);
        Container engine = service.getContainer();
        if (hostName == null) {
            // child's container is Engine
            return engine;
        } else if (path == null) {
            // child's container is Host
            Container host = engine.findChild(hostName);
            return host;
        } else {
            // child's container is Context
            Container host = engine.findChild(hostName);
            path = getPathStr(path);
            Container context = host.findChild(path);
            return context;
        }
    }


    private Service getService(ObjectName oname) throws Exception {

        if (container instanceof Service) {
            // Don't bother checking the domain - this is the only option
            return (Service) container;
        }

        StandardService service = null;
        String domain = oname.getDomain();
        if (container instanceof Server) {
            Service[] services = ((Server)container).findServices();
            for (int i = 0; i < services.length; i++) {
                service = (StandardService) services[i];
                if (domain.equals(service.getObjectName().getDomain())) {
                    break;
                }
            }
        }
        if (service == null ||
                !service.getObjectName().getDomain().equals(domain)) {
            throw new Exception("Service with the domain is not found");
        }
        return service;

    }


    /**
     * Create a new AjpConnector
     *
     * @param parent MBean Name of the associated parent component
     * @param address The IP address on which to bind
     * @param port TCP port number to listen on
     * @return the object name of the created connector
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createAjpConnector(String parent, String address, int port)
        throws Exception {

        return createConnector(parent, address, port, true, false);
    }


    /**
     * Create a new DataSource Realm.
     *
     * @param parent MBean Name of the associated parent component
     * @param dataSourceName the datasource name
     * @param roleNameCol the column name for the role names
     * @param userCredCol the column name for the user credentials
     * @param userNameCol the column name for the user names
     * @param userRoleTable the table name for the roles table
     * @param userTable the table name for the users
     * @return the object name of the created realm
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createDataSourceRealm(String parent, String dataSourceName,
        String roleNameCol, String userCredCol, String userNameCol,
        String userRoleTable, String userTable) throws Exception {

        // Create a new DataSourceRealm instance
        DataSourceRealm realm = new DataSourceRealm();
        realm.setDataSourceName(dataSourceName);
        realm.setRoleNameCol(roleNameCol);
        realm.setUserCredCol(userCredCol);
        realm.setUserNameCol(userNameCol);
        realm.setUserRoleTable(userRoleTable);
        realm.setUserTable(userTable);

        // Add the new instance to its parent component
        return addRealmToParent(parent, realm);
    }


    private String addRealmToParent(String parent, Realm realm) throws Exception {
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        // Add the new instance to its parent component
        container.setRealm(realm);
        // Return the corresponding MBean name
        ObjectName oname = null;
        if (realm instanceof JmxEnabled) {
            oname = ((JmxEnabled) realm).getObjectName();
        }
        if (oname != null) {
            return oname.toString();
        } else {
            return null;
        }
    }


    /**
     * Create a new HttpConnector
     *
     * @param parent MBean Name of the associated parent component
     * @param address The IP address on which to bind
     * @param port TCP port number to listen on
     * @return the object name of the created connector
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createHttpConnector(String parent, String address, int port)
            throws Exception {
        return createConnector(parent, address, port, false, false);
    }


    /**
     * Create a new Connector
     *
     * @param parent MBean Name of the associated parent component
     * @param address The IP address on which to bind
     * @param port TCP port number to listen on
     * @param isAjp Create a AJP/1.3 Connector
     * @param isSSL Create a secure Connector
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    private String createConnector(String parent, String address, int port, boolean isAjp, boolean isSSL)
        throws Exception {
        // Set the protocol in the constructor
        String protocol = isAjp ? "AJP/1.3" : "HTTP/1.1";
        Connector retobj = new Connector(protocol);
        if ((address!=null) && (address.length()>0)) {
            retobj.setProperty("address", address);
        }
        // Set port number
        retobj.setPort(port);
        // Set SSL
        retobj.setSecure(isSSL);
        retobj.setScheme(isSSL ? "https" : "http");
        // Add the new instance to its parent component
        // FIX ME - addConnector will fail
        ObjectName pname = new ObjectName(parent);
        Service service = getService(pname);
        service.addConnector(retobj);

        // Return the corresponding MBean name
        ObjectName coname = retobj.getObjectName();

        return coname.toString();
    }


    /**
     * Create a new HttpsConnector
     *
     * @param parent MBean Name of the associated parent component
     * @param address The IP address on which to bind
     * @param port TCP port number to listen on
     * @return the object name of the created connector
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createHttpsConnector(String parent, String address, int port)
        throws Exception {
        return createConnector(parent, address, port, false, true);
    }


    /**
     * Create a new JDBC Realm.
     *
     * @param parent MBean Name of the associated parent component
     * @param driverName JDBC driver name
     * @param connectionName the user name for the connection
     * @param connectionPassword the password for the connection
     * @param connectionURL the connection URL to the database
     * @return the object name of the created realm
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createJDBCRealm(String parent, String driverName,
        String connectionName, String connectionPassword, String connectionURL)
        throws Exception {

        // Create a new JDBCRealm instance
        JDBCRealm realm = new JDBCRealm();
        realm.setDriverName(driverName);
        realm.setConnectionName(connectionName);
        realm.setConnectionPassword(connectionPassword);
        realm.setConnectionURL(connectionURL);

        // Add the new instance to its parent component
        return addRealmToParent(parent, realm);
    }


    /**
     * Create a new JNDI Realm.
     *
     * @param parent MBean Name of the associated parent component
     * @return the object name of the created realm
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createJNDIRealm(String parent) throws Exception {

         // Create a new JNDIRealm instance
        JNDIRealm realm = new JNDIRealm();

        // Add the new instance to its parent component
        return addRealmToParent(parent, realm);
    }


    /**
     * Create a new Memory Realm.
     *
     * @param parent MBean Name of the associated parent component
     * @return the object name of the created realm
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createMemoryRealm(String parent) throws Exception {

         // Create a new MemoryRealm instance
        MemoryRealm realm = new MemoryRealm();

        // Add the new instance to its parent component
        return addRealmToParent(parent, realm);
    }


   /**
     * Create a new StandardContext.
     *
     * @param parent MBean Name of the associated parent component
     * @param path The context path for this Context
     * @param docBase Document base directory (or WAR) for this Context
     * @return the object name of the created context
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createStandardContext(String parent,
                                        String path,
                                        String docBase)
        throws Exception {

        return createStandardContext(parent, path, docBase, false, false);
    }


    /**
     * Create a new StandardContext.
     *
     * @param parent MBean Name of the associated parent component
     * @param path The context path for this Context
     * @param docBase Document base directory (or WAR) for this Context
     * @param xmlValidation if XML descriptors should be validated
     * @param xmlNamespaceAware if the XML processor should namespace aware
     * @return the object name of the created context
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createStandardContext(String parent,
                                        String path,
                                        String docBase,
                                        boolean xmlValidation,
                                        boolean xmlNamespaceAware)
        throws Exception {

        // Create a new StandardContext instance
        StandardContext context = new StandardContext();
        path = getPathStr(path);
        context.setPath(path);
        context.setDocBase(docBase);
        context.setXmlValidation(xmlValidation);
        context.setXmlNamespaceAware(xmlNamespaceAware);

        ContextConfig contextConfig = new ContextConfig();
        context.addLifecycleListener(contextConfig);

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        ObjectName deployer = new ObjectName(pname.getDomain()+
                                             ":type=Deployer,host="+
                                             pname.getKeyProperty("host"));
        if(mserver.isRegistered(deployer)) {
            String contextName = context.getName();
            mserver.invoke(deployer, "addServiced",
                           new Object [] {contextName},
                           new String [] {"java.lang.String"});
            String configPath = (String)mserver.getAttribute(deployer,
                                                             "configBaseName");
            String baseName = context.getBaseName();
            File configFile = new File(new File(configPath), baseName+".xml");
            if (configFile.isFile()) {
                context.setConfigFile(configFile.toURI().toURL());
            }
            mserver.invoke(deployer, "manageApp",
                           new Object[] {context},
                           new String[] {"org.apache.catalina.Context"});
            mserver.invoke(deployer, "removeServiced",
                           new Object [] {contextName},
                           new String [] {"java.lang.String"});
        } else {
            log.warn("Deployer not found for "+pname.getKeyProperty("host"));
            Service service = getService(pname);
            Engine engine = service.getContainer();
            Host host = (Host) engine.findChild(pname.getKeyProperty("host"));
            host.addChild(context);
        }

        // Return the corresponding MBean name
        return context.getObjectName().toString();

    }


    /**
     * Create a new StandardHost.
     *
     * @param parent MBean Name of the associated parent component
     * @param name Unique name of this Host
     * @param appBase Application base directory name
     * @param autoDeploy Should we auto deploy?
     * @param deployOnStartup Deploy on server startup?
     * @param deployXML Should we deploy Context XML config files property?
     * @param unpackWARs Should we unpack WARs when auto deploying?
     * @return the object name of the created host
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createStandardHost(String parent, String name,
                                     String appBase,
                                     boolean autoDeploy,
                                     boolean deployOnStartup,
                                     boolean deployXML,
                                     boolean unpackWARs)
        throws Exception {

        // Create a new StandardHost instance
        StandardHost host = new StandardHost();
        host.setName(name);
        host.setAppBase(appBase);
        host.setAutoDeploy(autoDeploy);
        host.setDeployOnStartup(deployOnStartup);
        host.setDeployXML(deployXML);
        host.setUnpackWARs(unpackWARs);

        // add HostConfig for active reloading
        HostConfig hostConfig = new HostConfig();
        host.addLifecycleListener(hostConfig);

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Service service = getService(pname);
        Engine engine = service.getContainer();
        engine.addChild(host);

        // Return the corresponding MBean name
        return host.getObjectName().toString();

    }


    /**
     * Creates a new StandardService and StandardEngine.
     *
     * @param domain       Domain name for the container instance
     * @param defaultHost  Name of the default host to be used in the Engine
     * @param baseDir      Base directory value for Engine
     * @return the object name of the created service
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createStandardServiceEngine(String domain,
            String defaultHost, String baseDir) throws Exception{

        if (!(container instanceof Server)) {
            throw new Exception("Container not Server");
        }

        StandardEngine engine = new StandardEngine();
        engine.setDomain(domain);
        engine.setName(domain);
        engine.setDefaultHost(defaultHost);

        Service service = new StandardService();
        service.setContainer(engine);
        service.setName(domain);

        ((Server) container).addService(service);

        return engine.getObjectName().toString();
    }


    /**
     * Create a new StandardManager.
     *
     * @param parent MBean Name of the associated parent component
     * @return the object name of the created manager
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createStandardManager(String parent)
        throws Exception {

        // Create a new StandardManager instance
        StandardManager manager = new StandardManager();

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        if (container instanceof Context) {
            ((Context) container).setManager(manager);
        } else {
            throw new Exception(sm.getString("mBeanFactory.managerContext"));
        }
        ObjectName oname = manager.getObjectName();
        if (oname != null) {
            return oname.toString();
        } else {
            return null;
        }

    }


    /**
     * Create a new  UserDatabaseRealm.
     *
     * @param parent MBean Name of the associated parent component
     * @param resourceName Global JNDI resource name of the associated
     *  UserDatabase
     * @return the object name of the created realm
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createUserDatabaseRealm(String parent, String resourceName)
        throws Exception {

         // Create a new UserDatabaseRealm instance
        UserDatabaseRealm realm = new UserDatabaseRealm();
        realm.setResourceName(resourceName);

        // Add the new instance to its parent component
        return addRealmToParent(parent, realm);
    }


    /**
     * Create a new Valve and associate it with a {@link Container}.
     *
     * @param className The fully qualified class name of the {@link Valve} to
     *                  create
     * @param parent    The MBean name of the associated parent
     *                  {@link Container}.
     *
     * @return  The MBean name of the {@link Valve} that was created or
     *          <code>null</code> if the {@link Valve} does not implement
     *          {@link JmxEnabled}.
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createValve(String className, String parent)
            throws Exception {

        // Look for the parent
        ObjectName parentName = new ObjectName(parent);
        Container container = getParentContainerFromParent(parentName);

        if (container == null) {
            // TODO
            throw new IllegalArgumentException();
        }

        Valve valve = (Valve) Class.forName(className).getConstructor().newInstance();

        container.getPipeline().addValve(valve);

        if (valve instanceof JmxEnabled) {
            return ((JmxEnabled) valve).getObjectName().toString();
        } else {
            return null;
        }
    }


    /**
     * Create a new Web Application Loader.
     *
     * @param parent MBean Name of the associated parent component
     * @return the object name of the created loader
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String createWebappLoader(String parent)
        throws Exception {

        // Create a new WebappLoader instance
        WebappLoader loader = new WebappLoader();

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        if (container instanceof Context) {
            ((Context) container).setLoader(loader);
        }
        // FIXME add Loader.getObjectName
        //ObjectName oname = loader.getObjectName();
        ObjectName oname =
            MBeanUtils.createObjectName(pname.getDomain(), loader);
        return oname.toString();

    }


    /**
     * Remove an existing Connector.
     *
     * @param name MBean Name of the component to remove
     *
     * @exception Exception if a component cannot be removed
     */
    public void removeConnector(String name) throws Exception {

        // Acquire a reference to the component to be removed
        ObjectName oname = new ObjectName(name);
        Service service = getService(oname);
        String port = oname.getKeyProperty("port");
        String address = oname.getKeyProperty("address");
        if (address != null) {
            address = ObjectName.unquote(address);
        }

        Connector conns[] = service.findConnectors();

        for (int i = 0; i < conns.length; i++) {
            String connAddress = null;
            Object objConnAddress = conns[i].getProperty("address");
            if (objConnAddress != null) {
                connAddress = ((InetAddress) objConnAddress).getHostAddress();
            }
            String connPort = ""+conns[i].getPort();

            if (address == null) {
                // Don't combine this with outer if or we could get an NPE in
                // 'else if' below
                if (connAddress == null && port.equals(connPort)) {
                    service.removeConnector(conns[i]);
                    conns[i].destroy();
                    break;
                }
            } else if (address.equals(connAddress) && port.equals(connPort)) {
                service.removeConnector(conns[i]);
                conns[i].destroy();
                break;
            }
        }
    }


    /**
     * Remove an existing Context.
     *
     * @param contextName MBean Name of the component to remove
     *
     * @exception Exception if a component cannot be removed
     */
    public void removeContext(String contextName) throws Exception {

        // Acquire a reference to the component to be removed
        ObjectName oname = new ObjectName(contextName);
        String domain = oname.getDomain();
        StandardService service = (StandardService) getService(oname);

        Engine engine = service.getContainer();
        String name = oname.getKeyProperty("name");
        name = name.substring(2);
        int i = name.indexOf('/');
        String hostName = name.substring(0,i);
        String path = name.substring(i);
        ObjectName deployer = new ObjectName(domain+":type=Deployer,host="+
                                             hostName);
        String pathStr = getPathStr(path);
        if(mserver.isRegistered(deployer)) {
            mserver.invoke(deployer,"addServiced",
                           new Object[]{pathStr},
                           new String[] {"java.lang.String"});
            mserver.invoke(deployer,"unmanageApp",
                           new Object[] {pathStr},
                           new String[] {"java.lang.String"});
            mserver.invoke(deployer,"removeServiced",
                           new Object[] {pathStr},
                           new String[] {"java.lang.String"});
        } else {
            log.warn("Deployer not found for "+hostName);
            Host host = (Host) engine.findChild(hostName);
            Context context = (Context) host.findChild(pathStr);
            // Remove this component from its parent component
            host.removeChild(context);
            if(context instanceof StandardContext)
            try {
                context.destroy();
            } catch (Exception e) {
                log.warn("Error during context [" + context.getName() + "] destroy ", e);
           }

        }

    }


    /**
     * Remove an existing Host.
     *
     * @param name MBean Name of the component to remove
     *
     * @exception Exception if a component cannot be removed
     */
    public void removeHost(String name) throws Exception {

        // Acquire a reference to the component to be removed
        ObjectName oname = new ObjectName(name);
        String hostName = oname.getKeyProperty("host");
        Service service = getService(oname);
        Engine engine = service.getContainer();
        Host host = (Host) engine.findChild(hostName);

        // Remove this component from its parent component
        if(host!=null) {
            engine.removeChild(host);
        }
    }


    /**
     * Remove an existing Loader.
     *
     * @param name MBean Name of the component to remove
     *
     * @exception Exception if a component cannot be removed
     */
    public void removeLoader(String name) throws Exception {

        ObjectName oname = new ObjectName(name);
        // Acquire a reference to the component to be removed
        Container container = getParentContainerFromChild(oname);
        if (container instanceof Context) {
            ((Context) container).setLoader(null);
        }
    }


    /**
     * Remove an existing Manager.
     *
     * @param name MBean Name of the component to remove
     *
     * @exception Exception if a component cannot be removed
     */
    public void removeManager(String name) throws Exception {

        ObjectName oname = new ObjectName(name);
        // Acquire a reference to the component to be removed
        Container container = getParentContainerFromChild(oname);
        if (container instanceof Context) {
            ((Context) container).setManager(null);
        }
    }


    /**
     * Remove an existing Realm.
     *
     * @param name MBean Name of the component to remove
     *
     * @exception Exception if a component cannot be removed
     */
    public void removeRealm(String name) throws Exception {

        ObjectName oname = new ObjectName(name);
        // Acquire a reference to the component to be removed
        Container container = getParentContainerFromChild(oname);
        container.setRealm(null);
    }


    /**
     * Remove an existing Service.
     *
     * @param name MBean Name of the component to remove
     *
     * @exception Exception if a component cannot be removed
     */
    public void removeService(String name) throws Exception {

        if (!(container instanceof Server)) {
            throw new Exception();
        }

        // Acquire a reference to the component to be removed
        ObjectName oname = new ObjectName(name);
        Service service = getService(oname);
        ((Server) container).removeService(service);
    }


    /**
     * Remove an existing Valve.
     *
     * @param name MBean Name of the component to remove
     *
     * @exception Exception if a component cannot be removed
     */
    public void removeValve(String name) throws Exception {

        // Acquire a reference to the component to be removed
        ObjectName oname = new ObjectName(name);
        Container container = getParentContainerFromChild(oname);
        Valve[] valves = container.getPipeline().getValves();
        for (int i = 0; i < valves.length; i++) {
            ObjectName voname = ((JmxEnabled) valves[i]).getObjectName();
            if (voname.equals(oname)) {
                container.getPipeline().removeValve(valves[i]);
            }
        }
    }

}

