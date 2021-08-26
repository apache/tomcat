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
package javax.servlet.descriptor;

import java.util.Collection;

/**
 * This interface exposes the JSP specific configuration information obtain ed
 * from the deployment descriptors. It is primarily provided so that JSP
 * implementations do not have to parse deployment descriptors.
 *
 * @since Servlet 3.0
 */
public interface JspConfigDescriptor {

    /**
     * Provide the set of tag library descriptors obtained from the
     * &lt;jsp-config&gt; elements in the web application's deployment
     * descriptors.
     *
     * @return the tag library descriptors
     */
    public Collection<TaglibDescriptor> getTaglibs();

    /**
     * Provide the set of JSP property groups obtained from the
     * &lt;jsp-config&gt; elements in the web application's deployment
     * descriptors.
     *
     * @return the JSP property groups
     */
    public Collection<JspPropertyGroupDescriptor> getJspPropertyGroups();
}
