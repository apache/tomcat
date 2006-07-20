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

import org.apache.catalina.Context;


/**
    Generates automatic Netscape nsapi_redirect configurations based on
    the Tomcat server.xml settings and the war contexts
    initialized during startup.
    <p>
    This config interceptor is enabled by inserting an NSConfig
    element in the <b>&lt;ContextManager&gt;</b> tag body inside
    the server.xml file like so:
    <pre>
    * < ContextManager ... >
    *   ...
    *   <<b>NSConfig</b> <i>options</i> />
    *   ...
    * < /ContextManager >
    </pre>
    where <i>options</i> can include any of the following attributes:
    <ul>
     <li><b>configHome</b> - default parent directory for the following paths.
                            If not set, this defaults to TOMCAT_HOME. Ignored
                            whenever any of the following paths is absolute.
                             </li>
     <li><b>objConfig</b> - path to use for writing Netscape obj.conf
                            file. If not set, defaults to
                            "conf/auto/obj.conf".</li>
     <li><b>objectName</b> - Name of the Object to execute the requests.
                             Defaults to "servlet".</li>
     <li><b>workersConfig</b> - path to workers.properties file used by 
                                nsapi_redirect. If not set, defaults to
                                "conf/jk/workers.properties".</li>
     <li><b>nsapiJk</b> - path to Netscape mod_jk plugin file.  If not set,
                        defaults to "bin/nsapi_redirect.dll" on windows,
                        "bin/nsapi_rd.nlm" on netware, and
                        "bin/nsapi_redirector.so" everywhere else.</li>
     <li><b>jkLog</b> - path to log file to be used by nsapi_redirect.</li>
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
                             file functions correctly.  If false, let Netscape serve
                             static resources assuming it has been configured
                             to do so. The default is true.
                             Warning: When false, some configuration in
                             the web.xml may not be duplicated in Netscape.
                             Review the uriworkermap file to see what
                             configuration is actually being set in Netscape.</li>
     <li><b>noRoot</b> - If true, the root context is not mapped to
                         Tomcat.  If false and forwardAll is true, all requests
                         to the root context are mapped to Tomcat. If false and
                         forwardAll is false, only JSP and servlets requests to
                         the root context are mapped to Tomcat. When false,
                         to correctly serve Tomcat's root context you must also
                         modify the Home Directory setting in Netscape
                         to point to Tomcat's root context directory.
                         Otherwise some content, such as the root index.html,
                         will be served by Netscape before nsapi_redirect gets a chance
                         to claim the request and pass it to Tomcat.
                         The default is true.</li>
    </ul>
  <p>
    @author Costin Manolache
    @author Larry Isaacs
    @author Gal Shachor
    @author Bill Barker
 */
public class NSConfig  extends BaseJkConfig { 
    private static org.apache.commons.logging.Log log =
        org.apache.commons.logging.LogFactory.getLog(NSConfig.class);

    public static final String WORKERS_CONFIG = "/conf/jk/workers.properties";
    public static final String NS_CONFIG = "/conf/auto/obj.conf";
    public static final String NSAPI_LOG_LOCATION = "/logs/nsapi_redirect.log";
    /** default location of nsapi plug-in. */
    public static final String NSAPI_REDIRECTOR;
    
    //set up some defaults based on OS type
    static{
        String os = System.getProperty("os.name").toLowerCase();
        if(os.indexOf("windows")>=0){
           NSAPI_REDIRECTOR = "bin/nsapi_redirect.dll";
        }else if(os.indexOf("netware")>=0){
           NSAPI_REDIRECTOR = "bin/nsapi_rd.nlm";
        }else{
           NSAPI_REDIRECTOR = "bin/nsapi_redirector.so";
        }
    }

    private File objConfig = null;
    private File nsapiJk = null;
    private String objectName = "servlet";

    public NSConfig() 
    {
    }

    //-------------------- Properties --------------------
    
    /**
        set the path to the output file for the auto-generated
        isapi_redirect registry file.  If this path is relative
        then getRegConfig() will resolve it absolutely against
        the getConfigHome() path.
        <p>
        @param path String path to a file
    */
    public void setObjConfig(String path) {
	objConfig= (path==null)?null:new File(path);
    }

    /**
        set the path to the nsapi plugin module
        @param path String path to a file
    */
    public void setNsapiJk(String path) {
        nsapiJk=( path==null?null:new File(path));
    }

    /**
        Set the name for the Object that implements the
        jk_service call.
        @param name Name of the obj.conf Object
    */
    public void setObjectName(String name) {
        objectName = name;
    }

    // -------------------- Initialize/guess defaults --------------------

    /** Initialize defaults for properties that are not set
	explicitely
    */
    protected void initProperties() {
        super.initProperties();

	objConfig=getConfigFile( objConfig, configHome, NS_CONFIG);
	workersConfig=getConfigFile( workersConfig, configHome, WORKERS_CONFIG);

	if( nsapiJk == null )
	    nsapiJk=new File(NSAPI_REDIRECTOR);
	else
	    nsapiJk =getConfigFile( nsapiJk, configHome, NSAPI_REDIRECTOR );
	jkLog=getConfigFile( jkLog, configHome, NSAPI_LOG_LOCATION);
    }

    // -------------------- Generate config --------------------
    protected PrintWriter getWriter() throws IOException {
	String abObjConfig = objConfig.getAbsolutePath();
	return new PrintWriter(new FileWriter(abObjConfig,append));
    }
    protected boolean generateJkHead(PrintWriter mod_jk) {
	log.info("Generating netscape web server config = "+objConfig );
	
	generateNsapiHead( mod_jk );
	
	mod_jk.println("<Object name=default>");
	return true;
    }

