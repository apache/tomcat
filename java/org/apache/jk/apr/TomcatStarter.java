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
 
package org.apache.jk.apr;

import java.lang.reflect.Method;

// Hack for Catalina 4.1 who hungs the calling thread.
// Also avoids delays in apache initialization ( tomcat can take a while )

/** 
 * Start some tomcat.
 * 
 */
public class TomcatStarter implements Runnable {
    Class c;
    String args[];
    AprImpl apr = new AprImpl();
    
    public static String mainClasses[]={ "org.apache.tomcat.startup.Main",
                                         "org.apache.catalina.startup.BootstrapService",
                                         "org.apache.catalina.startup.Bootstrap"};

    // If someone has time - we can also guess the classpath and do other
    // fancy guessings.
    
    public static void main( String args[] ) {
        System.err.println("TomcatStarter: main()");
        int nClasses = 0;
        
        try {
            AprImpl.jniMode();            
            // Find the class
            Class c=null;
            for( int i=0; i<mainClasses.length; i++ ) {
                try {
                    System.err.println("Try  " + mainClasses[i]);
                    c=Class.forName( mainClasses[i] );
                } catch( ClassNotFoundException ex  ) {
                    continue;
                }
                if( c!= null ) {
                    ++nClasses;
                    Thread startThread=new Thread( new TomcatStarter(c, args));
                    c=null;
                    startThread.start();
                    break;
                }
            }
            if (nClasses==0)
                System.err.println("No class found  ");

        } catch (Throwable t ) {
            t.printStackTrace(System.err);
        }
    }

    public TomcatStarter( Class c, String args[] ) {
        this.c=c;
        this.args=args;
    }
    
    public void run() {
        System.err.println("Starting " + c.getName());
        try {
            Class argClass=args.getClass();
            Method m=c.getMethod( "main", new Class[] {argClass} );
            m.invoke( c, new Object[] { args } );
            System.out.println("TomcatStarter: Done");
            if (apr.isLoaded())
                apr.jkSetAttribute(0, 0, "channel:jni", "done");
            if (args[0].equals("stop")) {
                  Thread.sleep(5000);
                  Runtime.getRuntime().exit(0);
            }
        } catch( Throwable t ) {
            t.printStackTrace(System.err);
        }
    }
}
