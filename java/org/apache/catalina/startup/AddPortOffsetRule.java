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

import org.apache.catalina.Server;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;

public class AddPortOffsetRule extends Rule {

    // Set portOffset on all the connectors based on portOffset in the Server
    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {

        Connector conn = (Connector) digester.peek();
        Server server = (Server) digester.peek(2);

        int portOffset = server.getPortOffset();
        conn.setPortOffset(portOffset);

        StringBuilder code = digester.getGeneratedCode();
        if (code != null) {
            code.append(digester.toVariableName(conn)).append(".setPortOffset(");
            code.append(digester.toVariableName(server)).append(".getPortOffset());");
            code.append(System.lineSeparator());
        }
    }
}