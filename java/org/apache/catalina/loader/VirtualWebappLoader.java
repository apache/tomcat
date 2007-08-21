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
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.ExpandWar;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.resources.Resource;

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
 * IF the attribute readManifestCP is set to true, the Class-Path from the WAR files META-INF/MANIFEST.MF file is also read.
 * <code>
 * &lt;Context docBase="\webapps\mydocbase">
 *   &lt;Loader className="org.apache.catalina.loader.VirtualWebappLoader"
 *              virtualClasspath="\dir\classes:\somedir\somejar.jar"
 *              makeLocalCopy="true"
 *              separator=":"
 *              separateJars="true"
 *              readManifestCP="true"/>
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
    protected boolean makeLocalCopy = true;
    
    /**
     * The location of the libraries.
     */
    protected File tempDir = null;
    
    /**
     * The path seperator
     */
    protected String separator = ";";
    
    /**
     * Separate Jars, means that Jars will be loaded
     * into the classloaded, in a non locking way.
     */
    protected boolean separateJars = true;
    
    /**
     * Read the manifest class-path
     */
    protected boolean readManifestCP = true;
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

    public void setReadManifestCP(boolean readManifestCP) {
        this.readManifestCP = readManifestCP;
    }

    public void setSeparateJars(boolean separateJars) {
        this.separateJars = separateJars;
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
        processClassPath(virtualClasspath,jarFiles,separator);
        
        processManifest(jarFiles);

        
        if (log.isDebugEnabled()) log.debug("Starting parent WebappLoader");
        super.start();
        
        //jars were added as a regular repository above
        if (!separateJars) return;
        
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
                }//catch
            }//for
        }//end if
    }

    protected void processManifest(ArrayList<String> jarFiles) throws LifecycleException {
        //process the meta inf directory
        DirContext resources = getContainer().getResources();
        // Setting up the class repository (/WEB-INF/classes), if it exists
        String metainfPath = "/META-INF";
        DirContext metainf = null;
        try {
            Object object = resources.lookup(metainfPath);
            if (object instanceof DirContext) {
                metainf = (DirContext) object;
                Object o = metainf.lookup("MANIFEST.MF");
                if (o!=null && o instanceof Resource) {
                    Manifest mf = new Manifest(((Resource)o).streamContent());
                    Attributes attr = mf.getMainAttributes();
                    String classpath = (String)attr.getValue("Class-Path");
                    if ( classpath != null ) processClassPath(classpath,jarFiles," ");
                }
            }
        } catch(IOException e) {
            //unable to read manifest
            log.debug("Unable to read manifest.",e);
        } catch(NamingException e) {
            // Silent catch: it's valid that no /META-INF collection exists
        }
    }

    private void processClassPath(String classpath, ArrayList<String> jarFiles, String sep) throws LifecycleException {
        StringTokenizer tkn = new StringTokenizer(classpath, sep);
        while (tkn!=null && tkn.hasMoreTokens()) {
            String ftkn = tkn.nextToken();
            if (ftkn==null) continue;
            File file = new File(ftkn);
            addRepository(jarFiles, file);
        }
    }

    protected void addRepository(ArrayList<String> jarFiles, File file) throws LifecycleException {
        if (!file.exists()) {
            return;
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
        } else if (tmpFile.getAbsolutePath().endsWith(".jar") && separateJars) {
            //addRepository("file:/" + tmpFile.getAbsolutePath());
            jarFiles.add(tmpFile.getAbsolutePath());
        } else {
            if (log.isDebugEnabled()) log.debug("Adding file to virtual repo:"+tmpFile.getAbsolutePath());
            addRepository("file:/" + tmpFile.getAbsolutePath());
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

    public boolean getSeparateJars() {
        return separateJars;
    }

    public boolean getReadManifestCP() {
        return readManifestCP;
    }

    public String getVirtualClasspath() {
        return virtualClasspath;
    }
}
