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
package org.apache.catalina.core;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource.Resource;
import org.apache.tomcat.util.res.StringManager;

/**
 * Implementation of {@code LifecycleListener} that will populate the context's role mapping from a properties file.
 * <p>
 * This listener must only be nested within {@link Context} elements.
 * <p>
 * The keys represent application roles (e.g., admin, user, uservisor, etc.) while the values represent technical roles
 * (e.g., DNs, SIDs, UUIDs, etc.). A key can also be prefixed if, e.g., the properties file contains generic application
 * configuration as well: {@code app-roles.}.
 * <p>
 * Note: The default value for the {@code roleMappingFile} is {@code webapp:/WEB-INF/role-mapping.properties}.
 */
public class PropertiesRoleMappingListener implements LifecycleListener {

    private static final String WEBAPP_PROTOCOL = "webapp:";

    private static final Log log = LogFactory.getLog(PropertiesRoleMappingListener.class);
    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(ContextNamingInfoListener.class);

    private String roleMappingFile = WEBAPP_PROTOCOL + "/WEB-INF/role-mapping.properties";
    private String keyPrefix;

    /**
     * Sets the path to the role mapping properties file. You can use protocol {@code webapp:} and whatever
     * {@link ConfigFileLoader} supports.
     *
     * @param roleMappingFile the role mapping properties file to load from
     *
     * @throws NullPointerException     if roleMappingFile is null
     * @throws IllegalArgumentException if roleMappingFile is empty
     */
    public void setRoleMappingFile(String roleMappingFile) {
        Objects.requireNonNull(roleMappingFile, sm.getString("propertiesRoleMappingListener.roleMappingFileNull"));
        if (roleMappingFile.isEmpty()) {
            throw new IllegalArgumentException(sm.getString("propertiesRoleMappingListener.roleMappingFileEmpty"));
        }

        this.roleMappingFile = roleMappingFile;
    }

    /**
     * Gets the path to the role mapping properties file.
     *
     * @return the path to the role mapping properties file
     */
    public String getRoleMappingFile() {
        return roleMappingFile;
    }

    /**
     * Sets the prefix to filter from property keys. All other keys will be ignored which do not have the prefix.
     *
     * @param keyPrefix the properties key prefix
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * Gets the prefix to filter from property keys.
     *
     * @return the properties key prefix
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
            if (!(event.getLifecycle() instanceof Context)) {
                log.warn(sm.getString("listener.notContext", event.getLifecycle().getClass().getSimpleName()));
                return;
            }
            Properties props = new Properties();
            Context context = (Context) event.getLifecycle();
            try (Resource resource = context.findConfigFileResource(roleMappingFile)) {
                props.load(resource.getInputStream());
            } catch (IOException e) {
                throw new IllegalStateException(
                        sm.getString("propertiesRoleMappingListener.roleMappingFileFail", roleMappingFile), e);
            }

            int linkCount = 0;
            for (Entry<Object,Object> prop : props.entrySet()) {
                String role = (String) prop.getKey();

                if (keyPrefix != null) {
                    if (role.startsWith(keyPrefix)) {
                        role = role.substring(keyPrefix.length());
                    } else {
                        continue;
                    }
                }

                String link = (String) prop.getValue();

                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("propertiesRoleMappingListener.linkedRole", role, link));
                }
                context.addRoleMapping(role, link);
                linkCount++;
            }

            if (log.isDebugEnabled()) {
                log.debug(sm.getString("propertiesRoleMappingListener.linkedRoleCount", Integer.valueOf(linkCount)));
            }
        }
    }

}
