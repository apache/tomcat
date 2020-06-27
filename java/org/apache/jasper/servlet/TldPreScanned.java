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
package org.apache.jasper.servlet;

import java.net.URL;
import java.util.Collection;

import jakarta.servlet.ServletContext;

import org.apache.jasper.compiler.Localizer;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;

public class TldPreScanned extends TldScanner {

    private final Collection<URL> preScannedURLs;

    public TldPreScanned (ServletContext context, boolean namespaceAware, boolean validation,
            boolean blockExternal, Collection<URL> preScannedTlds) {
        super(context, namespaceAware, validation, blockExternal);
        preScannedURLs = preScannedTlds;
    }

    @Override
    public void scanJars() {
        for (URL url : preScannedURLs){
            String str = url.toExternalForm();
            int a = str.indexOf("jar:");
            int b = str.indexOf("!/");
            if (a >= 0 && b> 0) {
                String fileUrl = str.substring(a + 4, b);
                String path = str.substring(b + 2);
                try {
                    parseTld(new TldResourcePath(new URL(fileUrl), null, path));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            } else {
                throw new IllegalStateException(Localizer.getMessage("jsp.error.tld.url", str));
            }
        }
    }
}