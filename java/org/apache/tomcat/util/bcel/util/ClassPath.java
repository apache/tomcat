/*
 * Copyright  2000-2009 The Apache Software Foundation
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
 *
 */
package org.apache.tomcat.util.bcel.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Responsible for loading (class) files from the CLASSPATH. Inspired by
 * sun.tools.ClassPath.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class ClassPath implements Serializable {

    public static final ClassPath SYSTEM_CLASS_PATH = new ClassPath();
    private PathEntry[] paths;
    private String class_path;


    /**
     * Search for classes in given path.
     */
    public ClassPath(String class_path) {
        this.class_path = class_path;
        List vec = new ArrayList();
        for (StringTokenizer tok = new StringTokenizer(class_path, System
                .getProperty("path.separator")); tok.hasMoreTokens();) {
            String path = tok.nextToken();
            if (!path.equals("")) {
                File file = new File(path);
                try {
                    if (file.exists()) {
                        if (file.isDirectory()) {
                            vec.add(new Dir(path));
                        } else {
                            vec.add(new Zip(new ZipFile(file)));
                        }
                    }
                } catch (IOException e) {
                    System.err.println("CLASSPATH component " + file + ": " + e);
                }
            }
        }
        paths = new PathEntry[vec.size()];
        vec.toArray(paths);
    }


    /**
     * Search for classes in CLASSPATH.
     * @deprecated Use SYSTEM_CLASS_PATH constant
     */
    public ClassPath() {
        this(getClassPath());
    }


    /** @return used class path string
     */
    public String toString() {
        return class_path;
    }


    public int hashCode() {
        return class_path.hashCode();
    }


    public boolean equals( Object o ) {
        if (o instanceof ClassPath) {
            return class_path.equals(((ClassPath) o).class_path);
        }
        return false;
    }


    private static final void getPathComponents( String path, List list ) {
        if (path != null) {
            StringTokenizer tok = new StringTokenizer(path, File.pathSeparator);
            while (tok.hasMoreTokens()) {
                String name = tok.nextToken();
                File file = new File(name);
                if (file.exists()) {
                    list.add(name);
                }
            }
        }
    }


    /** Checks for class path components in the following properties:
     * "java.class.path", "sun.boot.class.path", "java.ext.dirs"
     *
     * @return class path as used by default by BCEL
     */
    public static final String getClassPath() {
        String class_path = System.getProperty("java.class.path");
        String boot_path = System.getProperty("sun.boot.class.path");
        String ext_path = System.getProperty("java.ext.dirs");
        List list = new ArrayList();
        getPathComponents(class_path, list);
        getPathComponents(boot_path, list);
        List dirs = new ArrayList();
        getPathComponents(ext_path, dirs);
        for (Iterator e = dirs.iterator(); e.hasNext();) {
            File ext_dir = new File((String) e.next());
            String[] extensions = ext_dir.list(new FilenameFilter() {

                public boolean accept( File dir, String name ) {
                    name = name.toLowerCase(Locale.ENGLISH);
                    return name.endsWith(".zip") || name.endsWith(".jar");
                }
            });
            if (extensions != null) {
                for (int i = 0; i < extensions.length; i++) {
                    list.add(ext_dir.getPath() + File.separatorChar + extensions[i]);
                }
            }
        }
        StringBuffer buf = new StringBuffer();
        for (Iterator e = list.iterator(); e.hasNext();) {
            buf.append((String) e.next());
            if (e.hasNext()) {
                buf.append(File.pathSeparatorChar);
            }
        }
        return buf.toString().intern();
    }


    /**
     * @param name fully qualified class name, e.g. java.lang.String
     * @return input stream for class
     */
    public InputStream getInputStream( String name ) throws IOException {
        return getInputStream(name.replace('.', '/'), ".class");
    }


    /**
     * Return stream for class or resource on CLASSPATH.
     *
     * @param name fully qualified file name, e.g. java/lang/String
     * @param suffix file name ends with suff, e.g. .java
     * @return input stream for file on class path
     */
    public InputStream getInputStream( String name, String suffix ) throws IOException {
        InputStream is = null;
        try {
            is = getClass().getClassLoader().getResourceAsStream(name + suffix);
        } catch (Exception e) {
        }
        if (is != null) {
            return is;
        }
        return getClassFile(name, suffix).getInputStream();
    }

    
    
    

    

    /**
     * @param name fully qualified file name, e.g. java/lang/String
     * @param suffix file name ends with suff, e.g. .java
     * @return class file for the java class
     */
    public ClassFile getClassFile( String name, String suffix ) throws IOException {
        for (int i = 0; i < paths.length; i++) {
            ClassFile cf;
            if ((cf = paths[i].getClassFile(name, suffix)) != null) {
                return cf;
            }
        }
        throw new IOException("Couldn't find: " + name + suffix);
    }


    


    


    


    


    

    private static abstract class PathEntry implements Serializable {

        abstract ClassFile getClassFile( String name, String suffix ) throws IOException;
        abstract URL getResource(String name);
        abstract InputStream getResourceAsStream(String name);
    }

    /** Contains information about file/ZIP entry of the Java class.
     */
    public interface ClassFile {

        /** @return input stream for class file.
         */
        public abstract InputStream getInputStream() throws IOException;


        


        


        


        
    }

    private static class Dir extends PathEntry {

        private String dir;


        Dir(String d) {
            dir = d;
        }

        URL getResource(String name) {
            // Resource specification uses '/' whatever the platform
            final File file = new File(dir + File.separatorChar + name.replace('/', File.separatorChar));
            try {
                return file.exists() ? file.toURL() : null;
            } catch (MalformedURLException e) {
               return null;
            }
        }
        
        InputStream getResourceAsStream(String name) {
            // Resource specification uses '/' whatever the platform
            final File file = new File(dir + File.separatorChar + name.replace('/', File.separatorChar));
            try {
               return file.exists() ? new FileInputStream(file) : null;
            } catch (IOException e) {
               return null;
            }
        }

        ClassFile getClassFile( String name, String suffix ) throws IOException {
            final File file = new File(dir + File.separatorChar
                    + name.replace('.', File.separatorChar) + suffix);
            return file.exists() ? new ClassFile() {

                public InputStream getInputStream() throws IOException {
                    return new FileInputStream(file);
                }
            } : null;
        }


        public String toString() {
            return dir;
        }
    }

    private static class Zip extends PathEntry {

        private ZipFile zip;


        Zip(ZipFile z) {
            zip = z;
        }

        URL getResource(String name) {
            final ZipEntry entry = zip.getEntry(name);
            try {
                return (entry != null) ? new URL("jar:file:" + zip.getName() + "!/" + name) : null;
            } catch (MalformedURLException e) {
                return null;
           }
        }
        
        InputStream getResourceAsStream(String name) {
            final ZipEntry entry = zip.getEntry(name);
            try {
                return (entry != null) ? zip.getInputStream(entry) : null;
            } catch (IOException e) {
                return null;
            }
        }
        	
        ClassFile getClassFile( String name, String suffix ) throws IOException {
            final ZipEntry entry = zip.getEntry(name.replace('.', '/') + suffix);
            
            if (entry == null)
            	return null;
            
            return new ClassFile() {

                public InputStream getInputStream() throws IOException {
                    return zip.getInputStream(entry);
                }
            };
        }
    }
}
