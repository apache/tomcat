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
package org.apache.catalina;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.startup.HostConfig.DeployedApplication;
import org.apache.catalina.webresources.StandardRoot;

public final class TomcatControllerHandler {

    public static final ThreadLocal<TomcatController> TOMCAT_CONTROLLER_THREAD_LOCAL = new InheritableThreadLocal<TomcatController>();

    public static final TomcatController DEFAULT_TOMCAT_CONTROLLER = new TomcatController() {

        @Override
        public boolean processWebInfLib(StandardRoot caller, WebResource possibleJar,
                List<WebResourceSet> classResources) {
            return false;
        }

        @Override
        public boolean specificFixDocBase(String originalDocBase) {
            return false;
        }

        @Override
        public WebResourceSet createMainResourceSet(StandardRoot caller, File f, String docBase, String baseName) {
            return null;
        }

        @Override
        public void deploySpecificApp(HostConfig caller, Host host, Set<String> invalidWars,
                Map<String, DeployedApplication> deployed, File appBase, String[] filteredAppPaths) {

        }

        @Override
        public Pattern getSpecificExtensions() {
            return null;
        }

    };

    private TomcatControllerHandler() {
    }

    public static void setTomcatController(TomcatController tomcatController) {
        TOMCAT_CONTROLLER_THREAD_LOCAL.set(tomcatController);
    }

    public static TomcatController getTomcatController() {
        TomcatController tomcatController = TOMCAT_CONTROLLER_THREAD_LOCAL.get();
        if (tomcatController == null) {
            return DEFAULT_TOMCAT_CONTROLLER;
        }
        return tomcatController;
    }
}
