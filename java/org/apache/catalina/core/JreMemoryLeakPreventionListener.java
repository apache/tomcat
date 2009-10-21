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

import javax.imageio.ImageIO;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;

/**
 * Provide a workaround for known places where the Java Runtime environment uses
 * the context class loader to load a singleton as this will cause a memory leak
 * if a web application class loader happens to be the context class loader at
 * the time. The work-around is to initialise these singletons when Tomcat's
 * common class loader is the context class loader.
 */
public class JreMemoryLeakPreventionListener implements LifecycleListener {

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // Initialise these classes when Tomcat starts
        if (Lifecycle.INIT_EVENT.equals(event.getType())) {
            /*
             * Several components end up calling:
             * sun.awt.AppContext.getAppContext()
             * 
             * Those libraries / components known to trigger memory leaks due to
             * eventual calls to getAppContext() are:
             * 
             * - Google Web Toolkit via its use of javax.imageio
             * - Tomcat via its use of java.beans.Introspector.flushCaches() in
             *   1.6.0_15 onwards
             * - others TBD
             */
            
            // Trigger a call to sun.awt.AppContext.getAppContext(). This will
            // pin the common class loader in memory but that shouldn't be an
            // issue.
            ImageIO.getCacheDirectory();
            
        }
    }

}
