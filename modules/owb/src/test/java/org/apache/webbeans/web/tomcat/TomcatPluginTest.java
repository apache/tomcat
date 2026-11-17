/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.exception.WebBeansConfigurationException;
import org.apache.webbeans.spi.SecurityService;
import org.junit.jupiter.api.Test;

class TomcatPluginTest {

    @Test
    void securityServiceRegistration() {
        TomcatPlugin plugin = new TomcatPlugin();

        assertTrue(plugin.supportService(SecurityService.class));
        assertFalse(plugin.supportService(BeanManager.class));

        SecurityService service = plugin.getSupportedService(SecurityService.class);
        assertNotNull(service);
        assertSame(WebBeansContext.getInstance().getSecurityService(), service);
        assertNull(plugin.getSupportedService(BeanManager.class));
    }

    @Test
    void servletSpecClassesAreRejectedAsManagedBeans() {
        TomcatPlugin plugin = new TomcatPlugin();

        assertThrows(WebBeansConfigurationException.class, () -> plugin.isManagedBean(TestServlet.class));
        assertThrows(WebBeansConfigurationException.class, () -> plugin.isManagedBean(TestFilter.class));
        assertThrows(WebBeansConfigurationException.class,
                () -> plugin.isManagedBean(TestServletContextListener.class));
        assertThrows(WebBeansConfigurationException.class, () -> plugin.isManagedBean(TestSessionListener.class));
        assertThrows(WebBeansConfigurationException.class,
                () -> plugin.isManagedBean(TestServletRequestListener.class));
    }

    @Test
    void nonServletClassesAreManagedBeans() {
        TomcatPlugin plugin = new TomcatPlugin();

        assertDoesNotThrow(() -> plugin.isManagedBean(PlainBean.class));
        assertDoesNotThrow(() -> plugin.isManagedBean(Object.class));
    }

    @Test
    void javaEeComponentInjectionSupport() {
        TomcatPlugin plugin = new TomcatPlugin();

        assertTrue(plugin.supportsJavaEeComponentInjections(TestServlet.class));
        assertTrue(plugin.supportsJavaEeComponentInjections(TestFilter.class));
        assertTrue(plugin.supportsJavaEeComponentInjections(TestServletContextListener.class));
        assertTrue(plugin.supportsJavaEeComponentInjections(TestSessionListener.class));
        assertTrue(plugin.supportsJavaEeComponentInjections(TestServletRequestListener.class));
        assertFalse(plugin.supportsJavaEeComponentInjections(PlainBean.class));
        assertFalse(plugin.supportsJavaEeComponentInjections(Object.class));
    }

    static class TestServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
    }

    static class TestFilter implements Filter {
        @Override
        public void init(FilterConfig filterConfig) {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    }

    static class TestServletContextListener implements ServletContextListener {
        @Override
        public void contextInitialized(ServletContextEvent event) {
        }

        @Override
        public void contextDestroyed(ServletContextEvent event) {
        }
    }

    static class TestSessionListener implements HttpSessionListener {
        @Override
        public void sessionCreated(HttpSessionEvent event) {
        }

        @Override
        public void sessionDestroyed(HttpSessionEvent event) {
        }
    }

    static class TestServletRequestListener implements ServletRequestListener {
        @Override
        public void requestInitialized(ServletRequestEvent event) {
        }

        @Override
        public void requestDestroyed(ServletRequestEvent event) {
        }
    }

    static class PlainBean {
    }
}
