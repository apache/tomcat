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

package org.apache.catalina.valves;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.valves.ValveBase;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

/**
 * Valve that explicitly sets the default character set for media subtypes of
 * the "text" type to ISO-8859-1. RFC2616 explicitly states that browsers must
 * use ISO-8859-1 in these circumstances. However, browsers may attempt to
 * auto-detect the character set. This may be exploited by an attacker to
 * perform an XSS attack. Internet Explorer has this behaviour by default. Other
 * browsers have an option to enable it.
 * 
 * This valve prevents the attack by explicitly setting a character set. Unless
 * the provided character set is explicitly overridden by the user - in which
 * case they deserve everything they get - the browser will adhere to an
 * explicitly set character set, thus preventing the XSS attack.
 * 
 * To use this valve add the following <code>&lt;Valve
 * className="org.apache.catalina.valves.AddDefaultCharsetValve" /&gt;</code>
 * to your <code>Engine</code>, <code>Host</code> or <code>Context</code> as
 * required.
 */

public class AddDefaultCharsetValve
    extends ValveBase {

    /**
     * Check for text/* and no character set and set charset to ISO-8859-1 in
     * those circumstances.
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Process the request first
        getNext().invoke(request, response);

        // Test once the response has been generated
        String ct = response.getContentType();
        if (ct != null && ct.startsWith("text/")) {
            // Make sure the charset is explicitly set
            response.setCharacterEncoding(response.getCharacterEncoding());
        }
    }

}
