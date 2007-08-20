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
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.jar.JarFile;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.ExpandWar;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Simple webapp classloader that allows a customized classpath to be added
 * through configuration in context xml. Any additional classpath entry will be
 * added to the default webapp classpath, making easy to emulate a standard
 * webapp without the need for assembly all the webapp dependencies as jars in
 * WEB-INF/lib.
 * 
 * The VirtualWebappLoader will add directories and regular files as org.apache.catalina.Loader.addRepository
 * Jar files, or files ending with .jar, file be added as org.apache.catalina.loader.WebappClassLoader.addJar
 * 
 * If the attribute makeLocalCopy is set to true, the VirtualWebappLoader will make a local copy of the 
 * virtual class path into the java.io.tmpdir directory, so that webapps can point to the same master repository,
 * but be kept separate during runtime.
 * 
 * The separator for the virtualClasspath defaulted to ; but can be configured using the separator attribute
 *
 * <code>
 * &lt;Context docBase="\webapps\mydocbase">
 *   &lt;Loader className="org.apache.catalina.loader.VirtualWebappLoader"
 *              virtualClasspath="\dir\classes:\somedir\somejar.jar"
 *              makeLocalCopy="true"
 *              separator=":"/>
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
 * @author Filip Hanik
 * @author Fabrizio Giustina
 * @version $Id: $
 */
public class VirtualWebappLoader extends WebappLoader {

    public static final Log log = LogFactory.getLog(VirtualWebappLoader.class);
    /**
     * <code>;</code> separated list of additional path elements.
     */
    private String virtualClasspath = "";

    /**
     * Should we make a copy of the libraries upon startup
     */
    protected boolean makeLocalCopy = false;
    
    /**
     * The location of the libraries.
     */
    protected File tempDir = null;
    
    /**
     * The path seperator
     */
    protected String separator = ";";
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

    public void setMakeLocalCopy(boolean makeLocalCopy) {
        this.makeLocalCopy = makeLocalCopy;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    @Override
    public void start() throws LifecycleException {
        if (log.isInfoEnabled()) log.info("Starting VirtualWebappLoader for:"+getContainer().getName());
        ArrayList<String> jarFiles = new ArrayList<String>();
        
        // just add any jar/directory set in virtual classpath to the
        // repositories list before calling start on the standard WebappLoader
        if (makeLocalCopy) {
            tempDir = new File(System.getProperty("java.io.tmpdir"), "VirtualWebappLoader-"+System.identityHashCode(this));
            if (log.isDebugEnabled()) log.debug("Creating temporary directory for virtual classpath:"+tempDir.getAbsolutePath());
            if (tempDir.exists()) {
                if (log.isDebugEnabled()) log.debug("Temporary directory exists, will clean out the old one.");
                boolean result = false;
                if (tempDir.isDirectory())
                    result = ExpandWar.deleteDir(tempDir);
                else
                    result = ExpandWar.delete(tempDir);
                if (!result) throw new LifecycleException("Unable to create temp dir for virtual app loader:"+tempDir.getAbsolutePath());
            }
            tempDir.mkdirs();
        }
        StringTokenizer tkn = new StringTokenizer(virtualClasspath, separator);
        while (tkn!=null && tkn.hasMoreTokens()) {
            String ftkn = tkn.nextToken();
            if (ftkn==null) continue;
            File file = new File(ftkn);
            if (!file.exists()) {
                continue;
            }
            File tmpFile = file;
            if (makeLocalCopy) {
                tmpFile = new File(tempDir, file.getName());
                if (log.isDebugEnabled()) log.debug("Creating local copy:"+tmpFile.getAbsolutePath());
                if (!ExpandWar.copy(file, tmpFile)) throw new LifecycleException("Unable to copy resources:"+file.getAbsolutePath());
            }
            if (tmpFile.isDirectory()) {
                if (log.isDebugEnabled()) log.debug("Adding directory to virtual repo:"+tmpFile.getAbsolutePath());
                addRepository("file:/" + tmpFile.getAbsolutePath() + "/");
            } else if (tmpFile.getAbsolutePath().endsWith(".jar")) {
                //addRepository("file:/" + tmpFile.getAbsolutePath());
                jarFiles.add(tmpFile.getAbsolutePath());
            } else {
                if (log.isDebugEnabled()) log.debug("Adding file to virtual repo:"+tmpFile.getAbsolutePath());
                addRepository("file:/" + tmpFile.getAbsolutePath());
            }
        }
        
        if (log.isDebugEnabled()) log.debug("Starting parent WebappLoader");
        super.start();
        
        
        //add JarFiles to the classloader, we can't do that before we start
        //since there is no classloader during that time
        ClassLoader loader = super.getClassLoader();
        if ( loader instanceof WebappClassLoader) {
            WebappClassLoader wloader = (WebappClassLoader)loader;
            for (int i = 0; i < jarFiles.size(); i++) {
                String filename = jarFiles.get(i);
                File file = new File(filename);
                try {
                    JarFile jfile = new JarFile(file);
                    if (log.isDebugEnabled()) log.debug("Adding virtual jar file to classloader:"+filename);
                    wloader.addJar(filename,jfile,file);
                }catch ( Exception iox) {
                    if (log.isDebugEnabled()) log.debug("",iox);
                }
            }
        }        
    }
    
    @Override
    public void stop() throws LifecycleException {
        super.stop();
        boolean result = true;
        if ( tempDir!=null ) result = ExpandWar.deleteDir(tempDir);
        if (!result) log.info("Unable to delete temporary directory:"+tempDir.getAbsolutePath());
    }

    public boolean getMakeLocalCopy() {
        return makeLocalCopy;
    }

    public String getSeparator() {
        return separator;
    }
}
