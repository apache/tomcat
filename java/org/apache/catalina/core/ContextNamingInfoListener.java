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

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.res.StringManager;

/**
 * Implementation of {@code LifecycleListener} that will create context naming information environment entries.
 * <p>
 * This listener must only be nested within {@link Context} elements.
 * <p>
 * The following entries will be added to the initial context ({@code java:comp/env} implied):
 * <ul>
 * <li>Path: {@code context/path} from {@link Context#getPath()}</li>
 * <li>Encoded Path: {@code context/encodedPath} from {@link Context#getEncodedPath()}</li>
 * <li>Webapp Version: {@code context/webappVersion} from {@link Context#getWebappVersion()}</li>
 * <li>Name: {@code context/name} from {@link Context#getName()}</li>
 * <li>Base Name: {@code context/baseName} from {@link Context#getBaseName()}</li>
 * <li>Display Name: {@code context/displayName} from {@link Context#getDisplayName()}</li>
 * </ul>
 * <p>
 * See the <a href="https://tomcat.apache.org/tomcat-12.0-doc/config/context.html#Naming">Tomcat documentation</a> for
 * more details on the values.
 */
public class ContextNamingInfoListener implements LifecycleListener {

    private static final String PATH_ENTRY_NAME = "context/path";
    private static final String ENCODED_PATH_ENTRY_NAME = "context/encodedPath";
    private static final String WEBAPP_VERSION_ENTRY_NAME = "context/webappVersion";
    private static final String NAME_ENTRY_NAME = "context/name";
    private static final String BASE_NAME_ENTRY_NAME = "context/baseName";
    private static final String DISPLAY_NAME_ENTRY_NAME = "context/displayName";

    private static final Log log = LogFactory.getLog(ContextNamingInfoListener.class);
    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(ContextNamingInfoListener.class);

    private boolean emptyOnRoot = true;

    /**
     * Sets whether for the root context {@code context/path} and {@code context/encodedPath} will contain {@code "/"}
     * and {@code context/name} will contain {@code "ROOT"} with a version, if any.
     *
     * @param emptyOnRoot whether paths and name for root context shall be empty
     */
    public void setEmptyOnRoot(boolean emptyOnRoot) {
        this.emptyOnRoot = emptyOnRoot;
    }

    /**
     * Gets whether paths and name for the root context will be empty.
     *
     * @return indicator whether paths and name for the root context will be empty
     */
    public boolean isEmptyOnRoot() {
        return emptyOnRoot;
    }

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
            if (!(event.getLifecycle() instanceof Context)) {
                log.warn(sm.getString("listener.notContext", event.getLifecycle().getClass().getSimpleName()));
                return;
            }
            Context context = (Context) event.getLifecycle();
            String path = context.getPath();
            String encodedPath = context.getEncodedPath();
            String name = context.getName();

            if (!emptyOnRoot && path.isEmpty()) {
                path = encodedPath = "/";
                name = "ROOT" + name;
            }

            addEnvEntry(context, PATH_ENTRY_NAME, path);
            addEnvEntry(context, ENCODED_PATH_ENTRY_NAME, encodedPath);
            addEnvEntry(context, WEBAPP_VERSION_ENTRY_NAME, context.getWebappVersion());
            addEnvEntry(context, NAME_ENTRY_NAME, name);
            addEnvEntry(context, BASE_NAME_ENTRY_NAME, context.getBaseName());
            addEnvEntry(context, DISPLAY_NAME_ENTRY_NAME, context.getDisplayName());
        }
    }

    private void addEnvEntry(Context context, String name, String value) {
        ContextEnvironment ce = new ContextEnvironment();
        ce.setName(name);
        ce.setOverride(true);
        ce.setType("java.lang.String");
        ce.setValue(value);
        if (log.isDebugEnabled()) {
            log.info(sm.getString("contextNamingInfoListener.envEntry", name, value));
        }
        context.getNamingResources().addEnvironment(ce);
    }

}
