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
package org.apache.jasper.compiler;

import java.util.Hashtable;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.jasper.JasperException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;


/**
 * A container for all tag libraries that are defined "globally"
 * for the web application.
 * <p/>
 * Tag Libraries can be defined globally in one of two ways:
 * 1. Via <taglib> elements in web.xml:
 * the uri and location of the tag-library are specified in
 * the <taglib> element.
 * 2. Via packaged jar files that contain .tld files
 * within the META-INF directory, or some subdirectory
 * of it. The taglib is 'global' if it has the <uri>
 * element defined.
 * <p/>
 * A mapping between the taglib URI and its associated TaglibraryInfoImpl
 * is maintained in this container.
 * Actually, that's what we'd like to do. However, because of the
 * way the classes TagLibraryInfo and TagInfo have been defined,
 * it is not currently possible to share an instance of TagLibraryInfo
 * across page invocations. A bug has been submitted to the spec lead.
 * In the mean time, all we do is save the 'location' where the
 * TLD associated with a taglib URI can be found.
 * <p/>
 * When a JSP page has a taglib directive, the mappings in this container
 * are first searched (see method getLocation()).
 * If a mapping is found, then the location of the TLD is returned.
 * If no mapping is found, then the uri specified
 * in the taglib directive is to be interpreted as the location for
 * the TLD of this tag library.
 *
 * @author Pierre Delisle
 * @author Jan Luehe
 */
@Deprecated
public class TldLocationsCache {

    public static final String KEY = TldLocationsCache.class.getName();

    private static final Log log = LogFactory.getLog(TldLocationsCache.class);
    private final Hashtable<String, TldLocation> mappings;

    public TldLocationsCache(Map<String, TldResourcePath> taglibMap) {
        mappings = new Hashtable<>(taglibMap.size());
        for (Map.Entry<String, TldResourcePath> entry : taglibMap.entrySet()) {
            String uri = entry.getKey();
            TldResourcePath tldResourcePath = entry.getValue();
            String url = tldResourcePath.getUrl().toExternalForm();
            String entryName = tldResourcePath.getEntryName();
            TldLocation tldLocation;
            if (entryName == null) {
                tldLocation = new TldLocation(url);
            } else {
                tldLocation = new TldLocation(entryName, url);
            }
            mappings.put(uri, tldLocation);
        }
    }

    /**
     * Obtains the TLD location cache for the given {@link ServletContext}.
     */
    public static synchronized TldLocationsCache getInstance(ServletContext ctxt) {
        if (ctxt == null) {
            throw new IllegalArgumentException("ServletContext was null");
        }
        return (TldLocationsCache) ctxt.getAttribute(KEY);
    }

    /**
     * Gets the 'location' of the TLD associated with the given taglib 'uri'.
     * <p/>
     * Returns null if the uri is not associated with any tag library 'exposed'
     * in the web application. A tag library is 'exposed' either explicitly in
     * web.xml or implicitly via the uri tag in the TLD of a taglib deployed
     * in a jar file (WEB-INF/lib).
     *
     * @param uri The taglib uri
     * @return the TldLocation for this uri
     */
    public TldLocation getLocation(String uri) throws JasperException {
        return mappings.get(uri);
    }
}
