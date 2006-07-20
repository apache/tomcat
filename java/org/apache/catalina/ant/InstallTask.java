/*
 * Copyright 2002,2004 The Apache Software Foundation.
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


package org.apache.catalina.ant;


import java.net.URLEncoder;

import org.apache.tools.ant.BuildException;


/**
 * Ant task that implements the <code>/install</code> command, supported by the
 * Tomcat manager application.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 302726 $ $Date: 2004-02-27 15:59:07 +0100 (ven., 27 févr. 2004) $
 * @since 4.1
 * @deprecated Replaced by DeployTask
 */
public class InstallTask extends AbstractCatalinaTask {


    // ------------------------------------------------------------- Properties


    /**
     * URL of the context configuration file for this application, if any.
     */
    protected String config = null;

    public String getConfig() {
        return (this.config);
    }

    public void setConfig(String config) {
        this.config = config;
    }


    /**
     * The context path of the web application we are managing.
     */
    protected String path = null;

    public String getPath() {
        return (this.path);
    }

    public void setPath(String path) {
        this.path = path;
    }


    /**
     * URL of the web application archive (WAR) file, or the unpacked directory
     * containing this application, if any.
     */
    protected String war = null;

    public String getWar() {
        return (this.war);
    }

    public void setWar(String war) {
        this.war = war;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Execute the requested operation.
     *
     * @exception BuildException if an error occurs
     */
    public void execute() throws BuildException {

        super.execute();
        if (path == null) {
            throw new BuildException
                ("Must specify 'path' attribute");
        }
        if ((config == null) && (war == null)) {
            throw new BuildException
                ("Must specify at least one of 'config' and 'war'");
        }
        StringBuffer sb = new StringBuffer("/install?path=");
        sb.append(URLEncoder.encode(this.path));
        if (config != null) {
            sb.append("&config=");
            sb.append(URLEncoder.encode(config));
        }
        if (war != null) {
            sb.append("&war=");
            sb.append(URLEncoder.encode(war));
        }
        execute(sb.toString());

    }


}
