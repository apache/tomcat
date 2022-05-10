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

import java.io.FilePermission;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.security.PermissionCheck;

/**
 * A {@link org.apache.tomcat.util.IntrospectionUtils.SecurePropertySource}
 * that uses Kubernetes service bindings to resolve expressions.
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
 * </pre>
 * <pre>
 *   {@code
 *     <SSLHostConfig>
 *           <Certificate certificateKeyFile="${custom-certificate.keyFile}"
 *                        certificateFile="${custom-certificate.file}"
 *                        certificateChainFile="${custom-certificate.chainFile}"
 *                        type="RSA" />
 *     </SSLHostConfig> }
 * </pre>
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
public class ServiceBindingPropertySource implements IntrospectionUtils.SecurePropertySource {

    private static final String SERVICE_BINDING_ROOT_ENV_VAR = "SERVICE_BINDING_ROOT";

    @Override
    public String getProperty(String key) {
        return null;
    }

    @Override
    public String getProperty(String key, ClassLoader classLoader) {
        // can we determine the service binding root
        if (classLoader instanceof PermissionCheck) {
            Permission p = new RuntimePermission("getenv." + SERVICE_BINDING_ROOT_ENV_VAR, null);
            if (!((PermissionCheck) classLoader).check(p)) {
                return null;
            }
        }

        // get the root to search from
        String serviceBindingRoot = System.getenv(SERVICE_BINDING_ROOT_ENV_VAR);
        if (serviceBindingRoot == null) {
            return null;
        }

        // we expect the keys to be in the format $SERVICE_BINDING_ROOT/<binding-name>/<key>
        String[] parts = key.split("\\.");
        if (parts.length != 2) {
            return null;
        }

        Path path = Paths.get(serviceBindingRoot, parts[0], parts[1]);
        try {
            if (classLoader instanceof PermissionCheck) {
                Permission p = new FilePermission(path.toString(), "read");
                if (!((PermissionCheck) classLoader).check(p)) {
                    return null;
                }
            }
            return new String(Files.readAllBytes(path));
        } catch (IOException e) {
            return null;
        }
    }
}
