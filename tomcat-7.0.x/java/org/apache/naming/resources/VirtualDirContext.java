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
package org.apache.naming.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;

import org.apache.naming.NamingEntry;

/**
 * Extended FileDirContext implementation that will allow loading of tld files
 * from the META-INF directory (or subdirectories) in classpath. This will fully
 * mimic the behavior of compressed jars also when using unjarred resources. Tld
 * files can be loaded indifferently from WEB-INF webapp dir (or subdirs) or
 * from META-INF dir from jars available in the classpath: using this DirContext
 * implementation you will be able to use unexpanded jars during development and
 * to make any tld in them virtually available to the webapp.
 *
 * Sample context xml configuration:
 *
 * <code>
 * &lt;Context docBase="\webapps\mydocbase">
 *   &lt;Resources className="org.apache.naming.resources.VirtualDirContext"
 *              virtualClasspath="\dir\classes;\somedir\somejar.jar"/>
 * &lt;/Resources>
 * </code>
 *
 *
 * <strong>This is not meant to be used for production.
 * Its meant to ease development with IDE's without the
 * need for fully republishing jars in WEB-INF/lib</strong>
 *
 *
 * @author Fabrizio Giustina
 * @version $Id$
 */
public class VirtualDirContext extends FileDirContext {

    /**
     * Map containing generated virtual names for tld files under WEB-INF and
     * the actual file reference.
     */
    private Map<String, File> virtualMappings;

    /**
     * Map containing a mapping for tag files that should be loaded from the
     * META-INF dir of referenced jar files.
     */
    private Map<String, File> tagfileMappings;

    /**
     * <code>;</code> separated list of virtual path elements.
     */
    private String virtualClasspath;

    /**
     * <code>virtualClasspath</code> attribute that will be automatically set
     * from the <code>Context</code> <code>virtualClasspath</code> attribute
     * from the context xml file.
     * @param path <code>;</code> separated list of path elements.
     */
    public void setVirtualClasspath(String path) {
        virtualClasspath = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void allocate() {
        super.allocate();

        virtualMappings = new Hashtable<String, File>();
        tagfileMappings = new Hashtable<String, File>();

        // looks into any META-INF dir found in classpath entries for tld files.
        StringTokenizer tkn = new StringTokenizer(virtualClasspath, ";");
        while (tkn.hasMoreTokens()) {
            File file = new File(tkn.nextToken(), "META-INF");

            if (!file.exists() || !file.isDirectory()) {
                continue;
            }
            scanForTlds(file);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release() {
        super.release();
        virtualMappings = null;
    }

    @Override
    public Attributes getAttributes(String name) throws NamingException {

        // handle "virtual" tlds
        if (name.startsWith("/WEB-INF/") && name.endsWith(".tld")) {
            String tldName = name.substring(name.lastIndexOf("/") + 1);
            if (virtualMappings.containsKey(tldName)) {
                return new FileResourceAttributes(virtualMappings.get(tldName));
            }
        } else if (name.startsWith("/META-INF/tags") && name.endsWith(".tag")
                || name.endsWith(".tagx")) {

            // already loaded tag file
            if (tagfileMappings.containsKey(name)) {
                return new FileResourceAttributes(tagfileMappings.get(name));
            }

            // unknown tagfile, search for it in virtualClasspath
            StringTokenizer tkn = new StringTokenizer(virtualClasspath, ";");
            while (tkn.hasMoreTokens()) {
                File file = new File(tkn.nextToken(), name);
                if (file.exists()) {
                    tagfileMappings.put(name, file);
                    return new FileResourceAttributes(file);
                }
            }
        }

        return super.getAttributes(name);
    }

    @Override
    protected List<NamingEntry> list(File file) {
        List<NamingEntry> entries = super.list(file);

        // adds virtual tlds for WEB-INF listing
        if ("WEB-INF".equals(file.getName())) {
            entries.addAll(getVirtualNamingEntries());
        }

        return entries;
    }

    @Override
    protected Object doLookup(String name) {

        // handle "virtual" tlds
        if (name.startsWith("/WEB-INF/") && name.endsWith(".tld")) {
            String tldName = name.substring(name.lastIndexOf("/") + 1);
            if (virtualMappings.containsKey(tldName)) {
                return new FileResource(virtualMappings.get(tldName));
            }
        } else if (name.startsWith("/META-INF/tags") && name.endsWith(".tag")
                || name.endsWith(".tagx")) {

            // already loaded tag file: we are sure that getAttributes() has
            // already been called if we are here
            File tagFile = tagfileMappings.get(name);
            if (tagFile != null) {
                return new FileResource(tagFile);
            }
        }

        return super.doLookup(name);
    }

    /**
     * Scan a given dir for tld files. Any found tld will be added to the
     * virtualMappings.
     * @param dir Dir to scan for tlds
     */
    private void scanForTlds(File dir) {

        File[] files = dir.listFiles();
        for (int j = 0; j < files.length; j++) {
            File file = files[j];

            if (file.isDirectory()) {
                scanForTlds(file);
            } else if (file.getName().endsWith(".tld")) {
                // just generate a random name using the current timestamp, name
                // doesn't matter since it needs to be referenced by URI
                String virtualTldName = "~" + System.currentTimeMillis() + "~"
                        + file.getName();
                virtualMappings.put(virtualTldName, file);
            }
        }

    }

    /**
     * Returns a list of virtual naming entries.
     * @return list of naming entries, containing tlds in virtualMappings
     */
    private List<NamingEntry> getVirtualNamingEntries() {
        List<NamingEntry> virtual = new ArrayList<NamingEntry>();

        for (String name : virtualMappings.keySet()) {

            File file = virtualMappings.get(name);
            NamingEntry entry = new NamingEntry(name, new FileResource(file),
                    NamingEntry.ENTRY);
            virtual.add(entry);
        }
        return virtual;
    }

}
