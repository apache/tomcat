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
package org.apache.juli;

/**
 * An interface intended for use by class loaders associated with a web
 * application that enables them to provide additional information to JULI about
 * the web application with which they are associated. For any web application
 * the combination of {@link #getWebappName()}, {@link #getHostName()} and
 * {@link #getServiceName()} must be unique.
 */
public interface WebappProperties {

    /**
     * Returns a name for the logging system to use for the web application, if
     * any, associated with the class loader.
     *
     * @return The name to use for the web application or null if none is
     *         available.
     */
    String getWebappName();

    /**
     * Returns a name for the logging system to use for the Host where the
     * web application, if any, associated with the class loader is deployed.
     *
     * @return The name to use for the Host where the web application is
     * deployed or null if none is available.
     */
    String getHostName();

    /**
     * Returns a name for the logging system to use for the Service where the
     * Host, if any, associated with the class loader is deployed.
     *
     * @return The name to use for the Service where the Host is deployed or
     * null if none is available.
     */
    String getServiceName();

    /**
     * Enables JULI to determine if the web application includes a local
     * configuration without JULI having to look for the file which it may not
     * have permission to do when running under a SecurityManager.
     *
     * @return {@code true} if the web application includes a logging
     *         configuration at the standard location of
     *         /WEB-INF/classes/logging.properties.
     */
    boolean hasLoggingConfig();
}
