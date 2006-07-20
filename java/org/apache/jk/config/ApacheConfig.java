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

package org.apache.jk.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Hashtable;

import org.apache.catalina.Context;
import org.apache.catalina.Host;

/* The idea is to keep all configuration in server.xml and
   the normal apache config files. We don't want people to
   touch apache ( or IIS, NES ) config files unless they
   want to and know what they're doing ( better than we do :-).

   One nice feature ( if someone sends it ) would be to
   also edit httpd.conf to add the include.

   We'll generate a number of configuration files - this one
   is trying to generate a native apache config file.

   Some web.xml mappings do not "map" to server configuration - in
   this case we need to fallback to forward all requests to tomcat.

   Ajp14 will add to that the posibility to have tomcat and
   apache on different machines, and many other improvements -
   but this should also work for Ajp12, Ajp13 and Jni.

*/

/**
    Generates automatic apache mod_jk configurations based on
    the Tomcat server.xml settings and the war contexts
    initialized during startup.
    <p>
    This config interceptor is enabled by inserting an ApacheConfig
    <code>Listener</code> in 
    the server.xml file like so:
    <pre>
    * < Server ... >
    *   ...
    *   <Listener className=<b>org.apache.ajp.tomcat4.config.ApacheConfig</b> 
    *       <i>options</i> />
    *   ...
    * < /Server >
    </pre>
    where <i>options</i> can include any of the following attributes:
    <ul>
     <li><b>configHome</b> - default parent directory for the following paths.
                            If not set, this defaults to TOMCAT_HOME. Ignored
                            whenever any of the following paths is absolute.
                             </li>
     <li><b>jkConfig</b> - path to use for writing Apache mod_jk conf file. If
                            not set, defaults to
                            "conf/auto/mod_jk.conf".</li>
     <li><b>workersConfig</b> - path to workers.properties file used by 
                            mod_jk. If not set, defaults to
                            "conf/jk/workers.properties".</li>
     <li><b>modJk</b> - path to Apache mod_jk plugin file.  If not set,
                        defaults to "modules/mod_jk.dll" on windows,
                        "modules/mod_jk.nlm" on netware, and
                        "libexec/mod_jk.so" everywhere else.</li>
     <li><b>jkLog</b> - path to log file to be used by mod_jk.</li>
     <li><b>jkDebug</b> - JK Loglevel setting.  May be debug, info, error, or emerg.
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
                         to correctly serve Tomcat's root context you must also
                         modify the DocumentRoot setting in Apache's httpd.conf
                         file to point to Tomcat's root context directory.
                         Otherwise some content, such as Apache's index.html,
                         will be served by Apache before mod_jk gets a chance
                         to claim the request and pass it to Tomcat.
                         The default is true.</li>
    </ul>
    <p>
    @author Costin Manolache
    @author Larry Isaacs
    @author Mel Martinez
    @author Bill Barker
 */
public class ApacheConfig  extends BaseJkConfig { 

    private static org.apache.commons.logging.Log log =
        org.apache.commons.logging.LogFactory.getLog(ApacheConfig.class);

    /** default path to mod_jk .conf location */
    public static final String MOD_JK_CONFIG = "conf/auto/mod_jk.conf";
    /** default path to workers.properties file
	This should be also auto-generated from server.xml.
    */
    public static final String WORKERS_CONFIG = "conf/jk/workers.properties";
    /** default mod_jk log file location */
    public static final String JK_LOG_LOCATION = "logs/mod_jk.log";
    /** default location of mod_jk Apache plug-in. */
    public static final String MOD_JK;
    
    //set up some defaults based on OS type
    static{
        String os = System.getProperty("os.name").toLowerCase();
        if(os.indexOf("windows")>=0){ 
           MOD_JK = "modules/mod_jk.dll";
        }else if(os.indexOf("netware")>=0){
           MOD_JK = "modules/mod_jk.nlm";
        }else{
           MOD_JK = "libexec/mod_jk.so";
        }
    }
    
    private File jkConfig = null;
    private File modJk = null;

    // ssl settings 
    private boolean sslExtract=true;
    private String sslHttpsIndicator="HTTPS";
    private String sslSessionIndicator="SSL_SESSION_ID";
    private String sslCipherIndicator="SSL_CIPHER";
    private String sslCertsIndicator="SSL_CLIENT_CERT";

    Hashtable NamedVirtualHosts=null;
    
    public ApacheConfig() {
    }

    //-------------------- Properties --------------------

    /**
        set the path to the output file for the auto-generated
        mod_jk configuration file.  If this path is relative
        then it will be resolved absolutely against
        the getConfigHome() path.
        <p>
        @param path String path to a file
    */
    public void setJkConfig(String path){
	jkConfig= (path==null)?null:new File(path);
    }

