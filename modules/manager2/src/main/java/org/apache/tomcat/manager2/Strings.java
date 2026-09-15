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
 */
final class Strings {


    /**
     * The {@link StringManager} for the package of this web application, created in a way that always finds the
     * {@code LocalStrings} bundle of this web application.
     *
     * @return the {@link StringManager} for this web application
     */
    static StringManager manager() {
        Thread thread = Thread.currentThread();
        ClassLoader own = Strings.class.getClassLoader();
        ClassLoader previous = thread.getContextClassLoader();
        if (previous == own) {
            return StringManager.getManager(Constants.Package);
        }
        thread.setContextClassLoader(own);
        try {
            return StringManager.getManager(Constants.Package);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }


    private Strings() {
        // Utility class, do not instantiate
    }
}
