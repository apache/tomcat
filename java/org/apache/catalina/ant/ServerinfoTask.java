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


import org.apache.tools.ant.BuildException;


/**
 * Ant task that implements the <code>/serverinfo</code> command
 * supported by the Tomcat manager application.
 *
 * @author Vivek Chopra
 * @version $Revision: 302726 $ $Date: 2004-02-27 15:59:07 +0100 (ven., 27 févr. 2004) $
 */
public class ServerinfoTask extends AbstractCatalinaTask {

    // Public Methods

    /**
     * Execute the requested operation.
     *
     * @exception BuildException if an error occurs
     */
    public void execute() throws BuildException {

        super.execute();
        execute("/serverinfo");

    }

}
