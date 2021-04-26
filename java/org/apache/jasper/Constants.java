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
package org.apache.jasper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Some constants and other global data that are used by the compiler and the runtime.
 *
 * @author Anil K. Vijendran
 * @author Harish Prabandham
 * @author Shawn Bayern
 * @author Mark Roth
 */
public class Constants {

    public static final String SPEC_VERSION = "3.0";

    /**
     * These classes/packages are automatically imported by the
     * generated code.
     */
    private static final String[] PRIVATE_STANDARD_IMPORTS = {
        "jakarta.servlet.*",
        "jakarta.servlet.http.*",
        "jakarta.servlet.jsp.*"
    };
    public static final List<String> STANDARD_IMPORTS =
        Collections.unmodifiableList(Arrays.asList(PRIVATE_STANDARD_IMPORTS));

    /**
     * Default size of the JSP buffer.
     */
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    /**
     * Default size for the tag buffers.
     */
    public static final int DEFAULT_TAG_BUFFER_SIZE = 512;

    /**
     * Default tag handler pool size.
     */
    public static final int MAX_POOL_SIZE = 5;

    /**
     * Default URLs to download the plugin for Netscape and IE.
     */
    public static final String NS_PLUGIN_URL =
        "http://java.sun.com/products/plugin/";

    public static final String IE_PLUGIN_URL =
        "http://java.sun.com/products/plugin/1.2.2/jinstall-1_2_2-win.cab#Version=1,2,2,0";

    /**
     * Has security been turned on?
     */
    public static final boolean IS_SECURITY_ENABLED =
        (System.getSecurityManager() != null);

    /**
     * Name of the system property containing
     * the tomcat product installation path
     */
    public static final String CATALINA_HOME_PROP = "catalina.home";


    /**
     * Name of the ServletContext init-param that determines if the XML parsers
     * used for *.tld files will be validating or not.
     * <p>
     * This must be kept in sync with org.apache.catalina.Globals
     */
    public static final String XML_VALIDATION_TLD_INIT_PARAM =
            "org.apache.jasper.XML_VALIDATE_TLD";

    /**
     * Name of the ServletContext init-param that determines if the XML parsers
     * will block the resolution of external entities.
     * <p>
     * This must be kept in sync with org.apache.catalina.Globals
     */
    public static final String XML_BLOCK_EXTERNAL_INIT_PARAM =
            "org.apache.jasper.XML_BLOCK_EXTERNAL";

    /**
     * Name of the ServletContext init-param that determines the JSP
     * factory pool size. Set the value to a positive integer to enable it.
     * The default value is <code>8</code> per thread.
     */
    public static final String JSP_FACTORY_POOL_SIZE_INIT_PARAM =
            "org.apache.jasper.runtime.JspFactoryImpl.POOL_SIZE";

}
