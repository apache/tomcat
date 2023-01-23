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
package org.apache.catalina.webresources;

import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

import org.apache.catalina.webresources.war.Handler;

@SuppressWarnings("deprecation")
public class TomcatURLStreamHandlerProvider extends URLStreamHandlerProvider {

    private static final String WAR_PROTOCOL = "war";
    private static final String CLASSPATH_PROTOCOL = "classpath";

    static {
        // Create an instance without calling URL.setURLStreamHandlerFactory
        TomcatURLStreamHandlerFactory.disable();
    }


    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (WAR_PROTOCOL.equals(protocol)) {
            return new Handler();
        } else if (CLASSPATH_PROTOCOL.equals(protocol)) {
            return new ClasspathURLStreamHandler();
        }

        // Possible user handler defined via Tomcat's custom API
        return TomcatURLStreamHandlerFactory.getInstance().createURLStreamHandler(protocol);
    }
}
