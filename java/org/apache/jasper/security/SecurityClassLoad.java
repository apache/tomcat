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

package org.apache.jasper.security;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Static class used to preload java classes when using the Java SecurityManager
 * so that the defineClassInPackage RuntimePermission does not trigger an
 * AccessControlException.
 */

public final class SecurityClassLoad {

    public static void securityClassLoad(ClassLoader loader) {

        if (System.getSecurityManager() == null) {
            return;
        }

        final String basePackage = "org.apache.jasper.";
        try {
            // Ensure XMLInputFactory is loaded with Tomcat's class loader
            loader.loadClass(basePackage + "compiler.EncodingDetector");

            loader.loadClass(basePackage + "runtime.JspFactoryImpl$PrivilegedGetPageContext");
            loader.loadClass(basePackage + "runtime.JspFactoryImpl$PrivilegedReleasePageContext");

            loader.loadClass(basePackage + "runtime.JspRuntimeLibrary");

            loader.loadClass(basePackage + "runtime.ServletResponseWrapperInclude");
            loader.loadClass(basePackage + "runtime.TagHandlerPool");
            loader.loadClass(basePackage + "runtime.JspFragmentHelper");

            loader.loadClass(basePackage + "runtime.ProtectedFunctionMapper");

            loader.loadClass(basePackage + "runtime.PageContextImpl");
            loadAnonymousInnerClasses(loader, basePackage + "runtime.PageContextImpl");

            loader.loadClass(basePackage + "runtime.JspContextWrapper");

            // Trigger loading of class and reading of property
            SecurityUtil.isPackageProtectionEnabled();

            loader.loadClass(basePackage + "servlet.JspServletWrapper");

            loadAnonymousInnerClasses(loader, "runtime.JspWriterImpl");
        } catch (ClassNotFoundException ex) {
            Log log = LogFactory.getLog(SecurityClassLoad.class);
            log.error("SecurityClassLoad", ex);
        }
    }

    private static final void loadAnonymousInnerClasses(ClassLoader loader, String enclosingClass) {
        try {
            for (int i = 1;; i++) {
                loader.loadClass(enclosingClass + '$' + i);
            }
        } catch (ClassNotFoundException ignored) {
            //
        }
    }
}
