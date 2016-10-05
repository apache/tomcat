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
package org.apache.catalina.webresources.war;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class Handler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new WarURLConnection(u);
    }

    @Override
    protected void setURL(URL u, String protocol, String host, int port, String authority, String userInfo, String path,
            String query, String ref) {
        if (path.startsWith("file:") && !path.startsWith("file:/")) {
            // Work around a problem with the URLs in the security policy file.
            // On Windows, the use of ${catalina.[home|base]} in the policy file
            // results in codebase URLs of the form file:C:/... when they should
            // be file:/C:/...
            // For file: and jar: URLs, the JRE compensates for this. It does not
            // compensate for this for war:file:... URLs. Therefore, we do that
            // here
            path = "file:/" + path.substring(5);
        }
        super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
    }
}
