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


import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * This listener is designed to initialize Jasper before any web applications are
 * started.
 *
 * @author Remy Maucherat
 * @version $Revision$ $Date$
 * @since 4.1
 */

public class JasperListener
    implements LifecycleListener {

    private static Log log = LogFactory.getLog(JasperListener.class);

    /**
     * The string manager for this package.
     */
    protected StringManager sm =
        StringManager.getManager(Constants.Package);

    
    // ---------------------------------------------- LifecycleListener Methods


    /**
     * Primary entry point for startup and shutdown events.
     *
     * @param event The event that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.INIT_EVENT.equals(event.getType())) {
            try {
                // Set JSP factory
                this.getClass().getClassLoader().loadClass
                    ("org.apache.jasper.compiler.JspRuntimeContext");
            } catch (Throwable t) {
                // Should not occur, obviously
                log.warn("Couldn't initialize Jasper", t);
            }
            // Another possibility is to do directly:
            // JspFactory.setDefaultFactory(new JspFactoryImpl());
        }

    }


}
