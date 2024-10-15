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
package org.apache.tomcat.util.digester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.tomcat.util.IntrospectionUtils;

/**
 * A {@link org.apache.tomcat.util.IntrospectionUtils.PropertySource}
 * that uses Kubernetes service bindings to resolve expressions.
 *
 * <p>
 *   The Kubernetes service binding specification can be found at
 *   <a href="https://servicebinding.io/">https://servicebinding.io/</a>.
 * </p>
 *
 * <p><strong>Usage example:</strong></p>
 *
 * Configure the certificate with a service binding.
 *
 * When the service binding is constructed as follows:
 *
 * <pre>
 *    $SERVICE_BINDING_ROOT/
 *                         /custom-certificate/
 *                                            /keyFile
 *                                            /file
 *                                            /chainFile
 *                                            /keyPassword
 * </pre>
 * <pre>
 *   {@code
 *     <SSLHostConfig>
 *           <Certificate certificateKeyFile="${custom-certificate.keyFile}"
 *                        certificateFile="${custom-certificate.file}"
 *                        certificateChainFile="${custom-certificate.chainFile}"
 *                        certificateKeyPassword="${chomp:custom-certificate.keyPassword}"
 *                        type="RSA" />
 *     </SSLHostConfig> }
 * </pre>
 *
 * <p>
 *   The optional <code>chomp:</code> prefix will cause the ServiceBindingPropertySource
 *   to trim a single newline (<code>\r\n</code>, <code>\r</code>, or <code>\n</code>)
 *   from the end of the file, if it exists. This is a convenience for hand-edited
 *   files/values where removing a trailing newline is difficult, and trailing
 *   whitespace changes the meaning of the value.
 * </p>
 *
 * How to configure:
 * <pre>
 * {@code
 *   echo "org.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.ServiceBindingPropertySource" >> conf/catalina.properties}
 * </pre>
 * or add this to {@code CATALINA_OPTS}
 *
 * <pre>
 * {@code
 *   -Dorg.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.ServiceBindingPropertySource}
 * </pre>
 *
 * <b>NOTE</b>: When configured the PropertySource for resolving expressions
 *              from system properties is still active.
 *
 * @see Digester
 *
 * @see <a href="https://tomcat.apache.org/tomcat-9.0-doc/config/systemprops.html#Property_replacements">Tomcat
 *      Configuration Reference System Properties</a>
 */
public class ServiceBindingPropertySource implements IntrospectionUtils.PropertySource {

    private static final String SERVICE_BINDING_ROOT_ENV_VAR = "SERVICE_BINDING_ROOT";

    @Override
    public String getProperty(String key) {
        // get the root to search from
        String serviceBindingRoot = System.getenv(SERVICE_BINDING_ROOT_ENV_VAR);
        if (serviceBindingRoot == null) {
            return null;
        }

        boolean chomp = false;
        if (key.startsWith("chomp:")) {
            chomp = true;
            key = key.substring(6); // Remove the "chomp:" prefix
        }

        // we expect the keys to be in the format $SERVICE_BINDING_ROOT/<binding-name>/<key>
        String[] parts = key.split("\\.");
        if (parts.length != 2) {
            return null;
        }

        Path path = Paths.get(serviceBindingRoot, parts[0], parts[1]);

        if (!path.toFile().exists()) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(path);

            int length = bytes.length;

            if (chomp) {
                if(length > 1 && bytes[length - 2] == '\r' && bytes[length - 1] == '\n') {
                    length -= 2;
                } else if (length > 0) {
                    byte c = bytes[length - 1];
                    if (c == '\r' || c == '\n') {
                        length -= 1;
                    }
                }
            }

            return new String(bytes, 0, length);
        } catch (IOException e) {
            return null;
        }
    }
}
