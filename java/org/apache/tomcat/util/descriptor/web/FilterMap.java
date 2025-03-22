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

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.DispatcherType;

import org.apache.tomcat.util.buf.UDecoder;

/**
 * Representation of a filter mapping for a web application, as represented
 * in a <code>&lt;filter-mapping&gt;</code> element in the deployment
 * descriptor.  Each filter mapping must contain a filter name plus either
 * a URL pattern or a servlet name.
 *
 * @author Craig R. McClanahan
 */
public class FilterMap extends XmlEncodingBase implements Serializable {


    // ------------------------------------------------------------- Properties


    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The name of this filter to be executed when this mapping matches
     * a particular request.
     */

    public static final int ERROR = 1;
    public static final int FORWARD = 2;
    public static final int INCLUDE = 4;
    public static final int REQUEST = 8;
    public static final int ASYNC = 16;

    // represents nothing having been set. This will be seen
    // as equal to a REQUEST
    private static final int NOT_SET = 0;

    private int dispatcherMapping = NOT_SET;

    private String filterName = null;

    public String getFilterName() {
        return this.filterName;
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }


    /**
     * The servlet name this mapping matches.
     */
    private String[] servletNames = new String[0];

    public String[] getServletNames() {
        if (matchAllServletNames) {
            return new String[] {};
        } else {
            return this.servletNames;
        }
    }

    public void addServletName(String servletName) {
        if ("*".equals(servletName)) {
            this.matchAllServletNames = true;
        } else {
            String[] results = new String[servletNames.length + 1];
            System.arraycopy(servletNames, 0, results, 0, servletNames.length);
            results[servletNames.length] = servletName;
            servletNames = results;
        }
    }


    /**
     * The flag that indicates this mapping will match all url-patterns
     */
    private boolean matchAllUrlPatterns = false;

    public boolean getMatchAllUrlPatterns() {
        return matchAllUrlPatterns;
    }


    /**
     * The flag that indicates this mapping will match all servlet-names
     */
    private boolean matchAllServletNames = false;

    public boolean getMatchAllServletNames() {
        return matchAllServletNames;
    }


    /**
     * The URL pattern this mapping matches.
     */
    private String[] urlPatterns = new String[0];

    public String[] getURLPatterns() {
        if (matchAllUrlPatterns) {
            return new String[] {};
        } else {
            return this.urlPatterns;
        }
    }

    public void addURLPattern(String urlPattern) {
        addURLPatternDecoded(UDecoder.URLDecode(urlPattern, getCharset()));
    }
    public void addURLPatternDecoded(String urlPattern) {
        if ("*".equals(urlPattern)) {
            this.matchAllUrlPatterns = true;
        } else {
            String[] results = new String[urlPatterns.length + 1];
            System.arraycopy(urlPatterns, 0, results, 0, urlPatterns.length);
            results[urlPatterns.length] = UDecoder.URLDecode(urlPattern, getCharset());
            urlPatterns = results;
        }
    }

    /**
     * This method will be used to set the current state of the FilterMap
     * representing the state of when filters should be applied.
     * @param dispatcherString the dispatcher type which should
     *  match this filter
     */
    public void setDispatcher(String dispatcherString) {
        String dispatcher = dispatcherString.toUpperCase(Locale.ENGLISH);

        if (dispatcher.equals(DispatcherType.FORWARD.name())) {
            // apply FORWARD to the global dispatcherMapping.
            dispatcherMapping |= FORWARD;
        } else if (dispatcher.equals(DispatcherType.INCLUDE.name())) {
            // apply INCLUDE to the global dispatcherMapping.
            dispatcherMapping |= INCLUDE;
        } else if (dispatcher.equals(DispatcherType.REQUEST.name())) {
            // apply REQUEST to the global dispatcherMapping.
            dispatcherMapping |= REQUEST;
        }  else if (dispatcher.equals(DispatcherType.ERROR.name())) {
            // apply ERROR to the global dispatcherMapping.
            dispatcherMapping |= ERROR;
        }  else if (dispatcher.equals(DispatcherType.ASYNC.name())) {
            // apply ERROR to the global dispatcherMapping.
            dispatcherMapping |= ASYNC;
        }
    }

    public int getDispatcherMapping() {
        // per the SRV.6.2.5 absence of any dispatcher elements is
        // equivalent to a REQUEST value
        if (dispatcherMapping == NOT_SET) {
            return REQUEST;
        }

        return dispatcherMapping;
    }

    public String[] getDispatcherNames() {
        List<String> result = new ArrayList<>();
        if ((dispatcherMapping & FORWARD) != 0) {
            result.add(DispatcherType.FORWARD.name());
        }
        if ((dispatcherMapping & INCLUDE) != 0) {
            result.add(DispatcherType.INCLUDE.name());
        }
        if ((dispatcherMapping & REQUEST) != 0) {
            result.add(DispatcherType.REQUEST.name());
        }
        if ((dispatcherMapping & ERROR) != 0) {
            result.add(DispatcherType.ERROR.name());
        }
        if ((dispatcherMapping & ASYNC) != 0) {
            result.add(DispatcherType.ASYNC.name());
        }
        return result.toArray(new String[0]);
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Render a String representation of this object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FilterMap[");
        sb.append("filterName=");
        sb.append(this.filterName);
        for (String servletName : servletNames) {
            sb.append(", servletName=");
            sb.append(servletName);
        }
        for (String urlPattern : urlPatterns) {
            sb.append(", urlPattern=");
            sb.append(urlPattern);
        }
        sb.append(']');
        return sb.toString();
    }


}
