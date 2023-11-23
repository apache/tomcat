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
package jakarta.servlet.jsp;

/**
 * Contains information about an error, for error pages. The information contained in this instance is meaningless if
 * not used in the context of an error page. To indicate a JSP is an error page, the page author must set the
 * isErrorPage attribute of the page directive to "true".
 *
 * @see PageContext#getErrorData
 *
 * @since JSP 2.0
 */
public final class ErrorData {

    private final Throwable throwable;
    private final int statusCode;
    private final String method;
    private final String uri;
    private final String servletName;
    private final String queryString;

    /**
     * Creates a new ErrorData object.
     *
     * @param throwable   The Throwable that is the cause of the error
     * @param statusCode  The status code of the error
     * @param uri         The request URI
     * @param servletName The name of the servlet invoked
     *
     * @deprecated Use {#link {@link ErrorData#ErrorData(Throwable, int, String, String, String, String)}
     */
    @Deprecated(since = "4.0", forRemoval = true)
    public ErrorData(Throwable throwable, int statusCode, String uri, String servletName) {
        this(throwable, statusCode, null, uri, servletName, null);
    }

    /**
     * Creates a new ErrorData object.
     *
     * @param throwable   The Throwable that is the cause of the error
     * @param statusCode  The status code of the error
     * @param method      The request method
     * @param uri         The request URI
     * @param servletName The name of the servlet invoked
     * @param queryString The request query string
     *
     * @since JSP 4.0
     */
    public ErrorData(Throwable throwable, int statusCode, String method, String uri, String servletName,
            String queryString) {
        this.throwable = throwable;
        this.statusCode = statusCode;
        this.method = method;
        this.uri = uri;
        this.servletName = servletName;
        this.queryString = queryString;
    }

    /**
     * Returns the Throwable that caused the error.
     *
     * @return The Throwable that caused the error
     */
    public Throwable getThrowable() {
        return this.throwable;
    }

    /**
     * Returns the status code of the error.
     *
     * @return The status code of the error
     */
    public int getStatusCode() {
        return this.statusCode;
    }

    /**
     * Returns the request method.
     *
     * @return The request method
     */
    public String getMethod() {
        return this.method;
    }

    /**
     * Returns the request URI.
     *
     * @return The request URI
     */
    public String getRequestURI() {
        return this.uri;
    }

    /**
     * Returns the name of the servlet invoked.
     *
     * @return The name of the servlet invoked
     */
    public String getServletName() {
        return this.servletName;
    }

    /**
     * Returns the request query string or {@code null} if the request had no query string.
     *
     * @return The request query string
     *
     * @since JSP 4.0
     */
    public String getQueryString() {
        return this.queryString;
    }
}
