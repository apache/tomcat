/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;


/**
 * Constants.
 *
 * @author Remy Maucherat
 */
public final class Constants {


    // -------------------------------------------------------------- Constants

    public static final String Package = "org.apache.coyote";

    public static final String DEFAULT_CHARACTER_ENCODING="ISO-8859-1";

    public static final int MAX_NOTES = 32;


    // Request states
    public static final int STAGE_NEW = 0;
    public static final int STAGE_PARSE = 1;
    public static final int STAGE_PREPARE = 2;
    public static final int STAGE_SERVICE = 3;
    public static final int STAGE_ENDINPUT = 4;
    public static final int STAGE_ENDOUTPUT = 5;
    public static final int STAGE_KEEPALIVE = 6;
    public static final int STAGE_ENDED = 7;


    /**
     * Has security been turned on?
     */
    public static final boolean IS_SECURITY_ENABLED =
        (System.getSecurityManager() != null);


    /**
     * If true, custom HTTP status messages will be used in headers.
     */
    public static final boolean USE_CUSTOM_STATUS_MSG_IN_HEADER =
        Boolean.valueOf(System.getProperty(
                "org.apache.coyote.USE_CUSTOM_STATUS_MSG_IN_HEADER",
                "false")).booleanValue();

    /**
     * The request attribute that is set to the value of {@code Boolean.TRUE}
     * if connector processing this request supports Comet API.
     */
    public static final String COMET_SUPPORTED_ATTR =
        "org.apache.tomcat.comet.support";

}