    private void generateNsapiHead(PrintWriter objfile)
    {
        objfile.println("###################################################################");		    
        objfile.println("# Auto generated configuration. Dated: " +  new Date());
        objfile.println("###################################################################");		    
        objfile.println();

        objfile.println("#");        
        objfile.println("# You will need to merge the content of this file with your ");
        objfile.println("# regular obj.conf and then restart (=stop + start) your Netscape server. ");
        objfile.println("#");        
        objfile.println();
            
        objfile.println("#");                    
        objfile.println("# Loading the redirector into your server");
        objfile.println("#");        
        objfile.println();            
        objfile.println("Init fn=\"load-modules\" funcs=\"jk_init,jk_service\" shlib=\"<put full path to the redirector here>\"");
        objfile.println("Init fn=\"jk_init\" worker_file=\"" + 
                        workersConfig.toString().replace('\\', '/') +  
                        "\" log_level=\"" + jkDebug + "\" log_file=\"" + 
                        jkLog.toString().replace('\\', '/') + 
                        "\"");
        objfile.println();
    }

    protected void generateJkTail(PrintWriter objfile)
    {
        objfile.println();
        objfile.println("#######################################################");		    
        objfile.println("# Protecting the WEB-INF and META-INF directories.");
        objfile.println("#######################################################");		    
        objfile.println("PathCheck fn=\"deny-existence\" path=\"*/WEB-INF/*\""); 
        objfile.println("PathCheck fn=\"deny-existence\" path=\"*/META-INF/*\""); 
        objfile.println();

        objfile.println("</Object>");            
        objfile.println();

        objfile.println("#######################################################");		    
        objfile.println("# New object to execute your servlet requests.");
        objfile.println("#######################################################");		    
        objfile.println("<Object name=" + objectName + ">");
        objfile.println("ObjectType fn=force-type type=text/html");
        objfile.println("Service fn=\"jk_service\" worker=\""+ jkWorker + "\" path=\"/*\"");
        objfile.println("</Object>");
        objfile.println();
    }

    // -------------------- Forward all mode --------------------
    
    /** Forward all requests for a context to tomcat.
	The default.
     */
    protected void generateStupidMappings(Context context, PrintWriter objfile )
    {
        String ctxPath  = context.getPath();
	String nPath=("".equals(ctxPath)) ? "/" : ctxPath;

        if( noRoot &&  "".equals(ctxPath) ) {
            log.debug("Ignoring root context in forward-all mode  ");
            return;
        } 
	objfile.println("<Object name=" + context.getName() + ">");

        objfile.println("NameTrans fn=\"assign-name\" from=\"" + ctxPath + "\" name=\"" + objectName + "\""); 
        objfile.println("NameTrans fn=\"assign-name\" from=\"" + ctxPath + "/*\" name=\"" + objectName + "\""); 
	objfile.println("</Object>");
    }


    // -------------------- Netscape serves static mode --------------------
    // This is not going to work for all apps. We fall back to stupid mode.
    
    protected void generateContextMappings(Context context, PrintWriter objfile )
    {
        String ctxPath  = context.getPath();
	String nPath=("".equals(ctxPath)) ? "/" : ctxPath;

        if( noRoot &&  "".equals(ctxPath) ) {
            log.debug("Ignoring root context in non-forward-all mode  ");
            return;
        } 
	objfile.println("<Object name=" + context.getName() + ">");
        // Static files will be served by Netscape
        objfile.println("#########################################################");		    
        objfile.println("# Auto configuration for the " + nPath + " context starts.");
        objfile.println("#########################################################");		    
        objfile.println();

        // XXX Need to determine what if/how static mappings are done

	// InvokerInterceptor - it doesn't have a container,
	// but it's implemented using a special module.
	
	// XXX we need to better collect all mappings
	if(context.getLoginConfig() != null) {
	    String loginPage = context.getLoginConfig().getLoginPage();
	    if(loginPage != null) {
		int lpos = loginPage.lastIndexOf("/");
		String jscurl = loginPage.substring(0,lpos+1) + "j_security_check";
		addMapping( ctxPath, jscurl, objfile);
	    }
	}
	
	String [] servletMaps=context.findServletMappings();
	for(int ii=0; ii < servletMaps.length; ii++) {
	    addMapping( ctxPath , servletMaps[ii] , objfile );
	}
	objfile.println("</Object>");
    }

    /** Add a Netscape extension mapping.
     */
    protected boolean addMapping( String ctxPath, String ext,
					 PrintWriter objfile )
    {
        if( log.isDebugEnabled() )
            log.debug( "Adding extension map for " + ctxPath + "/*." + ext );
	if(! ext.startsWith("/") )
	    ext = "/" + ext;
	if(ext.length() > 1)
	    objfile.println("NameTrans fn=\"assign-name\" from=\"" +
                    ctxPath  + ext + "\" name=\"" + objectName + "\""); 
	return true;
    }

    /** Add a fulling specified Netscape mapping.
     */
    protected boolean addMapping( String fullPath, PrintWriter objfile ) {
        if( log.isDebugEnabled() )
            log.debug( "Adding map for " + fullPath );
        objfile.println("NameTrans fn=\"assign-name\" from=\"" +
                        fullPath + "\" name=\"" + objectName + "\""); 
	return true;
    }

}
