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

package org.apache.catalina.core;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Library;



/**
 * Implementation of <code>LifecycleListener</code> that will init and
 * and destroy APR.
 *
 * @author Remy Maucherat
 * @author Filip Hanik
 * @version $Revision$ $Date$
 * @since 4.1
 */

public class AprLifecycleListener
    implements LifecycleListener {

    private static Log log = LogFactory.getLog(AprLifecycleListener.class);

    /**
     * The string manager for this package.
     */
    protected StringManager sm =
        StringManager.getManager(Constants.Package);


    // ---------------------------------------------- Constants


    protected static final int TCN_REQUIRED_MAJOR = 1;
    protected static final int TCN_REQUIRED_MINOR = 1;
    protected static final int TCN_REQUIRED_PATCH = 8;
    protected static final int TCN_RECOMMENDED_PV = 10;


    // ---------------------------------------------- Properties
    protected static String SSLEngine = "on"; //default on
    protected static String SSLRandomSeed = "builtin";
    protected static boolean sslInitialized = false;
    protected static boolean aprInitialized = false;

    // ---------------------------------------------- LifecycleListener Methods

    /**
     * Primary entry point for startup and shutdown events.
     *
     * @param event The event that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.INIT_EVENT.equals(event.getType())) {
            aprInitialized = init();
            if (aprInitialized) {
                try {
                    initializeSSL();
                } catch (Throwable t) {
                    if (!log.isDebugEnabled()) {
                        log.info(sm.getString("aprListener.sslInit"));
                    } else {
                        log.debug(sm.getString("aprListener.sslInit"), t);
                    }
                }
            }
        } else if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType())) {
            if (!aprInitialized) {
                return;
            }
            try {
                terminateAPR();
            } catch (Throwable t) {
                if (!log.isDebugEnabled()) {
                    log.info(sm.getString("aprListener.aprDestroy"));
                } else {
                    log.debug(sm.getString("aprListener.aprDestroy"), t);
                }
            }
        }

    }

    private static synchronized void terminateAPR()
        throws ClassNotFoundException, NoSuchMethodException,
               IllegalAccessException, InvocationTargetException
    {
        String methodName = "terminate";
        Method method = Class.forName("org.apache.tomcat.jni.Library")
            .getMethod(methodName, (Class [])null);
        method.invoke(null, (Object []) null);
    }

    private boolean init()
    {
        int major = 0;
        int minor = 0;
        int patch = 0;
        if (aprInitialized) {
            return true;    
        }
        try {
            String methodName = "initialize";
            Class paramTypes[] = new Class[1];
            paramTypes[0] = String.class;
            Object paramValues[] = new Object[1];
            paramValues[0] = null;
            Class clazz = Class.forName("org.apache.tomcat.jni.Library");
            Method method = clazz.getMethod(methodName, paramTypes);
            method.invoke(null, paramValues);
            major = clazz.getField("TCN_MAJOR_VERSION").getInt(null);
            minor = clazz.getField("TCN_MINOR_VERSION").getInt(null);
            patch = clazz.getField("TCN_PATCH_VERSION").getInt(null);
        } catch (Throwable t) {
            if (!log.isDebugEnabled()) {
                log.info(sm.getString("aprListener.aprInit",
                        System.getProperty("java.library.path")));
            } else {
                log.debug(sm.getString("aprListener.aprInit",
                        System.getProperty("java.library.path")), t);
            }
            return false;
        }
        if ((major != TCN_REQUIRED_MAJOR)  ||
            (minor != TCN_REQUIRED_MINOR) ||
            (patch <  TCN_REQUIRED_PATCH)) {
            log.error(sm.getString("aprListener.tcnInvalid", major + "."
                    + minor + "." + patch,
                    TCN_REQUIRED_MAJOR + "." +
                    TCN_REQUIRED_MINOR + "." +
                    TCN_REQUIRED_PATCH));
            try {
                // Terminate the APR in case the version
                // is below required.                
                terminateAPR();
            } catch (Throwable t) {
                // Ignore
            }
            return false;
        }
        if (patch <  TCN_RECOMMENDED_PV) {
            if (!log.isDebugEnabled()) {
                log.info(sm.getString("aprListener.tcnVersion", major + "."
                        + minor + "." + patch,
                        TCN_REQUIRED_MAJOR + "." +
                        TCN_REQUIRED_MINOR + "." +
                        TCN_RECOMMENDED_PV));
            } else {
                log.debug(sm.getString("aprListener.tcnVersion", major + "."
                        + minor + "." + patch,
                        TCN_REQUIRED_MAJOR + "." +
                        TCN_REQUIRED_MINOR + "." +
                        TCN_RECOMMENDED_PV));
            }
        }
        if (!log.isDebugEnabled()) {
           log.info(sm.getString("aprListener.tcnValid", major + "."
                    + minor + "." + patch));
        }
        else {
           log.debug(sm.getString("aprListener.tcnValid", major + "."
                     + minor + "." + patch));
        }
        // Log APR flags
        log.info(sm.getString("aprListener.flags", Library.APR_HAVE_IPV6, Library.APR_HAS_SENDFILE, 
                Library.APR_HAS_SO_ACCEPTFILTER, Library.APR_HAS_RANDOM));
        return true;
    }

    private static synchronized void initializeSSL()
        throws ClassNotFoundException, NoSuchMethodException,
               IllegalAccessException, InvocationTargetException
    {

        if ("off".equalsIgnoreCase(SSLEngine)) {
            return;
        }
        if (sslInitialized) {
             //only once per VM
            return;
        }
        String methodName = "randSet";
        Class paramTypes[] = new Class[1];
        paramTypes[0] = String.class;
        Object paramValues[] = new Object[1];
        paramValues[0] = SSLRandomSeed;
        Class clazz = Class.forName("org.apache.tomcat.jni.SSL");
        Method method = clazz.getMethod(methodName, paramTypes);
        method.invoke(null, paramValues);
        

        methodName = "initialize";
        paramValues[0] = "on".equalsIgnoreCase(SSLEngine)?null:SSLEngine;
        method = clazz.getMethod(methodName, paramTypes);
        method.invoke(null, paramValues);
 
        sslInitialized = true;
    }

    public String getSSLEngine() {
        return SSLEngine;
    }

    public void setSSLEngine(String SSLEngine) {
        this.SSLEngine = SSLEngine;
    }

    public String getSSLRandomSeed() {
        return SSLRandomSeed;
    }

    public void setSSLRandomSeed(String SSLRandomSeed) {
        this.SSLRandomSeed = SSLRandomSeed;
    }
}
