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

public interface TomcatController {

    /**
     * @return null if the controller does not handle
     */
    WebResourceSet createMainResourceSet(StandardRoot caller, File f, String docBase, String baseName);

    boolean processWebInfLib(StandardRoot caller, WebResource possibleJar,
            List<WebResourceSet> classResources);

    /**
     * @return true if the controller handle the docBase fix and nothing more need
     *         to be done.
     */
    boolean specificFixDocBase(String originalDocBase);

    void deploySpecificApp(HostConfig caller, Host host, Set<String> invalidWars,
            Map<String, DeployedApplication> deployed, File appBase, String[] filteredAppPaths);

    /**
     * @return a pattern whose first group is the file name and second group its
     *         extension.
     */
    Pattern getSpecificExtensions();

}
