/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.web.tomcat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.security.auth.Subject;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.security.SimpleSecurityService;

public class TomcatSecurityService extends SimpleSecurityService {
    private final boolean useWrapper;
    private final Principal proxy;

    public TomcatSecurityService(final WebBeansContext context) {
        useWrapper = "true".equalsIgnoreCase(context.getOpenWebBeansConfiguration()
                .getProperty("org.apache.webbeans.component.PrincipalBean.proxy", "true"));
        final ClassLoader loader = SimpleSecurityService.class.getClassLoader();
        final Class<?>[] apiToProxy = Stream.concat(
                Stream.of(Principal.class),
                Stream.of(context.getOpenWebBeansConfiguration()
                        .getProperty("org.apache.webbeans.component.PrincipalBean.proxyApis", "org.eclipse.microprofile.jwt.JsonWebToken").split(","))
                        .map(String::trim)
                        .filter(it -> !it.isEmpty())
                        .map(it -> {
                            try { // if MP JWT-Auth is available
                                return loader.loadClass(it.trim());
                            } catch (final NoClassDefFoundError | ClassNotFoundException e) {
                                return null;
                            }
                        })).filter(Objects::nonNull).toArray(Class[]::new);
        proxy = apiToProxy.length == 1 ? new TomcatSecurityServicePrincipal() : Principal.class.cast(
                Proxy.newProxyInstance(loader, apiToProxy, (proxy, method, args) -> {
                    try {
                        return method.invoke(getCurrentPrincipal(), args);
                    } catch (final InvocationTargetException ite) {
                        throw ite.getTargetException();
                    }
                }));

    }

    @Override // reason of that class
    public Principal getCurrentPrincipal() {
        return useWrapper ? proxy : getUserPrincipal();
    }

    // ensure it is contextual
    private static class TomcatSecurityServicePrincipal implements Principal {
        @Override
        public String getName() {
            return unwrap().getName();
        }

        @Override
        public boolean implies(final Subject subject) {
            return unwrap().implies(subject);
        }

        private Principal unwrap() {
            return getUserPrincipal();
        }
    }

    @SuppressWarnings("unchecked")
    private static Principal getUserPrincipal() {
        final BeanManager beanManager = CDI.current().getBeanManager();
        final HttpServletRequest request = HttpServletRequest.class.cast(
                beanManager.getReference(
                        beanManager.resolve(beanManager.getBeans(HttpServletRequest.class)), HttpServletRequest.class,
                        beanManager.createCreationalContext(null)));
        final Object supplier = request.getAttribute(Principal.class.getName() + ".supplier");
        if (supplier != null) {
            return ((Supplier<Principal>) supplier).get();
        }
        return request.getUserPrincipal();
    }
}
