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
package org.apache.tomcat.util.descriptor.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.scan.Jar;
import org.apache.tomcat.util.scan.JarFactory;
import org.xml.sax.InputSource;

/**
* Callback handling a web-fragment.xml descriptor.
*/
public class FragmentJarScannerCallback implements JarScannerCallback {

    private static final String FRAGMENT_LOCATION =
        "META-INF/web-fragment.xml";
    private final WebXmlParser webXmlParser;
    private final boolean delegate;
    private final boolean parseRequired;
    private final Map<String,WebXml> fragments = new HashMap<>();
    private boolean ok  = true;

    public FragmentJarScannerCallback(WebXmlParser webXmlParser, boolean delegate,
            boolean parseRequired) {
        this.webXmlParser = webXmlParser;
        this.delegate = delegate;
        this.parseRequired = parseRequired;
    }

    @Override
    public void scan(JarURLConnection jarConn, String webappPath, boolean isWebapp)
            throws IOException {

        URL url = jarConn.getURL();
        URL resourceURL = jarConn.getJarFileURL();
        Jar jar = null;
        InputStream is = null;
        WebXml fragment = new WebXml();
        fragment.setWebappJar(isWebapp);
        fragment.setDelegate(delegate);

        try {
            // Only web application JARs are checked for web-fragment.xml
            // files.
            // web-fragment.xml files don't need to be parsed if they are never
            // going to be used.
            if (isWebapp && parseRequired) {
                jar = JarFactory.newInstance(url);
                is = jar.getInputStream(FRAGMENT_LOCATION);
            }

            if (is == null) {
                // If there is no web.xml, normal JAR no impact on
                // distributable
                fragment.setDistributable(true);
            } else {
                InputSource source = new InputSource(
                        "jar:" + resourceURL.toString() + "!/" + FRAGMENT_LOCATION);
                source.setByteStream(is);
                if (!webXmlParser.parseWebXml(source, fragment, true)) {
                    ok = false;
                }
            }
        } finally {
            if (jar != null) {
                jar.close();
            }
            fragment.setURL(url);
            if (fragment.getName() == null) {
                fragment.setName(fragment.getURL().toString());
            }
            fragment.setJarName(extractJarFileName(url));
            fragments.put(fragment.getName(), fragment);
        }
    }

    private String extractJarFileName(URL input) {
        String url = input.toString();
        if (url.endsWith("!/")) {
            // Remove it
            url = url.substring(0, url.length() - 2);
        }

        // File name will now be whatever is after the final /
        return url.substring(url.lastIndexOf('/') + 1);
    }

    @Override
    public void scan(File file, String webappPath, boolean isWebapp) throws IOException {

        InputStream stream = null;
        WebXml fragment = new WebXml();
        fragment.setWebappJar(isWebapp);
        fragment.setDelegate(delegate);

        try {
            File fragmentFile = new File(file, FRAGMENT_LOCATION);
            if (fragmentFile.isFile()) {
                stream = new FileInputStream(fragmentFile);
                InputSource source =
                    new InputSource(fragmentFile.toURI().toURL().toString());
                source.setByteStream(stream);
                if (!webXmlParser.parseWebXml(source, fragment, true)) {
                    ok = false;
                }
            } else {
                // If there is no web.xml, normal folder no impact on
                // distributable
                fragment.setDistributable(true);
            }
        } finally {
            fragment.setURL(file.toURI().toURL());
            if (fragment.getName() == null) {
                fragment.setName(fragment.getURL().toString());
            }
            fragment.setJarName(file.getName());
            fragments.put(fragment.getName(), fragment);
        }
    }


    @Override
    public void scanWebInfClasses() {
        // NO-OP. Fragments unpacked in WEB-INF classes are not handled,
        // mainly because if there are multiple fragments there is no way to
        // handle multiple web-fragment.xml files.
    }

    public boolean isOk() {
        return ok;
    }

    public Map<String,WebXml> getFragments() {
        return fragments;
    }
}
