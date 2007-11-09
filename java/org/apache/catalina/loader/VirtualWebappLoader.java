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
package org.apache.catalina.loader;

import java.io.File;
import java.util.StringTokenizer;

import org.apache.catalina.LifecycleException;

/**
 * Simple webapp classloader that allows a customized classpath to be added
 * through configuration in context xml. Any additional classpath entry will be
 * added to the default webapp classpath, making easy to emulate a standard
 * webapp without the need for assembly all the webapp dependencies as jars in
 * WEB-INF/lib.
 *
 * <code>
 * &lt;Context docBase="\webapps\mydocbase">
 *   &lt;Loader className="org.apache.catalina.loader.VirtualWebappLoader"
 *              virtualClasspath="\dir\classes;\somedir\somejar.jar"/>
 * &lt;/Context>
 * </code>
 *
 *
 * <strong>This is not meant to be used for production.
 * Its meant to ease development with IDE's without the
 * need for fully republishing jars in WEB-INF/lib</strong>
 *
 *
 *
 * @author Fabrizio Giustina
 * @version $Id: $
 */
public class VirtualWebappLoader extends WebappLoader {

    /**
     * <code>;</code> separated list of additional path elements.
     */
    private String virtualClasspath;

    /**
     * Construct a new WebappLoader with no defined parent class loader (so that
     * the actual parent will be the system class loader).
     */
    public VirtualWebappLoader() {
        super();
    }

    /**
     * Construct a new WebappLoader with the specified class loader to be
     * defined as the parent of the ClassLoader we ultimately create.
     *
     * @param parent The parent class loader
     */
    public VirtualWebappLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * <code>virtualClasspath</code> attribute that will be automatically set
     * from the <code>Context</code> <code>virtualClasspath</code> attribute
     * from the context xml file.
     * @param path <code>;</code> separated list of path elements.
     */
    public void setVirtualClasspath(String path) {
        virtualClasspath = path;
    }

    @Override
    public void start() throws LifecycleException {

        // just add any jar/directory set in virtual classpath to the
        // repositories list before calling start on the standard WebappLoader
        StringTokenizer tkn = new StringTokenizer(virtualClasspath, ";");
        while (tkn.hasMoreTokens()) {
            File file = new File(tkn.nextToken());
            if (!file.exists()) {
                continue;
            }
            if (file.isDirectory()) {
                addRepository("file:/" + file.getAbsolutePath() + "/");
            } else {
                addRepository("file:/" + file.getAbsolutePath());
            }
        }

        super.start();
    }

}
