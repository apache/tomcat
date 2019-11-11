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
package org.apache.catalina.servlet4preview.http;

import javax.servlet.annotation.WebServlet;

/**
 * Represents how the request from which this object was obtained was mapped to
 * the associated servlet.
 *
 * @since 4.0
 */
public interface HttpServletMapping {

    /**
     * @return The value that was matched or the empty String if not known.
     */
    String getMatchValue();

    /**
     * @return The {@code url-pattern} that matched this request or the empty
     *         String if not known.
     */
    String getPattern();

    /**
     * @return The name of the servlet (as specified in web.xml,
     *         {@link WebServlet#name()},
     *         {@link javax.servlet.ServletContext#addServlet(String, Class)} or
     *         one of the other <code>addServlet()</code> methods) that the
     *         request was mapped to.
     */
    String getServletName();

    /**
     * @return The type of match ({@code null} if not known)
     */
    MappingMatch getMappingMatch();
}
