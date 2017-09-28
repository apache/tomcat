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
import javax.servlet.ServletRegistration;

/**
 * Provides early access to some parts of the proposed Servlet 4.0 API.
 *
 * @deprecated This class is not included in Tomcat 9 onwards. Users of this
 *             class should normally upgrade to Tomcat 9 and switch to the
 *             Servlet 4.0 API. If the functionality is required in Tomcat 8.5,
 *             then the Tomcat implementation classes should be used directly.
 *             This class may be removed from Tomcat 8.5.x some time after 30
 *             September 2018.
 */
@Deprecated
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

    /**
     *
     * @param jspName   The servlet name under which this JSP file should be
     *                  registered
     * @param jspFile   The path, relative to the web application root, for the
     *                  JSP file to be used for this servlet
     *
     * @return  a {@link javax.servlet.ServletRegistration.Dynamic} object
     *          that can be used to further configure the servlet
     *
     * @since Servlet 4.0
     */
    public ServletRegistration.Dynamic addJspFile(String jspName, String jspFile);

    /**
     * Get the default character encoding for reading request bodies.
     *
     * @return The character encoding name or {@code null} if no default has
     *         been specified
     *
     * @throws UnsupportedOperationException    If called from a
     *    {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *    method of a {@link ServletContextListener} that was not defined in a
     *    web.xml file, a web-fragment.xml file nor annotated with
     *    {@link javax.servlet.annotation.WebListener}. For example, a
     *    {@link ServletContextListener} defined in a TLD would not be able to
     *    use this method.
     *
     * @since Servlet 4.0
     */
    public String getRequestCharacterEncoding();

    /**
     * Set the default character encoding to use for reading request bodies.
     * Calling this method will over-ride any value set in the deployment
     * descriptor.
     *
     * @param encoding The name of the character encoding to use
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
    public void setRequestCharacterEncoding(String encoding);

    /**
     * Get the default character encoding for writing response bodies.
     *
     * @return The character encoding name or {@code null} if no default has
     *         been specified
     *
     * @throws UnsupportedOperationException    If called from a
     *    {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *    method of a {@link ServletContextListener} that was not defined in a
     *    web.xml file, a web-fragment.xml file nor annotated with
     *    {@link javax.servlet.annotation.WebListener}. For example, a
     *    {@link ServletContextListener} defined in a TLD would not be able to
     *    use this method.
     *
     * @since Servlet 4.0
     */
    public String getResponseCharacterEncoding();

    /**
     * Set the default character encoding to use for writing response bodies.
     * Calling this method will over-ride any value set in the deployment
     * descriptor.
     *
     * @param encoding The name of the character encoding to use
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
    public void setResponseCharacterEncoding(String encoding);
}
