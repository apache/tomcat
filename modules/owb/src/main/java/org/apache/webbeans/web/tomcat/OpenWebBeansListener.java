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
package org.apache.webbeans.web.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.FrameworkListener;

/**
 * This listener must be declared in server.xml as a Server listener to be active.
 * It will add OpenWebBeansContextLifecycleListener on all contexts.
 */
public class OpenWebBeansListener extends FrameworkListener {

    public OpenWebBeansListener() {
        // Try loading a class from OpenWebBeans to make sure it is available
        new org.apache.webbeans.exception.WebBeansConfigurationException("");
    }

    @Override
    protected LifecycleListener createLifecycleListener(Context context) {
        OpenWebBeansContextLifecycleListener listener = new OpenWebBeansContextLifecycleListener();
        listener.setAddSecurityValve(getAddSecurityValve());
        listener.setStartWithoutBeansXml(getStartWithoutBeansXml());
        return listener;
    }

    /**
     * Add security valve.
     */
    protected boolean addSecurityValve = true;

    /**
     * @return true to add the security valve
     */
    public boolean getAddSecurityValve() {
        return addSecurityValve;
    }

    /**
     * Configure if a security valve will be added
     * @param addSecurityValve the addSecurityValve to set
     */
    public void setAddSecurityValve(boolean addSecurityValve) {
        this.addSecurityValve = addSecurityValve;
    }

    /**
     * Start without a beans.xml file.
     */
    protected boolean startWithoutBeansXml = true;

    /**
     * @return the startWithoutBeansXml
     */
    public boolean getStartWithoutBeansXml() {
        return startWithoutBeansXml;
    }

    /**
     * @param startWithoutBeansXml the startWithoutBeansXml to set
     */
    public void setStartWithoutBeansXml(boolean startWithoutBeansXml) {
        this.startWithoutBeansXml = startWithoutBeansXml;
    }

}