    /**
        set the path to the mod_jk Apache Module
        @param path String path to a file
    */
    public void setModJk(String path){
        modJk=( path==null?null:new File(path));
    }

    /** By default mod_jk is configured to collect SSL information from
	the apache environment and send it to the Tomcat workers. The
	problem is that there are many SSL solutions for Apache and as
	a result the environment variable names may change.

	The following JK related SSL configureation
	can be used to customize mod_jk's SSL behaviour.

	Should mod_jk send SSL information to Tomact (default is On)
    */
    public void setExtractSSL( boolean sslMode ) {
	this.sslExtract=sslMode;
    }

    /** What is the indicator for SSL (default is HTTPS)
     */
    public void setHttpsIndicator( String s ) {
	sslHttpsIndicator=s;
    }

    /**What is the indicator for SSL session (default is SSL_SESSION_ID)
     */
    public void setSessionIndicator( String s ) {
	sslSessionIndicator=s;
    }
    
    /**What is the indicator for client SSL cipher suit (default is SSL_CIPHER)
     */
    public void setCipherIndicator( String s ) {
	sslCipherIndicator=s;
    }

    /** What is the indicator for the client SSL certificated(default
	is SSL_CLIENT_CERT
     */
    public void setCertsIndicator( String s ) {
	sslCertsIndicator=s;
    }

    // -------------------- Initialize/guess defaults --------------------

    /** Initialize defaults for properties that are not set
	explicitely
    */
    protected void initProperties() {
        super.initProperties();

	jkConfig= getConfigFile( jkConfig, configHome, MOD_JK_CONFIG);
	workersConfig=getConfigFile( workersConfig, configHome,
				     WORKERS_CONFIG);
	if( modJk == null )
	    modJk=new File(MOD_JK);
	else
	    modJk=getConfigFile( modJk, configHome, MOD_JK );
	jkLog=getConfigFile( jkLog, configHome, JK_LOG_LOCATION);
    }
    // -------------------- Generate config --------------------
    
    protected PrintWriter getWriter() throws IOException {
	String abJkConfig = jkConfig.getAbsolutePath();
	return new PrintWriter(new FileWriter(abJkConfig, append));
    }
			       

    // -------------------- Config sections  --------------------

    /** Generate the loadModule and general options
     */
    protected boolean generateJkHead(PrintWriter mod_jk)
    {

	mod_jk.println("########## Auto generated on " +  new Date() +
		       "##########" );
	mod_jk.println();

	// Fail if mod_jk not found, let the user know the problem
	// instead of running into problems later.
	if( ! modJk.exists() ) {
	    log.info( "mod_jk location: " + modJk );
	    log.info( "Make sure it is installed corectly or " +
		 " set the config location" );
	    log.info( "Using <Listener className=\""+getClass().getName()+"\"  modJk=\"PATH_TO_MOD_JK.SO_OR_DLL\" />" );
	}
            
	// Verify the file exists !!
	mod_jk.println("<IfModule !mod_jk.c>");
	mod_jk.println("  LoadModule jk_module \""+
		       modJk.toString().replace('\\','/') +
                       "\"");
	mod_jk.println("</IfModule>");
	mod_jk.println();                

	
	// Fail if workers file not found, let the user know the problem
	// instead of running into problems later.
	if( ! workersConfig.exists() ) {
	    log.warn( "Can't find workers.properties at " + workersConfig );
	    log.warn( "Please install it in the default location or " +
		 " set the config location" );
	    log.warn( "Using <Listener className=\"" + getClass().getName() + "\"  workersConfig=\"FULL_PATH\" />" );
	    return false;
	}
            
	mod_jk.println("JkWorkersFile \"" 
		       + workersConfig.toString().replace('\\', '/') 
		       + "\"");

	mod_jk.println("JkLogFile \"" 
		       + jkLog.toString().replace('\\', '/') 
		       + "\"");
	mod_jk.println();

	if( jkDebug != null ) {
	    mod_jk.println("JkLogLevel " + jkDebug);
	    mod_jk.println();
	}
	return true;
    }

    protected void generateVhostHead(Host host, PrintWriter mod_jk) {

        mod_jk.println();
        String vhostip = host.getName();
	String vhost = vhostip;
	int ppos = vhost.indexOf(":");
	if(ppos >= 0)
	    vhost = vhost.substring(0,ppos);

        mod_jk.println("<VirtualHost "+ vhostip + ">");
        mod_jk.println("    ServerName " + vhost );
        String [] aliases=host.findAliases();
        if( aliases.length > 0 ) {
            mod_jk.print("    ServerAlias " );
            for( int ii=0; ii < aliases.length ; ii++) {
                mod_jk.print( aliases[ii] + " " );
            }
            mod_jk.println();
        }
        indent="    ";
    }

