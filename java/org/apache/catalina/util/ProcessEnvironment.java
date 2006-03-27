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

package org.apache.catalina.util;

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;



/**
 * Encapsulates the Process environment and rules to derive
 * that environment from the servlet container and request information.
 * @author   Martin Dengler [root@martindengler.com]
 * @version  $Revision: 303236 $, $Date: 2004-09-17 01:19:54 +0200 (ven., 17 sept. 2004) $
 * @since    Tomcat 4.0
 */
public class ProcessEnvironment {
    
    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( ProcessEnvironment.class );
    
    /** context of the enclosing servlet */
    private ServletContext context = null;

    /** real file system directory of the enclosing servlet's web app */
    private String webAppRootDir = null;

    /** context path of enclosing servlet */
    private String contextPath = null;

    /** pathInfo for the current request */
    protected String pathInfo = null;

    /** servlet URI of the enclosing servlet */
    private String servletPath = null;

    /** derived process environment */
    protected Hashtable env = null;

    /** command to be invoked */
    protected String command = null;

    /** whether or not this object is valid or not */
    protected boolean valid = false;

    /** the debugging detail level for this instance. */
    protected int debug = 0;

    /** process' desired working directory */
    protected File workingDirectory = null;


    /**
     * Creates a ProcessEnvironment and derives the necessary environment,
     * working directory, command, etc.
     * @param  req       HttpServletRequest for information provided by
     *                   the Servlet API
     * @param  context   ServletContext for information provided by
     *                   the Servlet API
     */
    public ProcessEnvironment(HttpServletRequest req,
        ServletContext context) {
        this(req, context, 0);
    }


    /**
     * Creates a ProcessEnvironment and derives the necessary environment,
     * working directory, command, etc.
     * @param  req       HttpServletRequest for information provided by
     *                   the Servlet API
     * @param  context   ServletContext for information provided by
     *                   the Servlet API
     * @param  debug     int debug level (0 == none, 4 == medium, 6 == lots)
     */
    public ProcessEnvironment(HttpServletRequest req,
        ServletContext context, int debug) {
            this.debug = debug;
            setupFromContext(context);
            setupFromRequest(req);
            this.valid = deriveProcessEnvironment(req);
            if (log.isDebugEnabled()) 
                log.debug(this.getClass().getName() + "() ctor, debug level " + 
                          debug);
    }


    /**
     * Uses the ServletContext to set some process variables
     * @param  context   ServletContext for information provided by
     *                   the Servlet API
     */
    protected void setupFromContext(ServletContext context) {
        this.context = context;
        this.webAppRootDir = context.getRealPath("/");
    }


    /**
     * Uses the HttpServletRequest to set most process variables
     * @param  req   HttpServletRequest for information provided by
     *               the Servlet API
     */
    protected void setupFromRequest(HttpServletRequest req) {
        this.contextPath = req.getContextPath();
        this.pathInfo = req.getPathInfo();
        this.servletPath = req.getServletPath();
    }


    /**
     * Print important process environment information in an
     * easy-to-read HTML table
     * @return  HTML string containing process environment info
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("<TABLE border=2>");
        sb.append("<tr><th colspan=2 bgcolor=grey>");
        sb.append("ProcessEnvironment Info</th></tr>");
        sb.append("<tr><td>Debug Level</td><td>");
        sb.append(debug);
        sb.append("</td></tr>");
        sb.append("<tr><td>Validity:</td><td>");
        sb.append(isValid());
        sb.append("</td></tr>");
        if (isValid()) {
            Enumeration envk = env.keys();
            while (envk.hasMoreElements()) {
                String s = (String)envk.nextElement();
                sb.append("<tr><td>");
                sb.append(s);
                sb.append("</td><td>");
                sb.append(blanksToString((String)env.get(s),
                    "[will be set to blank]"));
                    sb.append("</td></tr>");
            }
        }
        sb.append("<tr><td colspan=2><HR></td></tr>");
        sb.append("<tr><td>Derived Command</td><td>");
        sb.append(nullsToBlanks(command));
        sb.append("</td></tr>");
        sb.append("<tr><td>Working Directory</td><td>");
        if (workingDirectory != null) {
            sb.append(workingDirectory.toString());
        }
        sb.append("</td></tr>");
        sb.append("</TABLE><p>end.");
        return sb.toString();
    }


    /**
     * Gets derived command string
     * @return  command string
     */
    public String getCommand() {
        return command;
    }


    /**
     * Sets the desired command string
     * @param   command String command as desired
     * @return  command string
     */
    protected String setCommand(String command) {
        return command;
    }


    /**
     * Gets this process' derived working directory
     * @return  working directory
     */
    public File getWorkingDirectory() {
        return workingDirectory;
    }


    /**
     * Gets process' environment
     * @return   process' environment
     */
    public Hashtable getEnvironment() {
        return env;
    }


    /**
     * Sets process' environment
     * @param    env process' environment
     * @return   Hashtable to which the process' environment was set
     */
    public Hashtable setEnvironment(Hashtable env) {
        this.env = env;
        return this.env;
    }


    /**
     * Gets validity status
     * @return   true if this environment is valid, false otherwise
     */
    public boolean isValid() {
        return valid;
    }


    /**
     * Converts null strings to blank strings ("")
     * @param    s string to be converted if necessary
     * @return   a non-null string, either the original or the empty string
     *           ("") if the original was <code>null</code>
     */
    protected String nullsToBlanks(String s) {
        return nullsToString(s, "");
    }


    /**
     * Converts null strings to another string
     * @param    couldBeNull string to be converted if necessary
     * @param    subForNulls string to return instead of a null string
     * @return   a non-null string, either the original or the substitute
     *           string if the original was <code>null</code>
     */
    protected String nullsToString(String couldBeNull, String subForNulls) {
        return (couldBeNull == null ? subForNulls : couldBeNull);
    }


    /**
     * Converts blank strings to another string
     * @param    couldBeBlank string to be converted if necessary
     * @param    subForBlanks string to return instead of a blank string
     * @return   a non-null string, either the original or the substitute
     *           string if the original was <code>null</code> or empty ("")
     */
    protected String blanksToString(String couldBeBlank,
        String subForBlanks) {
            return (("".equals(couldBeBlank) || couldBeBlank == null) ?
                subForBlanks : couldBeBlank);
    }


    /**
     * Constructs the Process environment to be supplied to the invoked
     * process.  Defines an environment no environment variables.
     * <p>
     * Should be overriden by subclasses to perform useful setup.
     * </p>
     *
     * @param    req request associated with the
     *           Process' invocation
     * @return   true if environment was set OK, false if there was a problem
     *           and no environment was set
     */
    protected boolean deriveProcessEnvironment(HttpServletRequest req) {

        Hashtable envp = new Hashtable();
        command = getCommand();
        if (command != null) {
            workingDirectory = new
                File(command.substring(0,
                command.lastIndexOf(File.separator)));
                envp.put("X_TOMCAT_COMMAND_PATH", command); //for kicks
        }
        this.env = envp;
        return true;
    }


    /**
     * Gets the root directory of the web application to which this process\
     * belongs
     * @return  root directory
     */
    public String getWebAppRootDir() {
        return webAppRootDir;
    }


    public String getContextPath(){
            return contextPath;
        }


    public ServletContext getContext(){
            return context;
        }


    public String getServletPath(){
            return servletPath;
        }
}
