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
import java.util.Locale;
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

    /*
     * Timeouts for the connection used when proxying an error report. These bound the time the request thread can be
     * blocked by an unresponsive error report target.
     */
    private static final int PROXY_CONNECT_TIMEOUT_MS = 5000;
    private static final int PROXY_READ_TIMEOUT_MS = 10000;

    /*
     * Control used to resolve the candidate locales of the optional properties file bundle. The default control is the
     * one getBundle() uses, so the candidate chain matches the one getBundle() would walk.
     */
    private static final ResourceBundle.Control BUNDLE_CONTROL =
            ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    /**
     * Default constructor.
     */
    public ProxyErrorReportValve() {
        super();
    }

    /**
     * Use a redirect or proxy the response to the specified location. Default to not redirect.
     */
    protected boolean useRedirect = false;

    /**
     * Returns whether a redirect is used to send the error report to the specified location.
     *
     * @return {@code true} if a redirect is used, {@code false} if a proxy is used
     */
    public boolean getUseRedirect() {
        return this.useRedirect;
    }

    /**
     * Sets whether to use a redirect or proxy for the error report.
     *
     * @param useRedirect {@code true} to use a redirect, {@code false} to use a proxy
     */
    public void setUseRedirect(boolean useRedirect) {
        this.useRedirect = useRedirect;
    }

    /**
     * Use a properties file for the URLs.
     */
    protected boolean usePropertiesFile = false;

    /**
     * Returns whether a properties file is used to determine the redirect URLs.
     *
     * @return {@code true} if a properties file is used, {@code false} otherwise
     */
    public boolean getUsePropertiesFile() {
        return this.usePropertiesFile;
    }

    /**
     * Sets whether to use a properties file for the redirect URLs.
     *
     * @param usePropertiesFile {@code true} to use a properties file, {@code false} otherwise
     */
    public void setUsePropertiesFile(boolean usePropertiesFile) {
        this.usePropertiesFile = usePropertiesFile;
    }

    private String getRedirectUrl(Response response) {
        String bundleName = this.getClass().getSimpleName();
        // Resolve the bundle for the first candidate locale for which a bundle exists.
        Locale bundleLocale = null;
        for (Locale candidate : BUNDLE_CONTROL.getCandidateLocales(bundleName, response.getLocale())) {
            if (StringManager.bundleExists(bundleName, candidate)) {
                bundleLocale = candidate;
                break;
            }
        }
        if (bundleLocale == null) {
            // No bundle available. Fall back to the default error report.
            return null;
        }
        ResourceBundle resourceBundle;
        try {
            resourceBundle = ResourceBundle.getBundle(bundleName, bundleLocale);
        } catch (MissingResourceException e) {
            // No bundle available. Fall back to the default error report.
            return null;
        }
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

    /**
     * Reports the error by redirecting or proxying to the configured error URL.
     *
     * @param request   the servlet request
     * @param response  the servlet response
     * @param throwable the exception that caused the error, or {@code null}
     */
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

        // If the error has already been reported, there is nothing more to do.
        // Note: the reported flag is deliberately not consumed here. A failed
        // proxy attempt must still be able to fall back to super.report(),
        // which consumes the flag itself.
        if (!response.isErrorReportRequired()) {
            return;
        }

        StringBuilder stringBuilder = new StringBuilder(urlString);
        if (urlString.contains("?")) {
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
        stringBuilder.append("&statusReason=");
        stringBuilder.append(URLEncoder.encode(reason, StandardCharsets.UTF_8));

        if (isShowReport()) {
            stringBuilder.append("&statusDescription=");
            stringBuilder.append(URLEncoder.encode(description, StandardCharsets.UTF_8));

            String message = response.getMessage();
            if (message != null) {
                stringBuilder.append("&message=");
                stringBuilder.append(URLEncoder.encode(message, StandardCharsets.UTF_8));
            }
            if (throwable != null) {
                stringBuilder.append("&throwable=");
                stringBuilder.append(URLEncoder.encode(throwable.toString(), StandardCharsets.UTF_8));
            }
        }

        urlString = stringBuilder.toString();
        if (useRedirect) {
            if (log.isTraceEnabled()) {
                log.trace("Redirecting error reporting to " + urlString);
            }
            // No need to delegate anymore
            if (!response.setErrorReported()) {
                return;
            }
            try {
                response.sendRedirect(urlString);
            } catch (IOException ioe) {
                // Ignore
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("Proxying error reporting to " + urlString);
            }
            HttpURLConnection httpURLConnection = null;
            boolean proxied = false;
            Exception failure = null;
            try {
                URL url = (new URI(urlString)).toURL();
                String protocol = url.getProtocol();
                if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
                    httpURLConnection = (HttpURLConnection) url.openConnection();
                    httpURLConnection.setConnectTimeout(PROXY_CONNECT_TIMEOUT_MS);
                    httpURLConnection.setReadTimeout(PROXY_READ_TIMEOUT_MS);
                    httpURLConnection.connect();
                    // Obtain the target response before modifying this response. Otherwise, a failure after the
                    // connection was opened would leave the target's metadata on this response, corrupting the
                    // fallback error report produced below.
                    InputStream inputStream = httpURLConnection.getInputStream();
                    response.setContentType(httpURLConnection.getContentType());
                    response.setContentLength(httpURLConnection.getContentLength());
                    OutputStream outputStream = response.getOutputStream();
                    IOTools.flow(inputStream, outputStream);
                    proxied = true;
                }
            } catch (URISyntaxException | IOException | IllegalArgumentException | IllegalStateException e) {
                failure = e;
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }
            if (proxied) {
                // No need to delegate anymore
                response.setErrorReported();
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(sm.getString("proxyErrorReportValve.error", urlString), failure);
                }
                // Discard any target metadata that was applied to this response before the failure, so that a stale
                // Content-Length cannot truncate or suppress the fallback report. Fall back to the default error
                // report rather than returning an empty response. The reported flag is consumed by super.report().
                response.setContentLength(-1);
                super.report(request, response, throwable);
            }
        }
    }
}

