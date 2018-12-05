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
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * An implementation of LifeCycleListener that loads a native library into the JVM.
 * <p>
 * Native libraries are associated with the class loader of the class that loaded them,
 * and the same library may not be loaded by more than one class loader. Due to that
 * restriction, loading a native library from a Webapp's class loader makes it impossible
 * for other Webapps to load the native library.
 * <p>
 * Loading the native library using this listener solves the issue as it is loaded
 * by a shared class loader (typically the Common class loader, but may vary in some
 * configurations).
 */
public class JniLifecycleListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(JniLifecycleListener.class);
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private String libraryName = "";
    private String libraryPath = "";

    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.BEFORE_START_EVENT.equals(event.getType())) {

            if (!libraryName.isEmpty()) {
                System.loadLibrary(libraryName);
                log.info(sm.getString("jniLifecycleListener.load.name", libraryName));
            } else if (!libraryPath.isEmpty()) {
                System.load(libraryPath);
                log.info(sm.getString("jniLifecycleListener.load.path", libraryPath));
            } else {
                throw new IllegalArgumentException(sm.getString("jniLifecycleListener.missingPathOrName"));
            }
        }
    }

    public void setLibraryName(String libraryName) {

        if (!this.libraryPath.isEmpty()) {
            throw new IllegalArgumentException(sm.getString("jniLifecycleListener.bothPathAndName"));
        }

        this.libraryName = libraryName;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public void setLibraryPath(String libraryPath) {

        if (!this.libraryName.isEmpty()) {
            throw new IllegalArgumentException(sm.getString("jniLifecycleListener.bothPathAndName"));
        }

        this.libraryPath = libraryPath;
    }

    public String getLibraryPath() {
        return libraryPath;
    }

}