    protected void generateVhostTail(Host host, PrintWriter mod_jk) {
        mod_jk.println("</VirtualHost>");
        indent="";
    }
    
    protected void generateSSLConfig(PrintWriter mod_jk) {
	if( ! sslExtract ) {
	    mod_jk.println("JkExtractSSL Off");        
	}
	if( ! "HTTPS".equalsIgnoreCase( sslHttpsIndicator ) ) {
	    mod_jk.println("JkHTTPSIndicator " + sslHttpsIndicator);        
	}
	if( ! "SSL_SESSION_ID".equalsIgnoreCase( sslSessionIndicator )) {
	    mod_jk.println("JkSESSIONIndicator " + sslSessionIndicator);
	}
	if( ! "SSL_CIPHER".equalsIgnoreCase( sslCipherIndicator )) {
	    mod_jk.println("JkCIPHERIndicator " + sslCipherIndicator);
	}
	if( ! "SSL_CLIENT_CERT".equalsIgnoreCase( sslCertsIndicator )) {
	    mod_jk.println("JkCERTSIndicator " + sslCertsIndicator);
	}

	mod_jk.println();
    }

    // -------------------- Forward all mode --------------------
    String indent="";
    
    /** Forward all requests for a context to tomcat.
	The default.
     */
    protected void generateStupidMappings(Context context,
					   PrintWriter mod_jk )
    {
	String ctxPath  = context.getPath();
	if(ctxPath == null)
	    return;

	String nPath=("".equals(ctxPath)) ? "/" : ctxPath;
	
        mod_jk.println();
	mod_jk.println(indent + "JkMount " +  nPath + " " + jkWorker );
	if( "".equals(ctxPath) ) {
	    mod_jk.println(indent + "JkMount " +  nPath + "* " + jkWorker );
            if ( context.getParent() instanceof Host ) {
                mod_jk.println(indent + "DocumentRoot \"" +
                            getApacheDocBase(context) + "\"");
            } else {
                mod_jk.println(indent +
                        "# To avoid Apache serving root welcome files from htdocs, update DocumentRoot");
                mod_jk.println(indent +
                        "# to point to: \"" + getApacheDocBase(context) + "\"");
            }

	} else {
	    mod_jk.println(indent + "JkMount " +  nPath + "/* " + jkWorker );
	}
    }    

    
    private void generateNameVirtualHost( PrintWriter mod_jk, String ip ) {
        if( !NamedVirtualHosts.containsKey(ip) ) {
            mod_jk.println("NameVirtualHost " + ip + "");
            NamedVirtualHosts.put(ip,ip);
        }
    }
    
    // -------------------- Apache serves static mode --------------------
    // This is not going to work for all apps. We fall back to stupid mode.
    
    protected void generateContextMappings(Context context, PrintWriter mod_jk )
    {
	String ctxPath  = context.getPath();
	Host vhost = getHost(context);

        if( noRoot &&  "".equals(ctxPath) ) {
            log.debug("Ignoring root context in non-forward-all mode  ");
            return;
        }

	mod_jk.println();
	mod_jk.println(indent + "#################### " +
		       ((vhost!=null ) ? vhost.getName() + ":" : "" ) +
		       (("".equals(ctxPath)) ? "/" : ctxPath ) +
		       " ####################" );
        mod_jk.println();
	// Dynamic /servet pages go to Tomcat
 
	generateStaticMappings( context, mod_jk );

	// InvokerInterceptor - it doesn't have a container,
	// but it's implemented using a special module.
	
	// XXX we need to better collect all mappings

	if(context.getLoginConfig() != null) {
	    String loginPage = context.getLoginConfig().getLoginPage();
	    if(loginPage != null) {
		int lpos = loginPage.lastIndexOf("/");
		String jscurl = loginPage.substring(0,lpos+1) + "j_security_check";
		addMapping( ctxPath, jscurl, mod_jk);
	    }
	}
	String [] servletMaps = context.findServletMappings();
	for(int ii=0; ii < servletMaps.length; ii++) {
	      addMapping( ctxPath, servletMaps[ii] , mod_jk );
	}
    }

    /** Add an Apache extension mapping.
     */
    protected boolean addExtensionMapping( String ctxPath, String ext,
					 PrintWriter mod_jk )
    {
        if( log.isDebugEnabled() )
            log.debug( "Adding extension map for " + ctxPath + "/*." + ext );
	mod_jk.println(indent + "JkMount " + ctxPath + "/*." + ext
		       + " " + jkWorker);
	return true;
    }
    
    
    /** Add a fulling specified Appache mapping.
     */
    protected boolean addMapping( String fullPath, PrintWriter mod_jk ) {
        if( log.isDebugEnabled() )
            log.debug( "Adding map for " + fullPath );
	mod_jk.println(indent + "JkMount " + fullPath + "  " + jkWorker );
	return true;
    }
    /** Add a partially specified Appache mapping.
     */
    protected boolean addMapping( String ctxP, String ext, PrintWriter mod_jk ) {
        if( log.isDebugEnabled() )
            log.debug( "Adding map for " + ext );
	if(! ext.startsWith("/") )
	    ext = "/" + ext;
	if(ext.length() > 1)
	    mod_jk.println(indent + "JkMount " + ctxP + ext+ "  " + jkWorker );
	return true;
    }

