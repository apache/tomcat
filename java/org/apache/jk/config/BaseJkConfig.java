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

package org.apache.jk.config;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;


/**
    Base class for automatic jk based configurations based on
    the Tomcat server.xml settings and the war contexts
    initialized during startup.
    <p>
    This config interceptor is enabled by inserting a Config
    element in the <b>&lt;ContextManager&gt;</b> tag body inside
    the server.xml file like so:
    <pre>
    * < ContextManager ... >
    *   ...
    *   <<b>???Config</b> <i>options</i> />
    *   ...
    * < /ContextManager >
    </pre>
    where <i>options</i> can include any of the following attributes:
    <ul>
     <li><b>configHome</b> - default parent directory for the following paths.
                             If not set, this defaults to TOMCAT_HOME. Ignored
                             whenever any of the following paths is absolute.
                             </li>
     <li><b>workersConfig</b> - path to workers.properties file used by 
                                jk connector. If not set, defaults to
                                "conf/jk/workers.properties".</li>
     <li><b>jkLog</b> - path to log file to be used by jk connector.</li>
     <li><b>jkDebug</b> - Loglevel setting.  May be debug, info, error, or emerg.
                          If not set, defaults to emerg.</li>
     <li><b>jkWorker</b> The desired worker.  Must be set to one of the workers
                         defined in the workers.properties file. "ajp12", "ajp13"
                         or "inprocess" are the workers found in the default
                         workers.properties file. If not specified, defaults
                         to "ajp13" if an Ajp13Interceptor is in use, otherwise
                         it defaults to "ajp12".</li>
     <li><b>forwardAll</b> - If true, forward all requests to Tomcat. This helps
                             insure that all the behavior configured in the web.xml
                             file functions correctly.  If false, let Apache serve
                             static resources. The default is true.
                             Warning: When false, some configuration in
                             the web.xml may not be duplicated in Apache.
                             Review the mod_jk conf file to see what
                             configuration is actually being set in Apache.</li>
     <li><b>noRoot</b> - If true, the root context is not mapped to
                         Tomcat.  If false and forwardAll is true, all requests
                         to the root context are mapped to Tomcat. If false and
                         forwardAll is false, only JSP and servlets requests to
                         the root context are mapped to Tomcat. When false,
                         to correctly serve Tomcat's root context you may also
                         need to modify the web server to point it's home
                         directory to Tomcat's root context directory.
                         Otherwise some content, such as the root index.html,
                         may be served by the web server before the connector
                         gets a chance to claim the request and pass it to Tomcat.
                         The default is true.</li>
    </ul>
    <p>
    @author Costin Manolache
    @author Larry Isaacs
    @author Bill Barker
        @version $Revision$
 */
public class BaseJkConfig  implements LifecycleListener {
    private static org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog(BaseJkConfig.class);

    protected File configHome = null;
    protected File workersConfig = null;

    protected File jkLog = null;
    protected String jkDebug="emerg";
    protected String jkWorker = "ajp13";

    protected boolean noRoot=true;
    protected boolean forwardAll=true;

    protected String tomcatHome;
    protected boolean regenerate=false;
    protected boolean append=false;
    protected boolean legacy=true;

    // -------------------- Tomcat callbacks --------------------


    // Auto-config should be able to react to dynamic config changes,
    // and regenerate the config.

    /** 
     *  Generate the configuration - only when the server is
     *  completely initialized ( before starting )
     */
    public void lifecycleEvent(LifecycleEvent evt) {
        if(Lifecycle.START_EVENT.equals(evt.getType())) {
           execute( evt );
        } 
    }

    /** 
     *  Generate configuration files.  Override with method to generate
     *  web server specific configuration.
     */
    public void execute(LifecycleEvent evt) {
        initProperties();
        PrintWriter mod_jk = null;
        try {
            mod_jk = getWriter();
        } catch(IOException iex) {
            log.warn("Unable to open config file");
            return;
        }
        Lifecycle who = evt.getLifecycle();
        if( who instanceof Server ) {
            executeServer((Server)who, mod_jk);
        } else if(who instanceof Engine) {
            executeEngine((Engine)who, mod_jk);
        } else if ( who instanceof Host ) {
            executeHost((Host)who, mod_jk);
        } else if( who instanceof Context ) {
            executeContext((Context)who, mod_jk);
        }
        mod_jk.close();
    }
    /** 
     *  Generate configuration files.  Override with method to generate
     *  web server specific configuration.
     */
    public void executeServer(Server svr, PrintWriter mod_jk) {
        if(! append ) {
            if( ! generateJkHead(mod_jk) )
                return;
            generateSSLConfig(mod_jk);
            generateJkTail(mod_jk);
        }
    }

    /** 
     * Generate SSL options
     */
    protected void generateSSLConfig(PrintWriter mod_jk) {
    }

