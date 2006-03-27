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
import java.net.MalformedURLException;
import java.net.URL;

//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;


/**
 *  See JdkCompat. This is an extension of that class for Jdk1.4 support.
 *
 * @author Tim Funk
 * @author Remy Maucherat
 */
public class Jdk14Compat extends JdkCompat {
    // -------------------------------------------------------------- Constants

    // ------------------------------------------------------- Static Variables
    //static Log logger = LogFactory.getLog(Jdk14Compat.class);

    // ----------------------------------------------------------- Constructors
    /**
     *  Default no-arg constructor
     */
    protected Jdk14Compat() {
    }


    // --------------------------------------------------------- Public Methods

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

        return realFile.toURI().toURL();
    }


    /**
     *  Return the maximum amount of memory the JVM will attempt to use.
     */
    public long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }


    /**
     * Print out a partial servlet stack trace (truncating at the last 
     * occurrence of javax.servlet.).
     */
    public String getPartialServletStackTrace(Throwable t) {
        StringBuffer trace = new StringBuffer();
        trace.append(t.toString()).append('\n');
        StackTraceElement[] elements = t.getStackTrace();
        int pos = elements.length;
        for (int i = 0; i < elements.length; i++) {
            if ((elements[i].getClassName().startsWith
                 ("org.apache.catalina.core.ApplicationFilterChain"))
                && (elements[i].getMethodName().equals("internalDoFilter"))) {
                pos = i;
            }
        }
        for (int i = 0; i < pos; i++) {
            if (!(elements[i].getClassName().startsWith
                  ("org.apache.catalina.core."))) {
                trace.append('\t').append(elements[i].toString()).append('\n');
            }
        }
        return trace.toString();
    }

    public  String [] split(String path, String pat) {
        return path.split(pat);
    }


    /**
     * Chains the <tt>wrapped</tt> throwable to the <tt>wrapper</tt> throwable.
     *
     * @param wrapper The wrapper throwable 
     * @param wrapped The throwable to be wrapped
     */
    public void chainException(Throwable wrapper, Throwable wrapped) {
        wrapper.initCause(wrapped);
    }

 }
