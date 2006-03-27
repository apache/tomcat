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

package org.apache.tomcat.util.compat;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;


/**
 *  General-purpose utility to provide backward-compatibility and JDK
 *  independence. This allow use of JDK1.3 ( or higher ) facilities if
 *  available, while maintaining the code compatible with older VMs.
 *
 *  The goal is to make backward-compatiblity reasonably easy.
 *
 *  The base class supports JDK1.3 behavior.
 *
 *  @author Tim Funk
 */
public class JdkCompat {

    // ------------------------------------------------------- Static Variables

    /**
     * class providing java2 support
     */
    static final String JAVA14_SUPPORT =
        "org.apache.tomcat.util.compat.Jdk14Compat";

    /** Return java version as a string
     */
    public static String getJavaVersion() {
        return javaVersion;
    }

    public static boolean isJava2() {
        return java2;
    } 
   
    public static boolean isJava14() {
        return java14;
    }

    public static boolean isJava15() {
        return java15;
    }

    // -------------------- Implementation --------------------
    
    // from ant
    public static final String JAVA_1_0 = "1.0";
    public static final String JAVA_1_1 = "1.1";
    public static final String JAVA_1_2 = "1.2";
    public static final String JAVA_1_3 = "1.3";
    public static final String JAVA_1_4 = "1.4";
    public static final String JAVA_1_5 = "1.5";

    static String javaVersion;
    static boolean java2=false;
    static boolean java14=false;
    static boolean java15=false;
    static JdkCompat jdkCompat;
    
    static {
        init();
    }

    private static void init() {
        try {
            javaVersion = JAVA_1_0;
            Class.forName("java.lang.Void");
            javaVersion = JAVA_1_1;
            Class.forName("java.lang.ThreadLocal");
            java2=true;
            javaVersion = JAVA_1_2;
            Class.forName("java.lang.StrictMath");
            javaVersion = JAVA_1_3;
            Class.forName("java.lang.CharSequence");
            javaVersion = JAVA_1_4;
            java14=true;
            Class.forName("java.lang.Appendable");
            javaVersion = JAVA_1_5;
            java15=true;
        } catch (ClassNotFoundException cnfe) {
            // swallow as we've hit the max class version that we have
        }
        if( java14 ) {
            try {
                Class c=Class.forName(JAVA14_SUPPORT);
                jdkCompat=(JdkCompat)c.newInstance();
            } catch( Exception ex ) {
                jdkCompat=new JdkCompat();
            }
        } else {
            jdkCompat=new JdkCompat();
            // Install jar handler if none installed
        }
    }

    // ----------------------------------------------------------- Constructors
    /**
     *  Default no-arg constructor
     */
    protected JdkCompat() {
    }


    // --------------------------------------------------------- Public Methods
    /**
     * Get a compatibiliy helper class.
     */
    public static JdkCompat getJdkCompat() {
        return jdkCompat;
    }

    /**
     *  Return the URI for the given file. Originally created for
     *  o.a.c.loader.WebappClassLoader
     *
     * @param file The file to wrap into URI
     * @return A URI as a URL
     * @throws MalformedURLException Doh ;)
     */
    public URL getURI(File file)
        throws MalformedURLException {

        File realFile = file;
        try {
            realFile = realFile.getCanonicalFile();
        } catch (IOException e) {
            // Ignore
        }

        return realFile.toURL();
    }


    /**
     *  Return the maximum amount of memory the JVM will attempt to use.
     */
    public long getMaxMemory() {
        return (-1L);
    }


    /**
     * Print out a partial servlet stack trace (truncating at the last 
     * occurrence of javax.servlet.).
     */
    public String getPartialServletStackTrace(Throwable t) {
        StringWriter stackTrace = new StringWriter();
        t.printStackTrace(new PrintWriter(stackTrace));
        String st = stackTrace.toString();
        int i = st.lastIndexOf
            ("org.apache.catalina.core.ApplicationFilterChain.internalDoFilter");
        if (i > -1) {
            return st.substring(0, i - 4);
        } else {
            return st;
        }
    }

    /**
     * Splits a string into it's components.
     * @param path String to split
     * @param pat Pattern to split at
     * @return the components of the path
     */
    public  String [] split(String path, String pat) {
        Vector comps = new Vector();
        int pos = path.indexOf(pat);
        int start = 0;
        while( pos >= 0 ) {
            if(pos > start ) {
                String comp = path.substring(start,pos);
                comps.add(comp);
            }
            start = pos + pat.length();
            pos = path.indexOf(pat,start);
        }
        if( start < path.length()) {
            comps.add(path.substring(start));
        }
        String [] result = new String[comps.size()];
        for(int i=0; i < comps.size(); i++) {
            result[i] = (String)comps.elementAt(i);
        }
        return result;
    }


    /**
     * Chains the <tt>wrapped</tt> throwable to the <tt>wrapper</tt> throwable.
     *
     * @param wrapper The wrapper throwable 
     * @param wrapped The throwable to be wrapped
     */
    public void chainException(Throwable wrapper, Throwable wrapped) {
        // do nothing
    }

 }
