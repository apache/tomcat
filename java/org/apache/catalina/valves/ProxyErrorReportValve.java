/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.valves;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.IOTools;
import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * Implementation of a Valve that proxies or redirects error reporting to other urls.
 * </p>
 * <p>
 * This Valve should be attached at the Host level, although it will work if attached to a Context.
 * </p>
 */
public class ProxyErrorReportValve extends ErrorReportValve {
    private static final Log log = LogFactory.getLog(ProxyErrorReportValve.class);

    /**
     * Use a redirect or proxy the response to the specified location. Default to redirect.
     */
    protected boolean useRedirect = true;

    /**
     * @return the useRedirect
     */
    public boolean getUseRedirect() {
        return this.useRedirect;
    }

    /**
     * @param useRedirect the useRedirect to set
     */
    public void setUseRedirect(boolean useRedirect) {
        this.useRedirect = useRedirect;
    }

    /**
     * Use a properties file for the URLs.
     */
    protected boolean usePropertiesFile = false;

    /**
     * @return the usePropertiesFile
     */
    public boolean getUsePropertiesFile() {
        return this.usePropertiesFile;
    }

    /**
     * @param usePropertiesFile the usePropertiesFile to set
     */
    public void setUsePropertiesFile(boolean usePropertiesFile) {
        this.usePropertiesFile = usePropertiesFile;
    }

    private String getRedirectUrl(Response response) {
        ResourceBundle resourceBundle = ResourceBundle.getBundle(this.getClass().getSimpleName(), response.getLocale());
        String redirectUrl = null;
        try {
            redirectUrl = resourceBundle.getString(Integer.toString(response.getStatus()));
        } catch (MissingResourceException e) {
            // Ignore
        }
        if (redirectUrl == null) {
            try {
                redirectUrl = resourceBundle.getString(Integer.toString(0));
            } catch (MissingResourceException ex) {
                // Ignore
            }
        }
        return redirectUrl;
    }

    @Override
    protected void report(Request request, Response response, Throwable throwable) {

        int statusCode = response.getStatus();

        // Do nothing on a 1xx, 2xx and 3xx status
        // Do nothing if anything has been written already
        // Do nothing if the response hasn't been explicitly marked as in error
        // and that error has not been reported.
        if (statusCode < 400 || response.getContentWritten() > 0) {
            return;
        }

        // If an error has occurred that prevents further I/O, don't waste time
        // producing an error report that will never be read
        AtomicBoolean result = new AtomicBoolean(false);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, result);
        if (!result.get()) {
            return;
        }

        String urlString = null;
        if (usePropertiesFile) {
            urlString = getRedirectUrl(response);
        } else {
            ErrorPage errorPage = findErrorPage(statusCode, throwable);
            if (errorPage != null) {
                urlString = errorPage.getLocation();
            }
        }
        if (urlString == null) {
            super.report(request, response, throwable);
            return;
        }

        // No need to delegate anymore
        if (!response.setErrorReported()) {
            return;
        }

        StringBuilder stringBuilder = new StringBuilder(urlString);
        if (urlString.indexOf("?") > -1) {
            stringBuilder.append('&');
        } else {
            stringBuilder.append('?');
        }
        stringBuilder.append("requestUri=");
        stringBuilder.append(URLEncoder.encode(request.getDecodedRequestURI(), request.getConnector().getURICharset()));
        stringBuilder.append("&statusCode=");
        stringBuilder.append(URLEncoder.encode(String.valueOf(statusCode), StandardCharsets.UTF_8));

        String reason = null;
        String description = null;
        StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());
        response.setLocale(smClient.getLocale());
        try {
            reason = smClient.getString("http." + statusCode + ".reason");
            description = smClient.getString("http." + statusCode + ".desc");
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }
        if (reason == null || description == null) {
            reason = smClient.getString("errorReportValve.unknownReason");
            description = smClient.getString("errorReportValve.noDescription");
        }
        stringBuilder.append("&statusDescription=");
        stringBuilder.append(URLEncoder.encode(description, StandardCharsets.UTF_8));
        stringBuilder.append("&statusReason=");
        stringBuilder.append(URLEncoder.encode(reason, StandardCharsets.UTF_8));

        String message = response.getMessage();
        if (message != null) {
            stringBuilder.append("&message=");
            stringBuilder.append(URLEncoder.encode(message, StandardCharsets.UTF_8));
        }
        if (throwable != null) {
            stringBuilder.append("&throwable=");
            stringBuilder.append(URLEncoder.encode(throwable.toString(), StandardCharsets.UTF_8));
        }

        urlString = stringBuilder.toString();
        if (useRedirect) {
            if (log.isTraceEnabled()) {
                log.trace("Redirecting error reporting to " + urlString);
            }
            try {
                response.sendRedirect(urlString);
            } catch (IOException e) {
                // Ignore
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("Proxying error reporting to " + urlString);
            }
            HttpURLConnection httpURLConnection = null;
            try {
                URL url = (new URI(urlString)).toURL();
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.connect();
                response.setContentType(httpURLConnection.getContentType());
                response.setContentLength(httpURLConnection.getContentLength());
                OutputStream outputStream = response.getOutputStream();
                InputStream inputStream = url.openStream();
                IOTools.flow(inputStream, outputStream);
            } catch (URISyntaxException | IOException | IllegalArgumentException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("proxyErrorReportValve.error", urlString), e);
                }
                // Ignore
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }
        }
    }
}

