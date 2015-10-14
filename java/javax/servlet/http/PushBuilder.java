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
package javax.servlet.http;

/**
 * Builds a push request based on the {@link HttpServletRequest} from which this
 * builder was obtained. The push request will be constructed on the following
 * basis:
 * <ul>
 * <li>The request method is set to <code>GET</code></li>
 * <li>The path will not be set. This must be set explicitly via a call to
 *     {@link #setPath(String)}</li>
 * </ul>
 *
 * @since Servlet 4.0
 */
public interface PushBuilder {

    /**
     * Sets the URI path to be used for the push request. This must be called
     * before every call to {@link #push()}. If the path includes a query
     * string, the query string will be appended to the existing query string
     * (if any) and no de-duplication will occur.
     *
     * @param path Paths beginning with '/' are treated as absolute paths. All
     *             other paths are treated as relative to the context path of
     *             the request used to create this builder instance. The path
     *             may include a query string.
     *
     * @return This builder instance
     */
    PushBuilder setPath(String path);

    void push();
}
