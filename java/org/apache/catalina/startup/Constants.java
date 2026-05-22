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
package org.apache.catalina.startup;

/**
 * String constants for the startup package. <br>
 * Note that some values include a leading '/' and that some do not. This is intentional based on how the values are
 * used.
 */
public final class Constants {

    /**
     * Prevents instantiation.
     */
    private Constants() {
    }

    /**
     * The fully qualified name of this package.
     */
    public static final String Package = "org.apache.catalina.startup";

    /**
     * The path to the application context XML file.
     */
    public static final String ApplicationContextXml = "META-INF/context.xml";

    /**
     * The path to the application web.xml file.
     */
    public static final String ApplicationWebXml = "/WEB-INF/web.xml";

    /**
     * The path to the Tomcat web.xml file.
     */
    public static final String TomcatWebXml = "/WEB-INF/tomcat-web.xml";

    /**
     * The path to the default context XML file.
     */
    public static final String DefaultContextXml = "conf/context.xml";

    /**
     * The path to the default web.xml file.
     */
    public static final String DefaultWebXml = "conf/web.xml";

    /**
     * The path to the host context XML file.
     */
    public static final String HostContextXml = "context.xml.default";

    /**
     * The path to the host web.xml file.
     */
    public static final String HostWebXml = "web.xml.default";

    /**
     * The path to the WAR tracker file.
     */
    public static final String WarTracker = "/META-INF/war-tracker";

    /**
     * A value that points to a non-existent file used to suppress loading the default web.xml file.
     * <p>
     * It is useful when embedding Tomcat, when the default configuration is done programmatically, e.g. by calling
     * <code>Tomcat.initWebappDefaults(context)</code>.
     *
     * @see Tomcat
     */
    public static final String NoDefaultWebXml = "org/apache/catalina/startup/NO_DEFAULT_XML";

    /**
     * Name of the system property containing the tomcat product installation path
     */
    public static final String CATALINA_HOME_PROP = "catalina.home";

    /**
     * Name of the system property containing the tomcat instance installation path
     */
    public static final String CATALINA_BASE_PROP = "catalina.base";
}