    /** 
     * Generate general options
     */
    protected boolean generateJkHead(PrintWriter mod_jk)  {
        return true;
    }

    /** 
     * Generate general options
     */
    protected void generateJkTail(PrintWriter mod_jk) {
    }

    /** 
     * Generate Virtual Host start
     */
    protected void generateVhostHead(Host host, PrintWriter mod_jk) {
    }

    /** 
     * Generate Virtual Host end
     */
    protected void generateVhostTail(Host host, PrintWriter mod_jk) {
    }

    /** 
     *  Generate configuration files.  Override with method to generate
     *  web server specific configuration.
     */
    protected void executeEngine(Engine egn, PrintWriter mod_jk) {
        if(egn.getJvmRoute() != null) {
            jkWorker = egn.getJvmRoute();
        }
        executeServer(egn.getService().getServer(), mod_jk);
        Container [] children = egn.findChildren();
        for(int ii=0; ii < children.length; ii++) {
            if( children[ii] instanceof Host ) {
                executeHost((Host)children[ii], mod_jk);
            } else if( children[ii] instanceof Context ) {
                executeContext((Context)children[ii], mod_jk);
            }
        }
    }
    /**
     *  Generate configuration files.  Override with method to generate
     *  web server specific configuration.
     */
    protected void executeHost(Host hst, PrintWriter mod_jk) {
        generateVhostHead(hst, mod_jk);
        Container [] children = hst.findChildren();
        for(int ii=0; ii < children.length; ii++) {
            if(children[ii] instanceof Context) {
                executeContext((Context)children[ii],mod_jk);
            }
        }
        generateVhostTail(hst, mod_jk);
    }
    /**
     *   executes the ApacheConfig interceptor. This method generates apache
     *   configuration files for use with  mod_jk.
     *   @param context a Context object.
     *   @param mod_jk Writer for output.
    */
    public void executeContext(Context context, PrintWriter mod_jk){

        if(context.getPath().length() > 0 || ! noRoot ) {
            String docRoot = context.getServletContext().getRealPath("/");
            if( forwardAll || docRoot == null)
                generateStupidMappings( context, mod_jk );
            else
                generateContextMappings( context, mod_jk);
        }
    }

    protected void generateStupidMappings(Context context, PrintWriter mod_jk){
    }
    protected void generateContextMappings(Context context, PrintWriter mod_jk){
    }

    /** 
     *  Get the output Writer.  Override with method to generate
     *  web server specific configuration.
     */
    protected PrintWriter getWriter() throws IOException {
        return null;
    }

    /** 
     * Get the host associated with this Container (if any).
     */
    protected Host getHost(Container child) {
        while(child != null && ! (child instanceof Host) ) {
            child = child.getParent();
        }
        return (Host)child;
    }

    //-------------------- Properties --------------------

    /** 
     *  Append to config file.
     *  Set to <code>true</code> if the config information should be 
     *  appended.
     */
    public void setAppend(boolean apnd) {
        append = apnd;
    }

    /** 
     *  If false, we'll try to generate a config that will
     *  let apache serve static files.
     *  The default is true, forward all requests in a context
     *  to tomcat.
     */
    public void setForwardAll( boolean b ) {
        forwardAll=b;
    }

    /** 
     *  Special option - do not generate mappings for the ROOT
     *  context. The default is true, and will not generate the mappings,
     *  not redirecting all pages to tomcat (since /* matches everything).
     *  This means that the web server's root remains intact but isn't
     *  completely servlet/JSP enabled. If the ROOT webapp can be configured
     *  with the web server serving static files, there's no problem setting
     *   this option to false. If not, then setting it true means the web
     *   server will be out of picture for all requests.
     */
    public void setNoRoot( boolean b ) {
        noRoot=b;
    }
    
    /**
     *  set a path to the parent directory of the
     *  conf folder.  That is, the parent directory
     *  within which path setters would be resolved against,
     *  if relative.  For example if ConfigHome is set to "/home/tomcat"
     *  and regConfig is set to "conf/mod_jk.conf" then the resulting 
     *  path used would be: 
     *  "/home/tomcat/conf/mod_jk.conf".</p>
     *  <p>
     *  However, if the path is set to an absolute path,
     *  this attribute is ignored.
     *  <p>
     *  If not set, execute() will set this to TOMCAT_HOME.
     *  @param dir - path to a directory
     */
    public void setConfigHome(String dir){
        if( dir==null ) return;
        File f=new File(dir);
        if(!f.isDirectory()){
            throw new IllegalArgumentException(
                "BaseConfig.setConfigHome(): "+
                "Configuration Home must be a directory! : "+dir);
        }
        configHome = f;
    }

    /**
     *  set a path to the workers.properties file.
     *  @param path String path to workers.properties file
     */
    public void setWorkersConfig(String path){
        workersConfig= (path==null?null:new File(path));
    }