    private void generateWelcomeFiles(Context context, PrintWriter mod_jk ) {
	String wf[]=context.findWelcomeFiles();
	if( wf==null || wf.length == 0 )
	    return;
	mod_jk.print(indent + "    DirectoryIndex ");
	for( int i=0; i<wf.length ; i++ ) {
	    mod_jk.print( wf[i] + " " );
	}
	mod_jk.println();
    }

    /** Mappings for static content. XXX need to add welcome files,
     *  mime mappings ( all will be handled by Mime and Static modules of
     *  apache ).
     */
    private void generateStaticMappings(Context context, PrintWriter mod_jk ) {
	String ctxPath  = context.getPath();

	// Calculate the absolute path of the document base
	String docBase = getApacheDocBase(context);

        if( !"".equals(ctxPath) ) {
            // Static files will be served by Apache
            mod_jk.println(indent + "# Static files ");		    
            mod_jk.println(indent + "Alias " + ctxPath + " \"" + docBase + "\"");
            mod_jk.println();
        } else {
            if ( getHost(context) != null ) {
                mod_jk.println(indent + "DocumentRoot \"" +
                            getApacheDocBase(context) + "\"");
            } else {
                // For root context, ask user to update DocumentRoot setting.
                // Using "Alias / " interferes with the Alias for other contexts.
                mod_jk.println(indent +
                        "# Be sure to update DocumentRoot");
                mod_jk.println(indent +
                        "# to point to: \"" + docBase + "\"");
            }
        }
	mod_jk.println(indent + "<Directory \"" + docBase + "\">");
	mod_jk.println(indent + "    Options Indexes FollowSymLinks");

	generateWelcomeFiles(context, mod_jk);

	// XXX XXX Here goes the Mime types and welcome files !!!!!!!!
	mod_jk.println(indent + "</Directory>");
	mod_jk.println();            
	

	// Deny serving any files from WEB-INF
	mod_jk.println();            
	mod_jk.println(indent +
		       "# Deny direct access to WEB-INF and META-INF");
	mod_jk.println(indent + "#");                        
	mod_jk.println(indent + "<Location \"" + ctxPath + "/WEB-INF/*\">");
	mod_jk.println(indent + "    AllowOverride None");
	mod_jk.println(indent + "    deny from all");
	mod_jk.println(indent + "</Location>");
	// Deny serving any files from META-INF
	mod_jk.println();            
	mod_jk.println(indent + "<Location \"" + ctxPath + "/META-INF/*\">");
	mod_jk.println(indent + "    AllowOverride None");
	mod_jk.println(indent + "    deny from all");
	mod_jk.println(indent + "</Location>");
	if (File.separatorChar == '\\') {
	    mod_jk.println(indent + "#");		    
	    mod_jk.println(indent +
			   "# Use Directory too. On Windows, Location doesn't"
			   + " work unless case matches");
	    mod_jk.println(indent + "#");                        
	    mod_jk.println(indent +
			   "<Directory \"" + docBase + "/WEB-INF/\">");
	    mod_jk.println(indent + "    AllowOverride None");
	    mod_jk.println(indent + "    deny from all");
	    mod_jk.println(indent + "</Directory>");
	    mod_jk.println();
	    mod_jk.println(indent +
			   "<Directory \"" + docBase + "/META-INF/\">");
	    mod_jk.println(indent + "    AllowOverride None");
	    mod_jk.println(indent + "    deny from all");
	    mod_jk.println(indent + "</Directory>");
	}
	mod_jk.println();
    }    

    // -------------------- Utils --------------------

    private String getApacheDocBase(Context context)
    {
	// Calculate the absolute path of the document base
	String docBase = getAbsoluteDocBase(context);
	if (File.separatorChar == '\\') {
	    // use separator preferred by Apache
	    docBase = docBase.replace('\\','/');
	}
        return docBase;
    }

    private String getVirtualHostAddress(String vhost, String vhostip) {
        if( vhostip == null ) {
            if ( vhost != null && vhost.length() > 0 && Character.isDigit(vhost.charAt(0)) )
                vhostip=vhost;
            else
                vhostip="*";
        }
        return vhostip;
    }

}
