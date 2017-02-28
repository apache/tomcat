/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.servlet4preview;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public interface ServletContext extends javax.servlet.ServletContext {

    /**
     * Get the default session timeout.
     *
     * @throws UnsupportedOperationException    If called from a
     *    {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *    method of a {@link ServletContextListener} that was not defined in a
     *    web.xml file, a web-fragment.xml file nor annotated with
     *    {@link javax.servlet.annotation.WebListener}. For example, a
     *    {@link ServletContextListener} defined in a TLD would not be able to
     *    use this method.
     *
     * @return The current default session timeout in minutes
     *
     * @since Servlet 4.0
     */
    public int getSessionTimeout();

    /**
     * Set the default session timeout. This method may only be called before
     * the ServletContext is initialised.
     *
     * @param sessionTimeout The new default session timeout in minutes.
     *
     * @throws UnsupportedOperationException    If called from a
     *    {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *    method of a {@link ServletContextListener} that was not defined in a
     *    web.xml file, a web-fragment.xml file nor annotated with
     *    {@link javax.servlet.annotation.WebListener}. For example, a
     *    {@link ServletContextListener} defined in a TLD would not be able to
     *    use this method.
     * @throws IllegalStateException If the ServletContext has already been
     *         initialised
     *
     * @since Servlet 4.0
     */
    public void setSessionTimeout(int sessionTimeout);

}
