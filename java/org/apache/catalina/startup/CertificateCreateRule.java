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

import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.xml.sax.Attributes;

/**
 * Rule implementation that creates an SSLHostConfigCertificate.
 */
public class CertificateCreateRule extends Rule {

    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
        SSLHostConfig sslHostConfig = (SSLHostConfig) digester.peek();

        Type type;
        String typeValue = attributes.getValue("type");
        if (typeValue == null || typeValue.length() == 0) {
            type = Type.UNDEFINED;
        } else {
            type = Type.valueOf(typeValue);
        }

        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, type);

        digester.push(certificate);

        StringBuilder code = digester.getGeneratedCode();
        if (code != null) {
            code.append(SSLHostConfigCertificate.class.getName()).append(' ')
                    .append(digester.toVariableName(certificate));
            code.append(" = new ").append(SSLHostConfigCertificate.class.getName());
            code.append('(').append(digester.toVariableName(sslHostConfig));
            code.append(", ").append(Type.class.getName().replace('$', '.')).append('.').append(type).append(");");
            code.append(System.lineSeparator());
        }
    }


    @Override
    public void end(String namespace, String name) throws Exception {
        digester.pop();
    }
}
