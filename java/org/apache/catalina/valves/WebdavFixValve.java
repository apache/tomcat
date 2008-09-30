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
 * Valve that attempts to force MS WebDAV clients connecting on port 80 to use
 * a WebDAV client that actually works. Other workarounds that might help
 * include:
 * <ul>
 *   <li>Specifing the port, even if it is port 80, when trying to connect.</li>
 *   <li>Canceling the first authentication dialog box and then trying to
 *       reconnect.</li>
 * </ul>
 * To use this valve add the following <code>&lt;Valve
 * className="org.apache.catalina.valves.WebdavFixValve" /&gt;</code>
 * to your <code>Engine</code>, <code>Host</code> or <code>Context</code> as
 * required. Normally, this valve would be used at the <code>Context</code>
 * level.
 *
 * @version $Revision: 420067 $, $Date: 2006-07-08 09:16:58 +0200 (sub, 08 srp 2006) $
 */

public class WebdavFixValve
    extends ValveBase {

    /**
     * Check for the broken MS WebDAV client and if detected issue a re-direct
     * that hopefully will cause the non-broken client to be used.
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.contains("MiniRedir")) {
            response.sendRedirect(buildRedirect(request));
        } else {
            getNext().invoke(request, response);
        }
    }

    private String buildRedirect(Request request) {
        StringBuffer location =
            new StringBuffer(request.getRequestURL().length());
        location.append(request.getScheme());
        location.append("://");
        location.append(request.getHost().getName());
        location.append(':');
        // If we include the port, even if it is 80, then MS clients will use
        // a WebDAV client that works rather than the MiniRedir that has
        // problems with BASIC authentication
        location.append(request.getServerPort());
        location.append(request.getRequestURI());
        return location.toString();
    }
}
