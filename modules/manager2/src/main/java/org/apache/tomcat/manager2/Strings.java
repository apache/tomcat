/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.tomcat.util.res.StringManager;


/**
 * Helper for the creation of the {@link StringManager} of this web application.
 * <p>
 * The {@link StringManager} cache is JVM wide and the first creation for a package wins. The class loaders of the first
 * thread that initializes one of the classes of this web application matter: the {@code LocalStrings} bundle is looked
 * up with the class loader of {@code StringManager} (the common class loader, which does not see the web application)
 * and, on failure, with the context class loader of that thread. A class may be initialized by a thread that is not a
 * request thread (for example the initialization of the filters of this web application during context start), in which
 * case the context class loader does not see the web application either and the {@link StringManager} would be created
 * without a bundle. To make the first creation deterministic, the context class loader is temporarily set to the class
 * loader of this web application.
 * <p>
 * Messages written to a response are localized with the locales sent by the client in its {@code Accept-Language}
 * header. {@link LocaleFilter} binds the {@link StringManager} matching those locales to the request thread;
 * {@link #sm()} returns it, falling back to the default {@link StringManager} outside of request processing (context
 * initialization, background collection).
 */
final class Strings {


    /**
     * The locales of the current request, set by {@link LocaleFilter}.
     */
    private static final ThreadLocal<RequestLocales> REQUEST_LOCALES = new ThreadLocal<>();


    /**
     * The keys of the base bundle, read lazily (see {@link #keys(String)}).
     */
    private static volatile Set<String> baseKeys;


    /**
     * The {@link StringManager} for the package of this web application, created in a way that always finds the
     * {@code LocalStrings} bundle of this web application.
     *
     * @return the {@link StringManager} for this web application
     */
    static StringManager manager() {
        return withWebappClassLoader(() -> StringManager.getManager(Constants.Package));
    }


    /**
     * The {@link StringManager} to use for messages of the current request: the manager matching the locales the
     * client sent, or the default one when there is no request bound to the thread or no bundle matches those locales.
     *
     * @return the {@link StringManager} for the current request or the default one
     */
    static StringManager sm() {
        RequestLocales requestLocales = REQUEST_LOCALES.get();
        if (requestLocales == null) {
            return manager();
        }
        if (requestLocales.resolved == null) {
            requestLocales.resolved = resolve(requestLocales.locales);
        }
        return requestLocales.resolved;
    }


    /**
     * The {@link StringManager} matching the locales sent by the client of the given request.
     *
     * @param request the current request
     *
     * @return the {@link StringManager} for the locales of the request
     */
    static StringManager manager(HttpServletRequest request) {
        List<Locale> locales = new ArrayList<>();
        Enumeration<Locale> requested = request.getLocales();
        while (requested.hasMoreElements()) {
            locales.add(requested.nextElement());
        }
        return resolve(locales);
    }


    /**
     * Bind the locales sent by the client to the current thread, for the duration of the request.
     *
     * @param request the current request
     */
    static void bindRequest(HttpServletRequest request) {
        REQUEST_LOCALES.set(new RequestLocales(request.getLocales()));
    }


    /**
     * Remove the request locales bound to the current thread.
     */
    static void unbindRequest() {
        REQUEST_LOCALES.remove();
    }


    /**
     * Resolve the best {@link StringManager} for the requested locales: the first manager whose bundle exactly
     * matches a requested locale, then the first match on the language alone (so that a request for a regional
     * variant uses a bundle that only carries the language), and finally the default manager.
     */
    private static StringManager resolve(List<Locale> requested) {
        List<Locale> locales = new ArrayList<>();
        for (Locale locale : requested) {
            if (locale != null && !locale.getLanguage().isEmpty()) {
                locales.add(locale);
            }
        }
        for (Locale locale : locales) {
            StringManager manager = manager(locale);
            if (manager.getLocale().equals(locale)) {
                return manager;
            }
        }
        for (Locale locale : locales) {
            Locale language = new Locale.Builder().setLanguage(locale.getLanguage()).build();
            StringManager manager = manager(language);
            if (manager.getLocale().getLanguage().equals(locale.getLanguage())) {
                return manager;
            }
        }
        return manager();
    }


    /**
     * The keys of the base {@code LocalStrings} bundle of this web application that start with the given prefix.
     *
     * @param prefix the key prefix, or {@code null} for all keys
     *
     * @return the matching keys
     */
    static Set<String> keys(String prefix) {
        Set<String> all = baseKeys;
        if (all == null) {
            ResourceBundle bundle = ResourceBundle.getBundle(Constants.Package + ".LocalStrings", Locale.ROOT,
                    Strings.class.getClassLoader());
            all = Set.copyOf(bundle.keySet());
            baseKeys = all;
        }
        if (prefix == null) {
            return all;
        }
        Set<String> result = new HashSet<>();
        for (String key : all) {
            if (key.startsWith(prefix)) {
                result.add(key);
            }
        }
        return result;
    }


    private static StringManager manager(Locale locale) {
        return withWebappClassLoader(() -> StringManager.getManager(Constants.Package, locale));
    }


    private static StringManager withWebappClassLoader(java.util.function.Supplier<StringManager> creation) {
        Thread thread = Thread.currentThread();
        ClassLoader own = Strings.class.getClassLoader();
        ClassLoader previous = thread.getContextClassLoader();
        if (previous == own) {
            return creation.get();
        }
        thread.setContextClassLoader(own);
        try {
            return creation.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }


    /**
     * The locales of a request and the lazily resolved manager for them.
     */
    private static final class RequestLocales {

        private final List<Locale> locales;
        private StringManager resolved;


        private RequestLocales(Enumeration<Locale> requested) {
            this.locales = new ArrayList<>();
            if (requested != null) {
                while (requested.hasMoreElements()) {
                    locales.add(requested.nextElement());
                }
            }
        }
    }


    private Strings() {
        // Utility class, do not instantiate
    }
}