    /**
     *  set the path to the log file
     *   @param path String path to a file
     */
    public void setJkLog(String path){
        jkLog = ( path==null ? null : new File(path));
    }

    /** 
     *  Set the verbosity level
     *  ( use debug, error, etc. ) If not set, no log is written.
     */
    public void setJkDebug( String level ) {
        jkDebug=level;
    }

    /**
     *   Sets the JK worker.
     *   @param worker The worker
     */
    public void setJkWorker(String worker){
        jkWorker = worker;
    }

    public void setLegacy(boolean legacy) {
        this.legacy = legacy;
    }

    // -------------------- Initialize/guess defaults --------------------

    /** 
     *  Initialize defaults for properties that are not set
     *   explicitely
     */
    protected void initProperties() {
        tomcatHome = System.getProperty("catalina.home");
        File tomcatDir = new File(tomcatHome);
        if(configHome==null){
            configHome=tomcatDir;
        }
    }

    // -------------------- Config Utils  --------------------


    /** 
     * Add an extension mapping. Override with method to generate
     *   web server specific configuration
     */
    protected boolean addExtensionMapping( String ctxPath, String ext,
                                         PrintWriter pw ) {
        return true;
    }
    
    
    /** 
     * Add a fulling specified mapping.  Override with method to generate
     * web server specific configuration
     */
    protected boolean addMapping( String fullPath, PrintWriter pw ) {
        return true;
    }

    // -------------------- General Utils --------------------

    protected String getAbsoluteDocBase(Context context) {
        // Calculate the absolute path of the document base
        String docBase = context.getServletContext().getRealPath("/");
        docBase = docBase.substring(0,docBase.length()-1);
        if (!isAbsolute(docBase)){
            docBase = tomcatHome + "/" + docBase;
        }
        docBase = patch(docBase);
        return docBase;
    }

    // ------------------ Grabbed from FileUtil -----------------
    public static File getConfigFile( File base, File configDir, String defaultF )
    {
        if( base==null )
            base=new File( defaultF );
        if( ! base.isAbsolute() ) {
            if( configDir != null )
                base=new File( configDir, base.getPath());
            else
                base=new File( base.getAbsolutePath()); //??
        }
        File parent=new File(base.getParent());
        if(!parent.exists()){
            if(!parent.mkdirs()){
                throw new RuntimeException(
                    "Unable to create path to config file :"+
                    base.getAbsolutePath());
            }
        }
        return base;
    }

    public static String patch(String path) {
        String patchPath = path;

        // Move drive spec to the front of the path
        if (patchPath.length() >= 3 &&
            patchPath.charAt(0) == '/'  &&
            Character.isLetter(patchPath.charAt(1)) &&
            patchPath.charAt(2) == ':') {
            patchPath=patchPath.substring(1,3)+"/"+patchPath.substring(3);
        }

        // Eliminate consecutive slashes after the drive spec
        if (patchPath.length() >= 2 &&
            Character.isLetter(patchPath.charAt(0)) &&
            patchPath.charAt(1) == ':') {
            char[] ca = patchPath.replace('/', '\\').toCharArray();
            char c;
            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < ca.length; i++) {
                if ((ca[i] != '\\') ||
                    (ca[i] == '\\' &&
                        i > 0 &&
                        ca[i - 1] != '\\')) {
                    if (i == 0 &&
                        Character.isLetter(ca[i]) &&
                        i < ca.length - 1 &&
                        ca[i + 1] == ':') {
                        c = Character.toUpperCase(ca[i]);
                    } else {
                        c = ca[i];
                    }

                    sb.append(c);
                }
            }

            patchPath = sb.toString();
        }

        // fix path on NetWare - all '/' become '\\' and remove duplicate '\\'
        if (System.getProperty("os.name").startsWith("NetWare") &&
            path.length() >=3 &&
            path.indexOf(':') > 0) {
            char[] ca = patchPath.replace('/', '\\').toCharArray();
            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < ca.length; i++) {
                if ((ca[i] != '\\') ||
                    (ca[i] == '\\' && i > 0 && ca[i - 1] != '\\')) {
                    sb.append(ca[i]);
                }
            }
            patchPath = sb.toString();
        }

        return patchPath;
    }

    public static boolean isAbsolute( String path ) {
        // normal file
        if( path.startsWith("/" ) ) return true;

        if( path.startsWith(File.separator ) ) return true;

        // win c:
        if (path.length() >= 3 &&
            Character.isLetter(path.charAt(0)) &&
            path.charAt(1) == ':')
            return true;

        // NetWare volume:
        if (System.getProperty("os.name").startsWith("NetWare") &&
            path.length() >=3 &&
            path.indexOf(':') > 0)
            return true;

        return false;
    }
}
