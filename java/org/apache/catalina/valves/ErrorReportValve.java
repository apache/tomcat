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
package org.apache.catalina.valves;


import java.io.IOException;
import java.io.Writer;
import java.util.Scanner;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.ServerInfo;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>Implementation of a Valve that outputs HTML error pages.</p>
 *
 * <p>This Valve should be attached at the Host level, although it will work
 * if attached to a Context.</p>
 *
 * <p>HTML code from the Cocoon 2 project.</p>
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 * @author <a href="mailto:nicolaken@supereva.it">Nicola Ken Barozzi</a> Aisa
 * @author <a href="mailto:stefano@apache.org">Stefano Mazzocchi</a>
 * @author Yoav Shapira
 */
public class ErrorReportValve extends ValveBase {

    //------------------------------------------------------ Constructor
    public ErrorReportValve() {
        super(true);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Invoke the next Valve in the sequence. When the invoke returns, check
     * the response state, and output an error report is necessary.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Perform the request
        getNext().invoke(request, response);

        if (response.isCommitted()) {
            return;
        }

        Throwable throwable =
                (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (request.isAsyncStarted() && ((response.getStatus() < 400 &&
                throwable == null) || request.isAsyncDispatching())) {
            return;
        }

        if (throwable != null) {

            // The response is an error
            response.setError();

            // Reset the response (if possible)
            try {
                response.reset();
            } catch (IllegalStateException e) {
                // Ignore
            }

            response.sendError
                (HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        }

        response.setSuspended(false);

        try {
            report(request, response, throwable);
        } catch (Throwable tt) {
            ExceptionUtils.handleThrowable(tt);
        }

        if (request.isAsyncStarted()) {
            request.getAsyncContext().complete();
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Prints out an error report.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param throwable The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    protected void report(Request request, Response response,
                          Throwable throwable) {

        // Do nothing on non-HTTP responses
        int statusCode = response.getStatus();

        // Do nothing on a 1xx, 2xx and 3xx status
        // Do nothing if anything has been written already
        if (statusCode < 400 || response.getContentWritten() > 0 ||
                !response.isError()) {
            return;
        }

        String message = RequestUtil.filter(response.getMessage());
        if (message == null) {
            if (throwable != null) {
                String exceptionMessage = throwable.getMessage();
                if (exceptionMessage != null && exceptionMessage.length() > 0) {
                    message = RequestUtil.filter(
                            (new Scanner(exceptionMessage)).nextLine());
                }
            }
            if (message == null) {
                message = "";
            }
        }

        // Do nothing if there is no report for the specified status code and
        // no error message provided
        String report = null;
        StringManager smClient = StringManager.getManager(
                Constants.Package, request.getLocales());
        response.setLocale(smClient.getLocale());
        try {
            report = smClient.getString("http." + statusCode);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }
        if (report == null) {
            if (message.length() == 0) {
                return;
            } else {
                report = smClient.getString("errorReportValve.noDescription");
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head><title>");
        sb.append(ServerInfo.getServerInfo()).append(" - ");
        sb.append(smClient.getString("errorReportValve.errorReport"));
        sb.append("</title>");
        sb.append("<style type=\"text/css\">");
        sb.append(org.apache.catalina.util.TomcatCSS.TOMCAT_CSS);
        sb.append("</style> ");
        sb.append("</head><body>");
        sb.append("<h1>");
        sb.append(smClient.getString("errorReportValve.statusHeader",
                               "" + statusCode, message)).append("</h1>");
        sb.append("<div class=\"line\"></div>");
        sb.append("<p><b>type</b> ");
        if (throwable != null) {
            sb.append(smClient.getString("errorReportValve.exceptionReport"));
        } else {
            sb.append(smClient.getString("errorReportValve.statusReport"));
        }
        sb.append("</p>");
        sb.append("<p><b>");
        sb.append(smClient.getString("errorReportValve.message"));
        sb.append("</b> <u>");
        sb.append(message).append("</u></p>");
        sb.append("<p><b>");
        sb.append(smClient.getString("errorReportValve.description"));
        sb.append("</b> <u>");
        sb.append(report);
        sb.append("</u></p>");

        if (throwable != null) {

            String stackTrace = getPartialServletStackTrace(throwable);
            sb.append("<p><b>");
            sb.append(smClient.getString("errorReportValve.exception"));
            sb.append("</b></p><pre>");
            sb.append(RequestUtil.filter(stackTrace));
            sb.append("</pre>");

            int loops = 0;
            Throwable rootCause = throwable.getCause();
            while (rootCause != null && (loops < 10)) {
                stackTrace = getPartialServletStackTrace(rootCause);
                sb.append("<p><b>");
                sb.append(smClient.getString("errorReportValve.rootCause"));
                sb.append("</b></p><pre>");
                sb.append(RequestUtil.filter(stackTrace));
                sb.append("</pre>");
                // In case root cause is somehow heavily nested
                rootCause = rootCause.getCause();
                loops++;
            }

            sb.append("<p><b>");
            sb.append(smClient.getString("errorReportValve.note"));
            sb.append("</b> <u>");
            sb.append(smClient.getString("errorReportValve.rootCauseInLogs",
                                   ServerInfo.getServerInfo()));
            sb.append("</u></p>");

        }

        sb.append("<hr class=\"line\">");
        sb.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        sb.append("</body></html>");

        try {
            try {
                response.setContentType("text/html");
                response.setCharacterEncoding("utf-8");
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                if (container.getLogger().isDebugEnabled()) {
                    container.getLogger().debug("status.setContentType", t);
                }
            }
            Writer writer = response.getReporter();
            if (writer != null) {
                // If writer is null, it's an indication that the response has
                // been hard committed already, which should never happen
                writer.write(sb.toString());
            }
        } catch (IOException e) {
            // Ignore
        } catch (IllegalStateException e) {
            // Ignore
        }

    }


    /**
     * Print out a partial servlet stack trace (truncating at the last
     * occurrence of javax.servlet.).
     */
    protected String getPartialServletStackTrace(Throwable t) {
        StringBuilder trace = new StringBuilder();
        trace.append(t.toString()).append('\n');
        StackTraceElement[] elements = t.getStackTrace();
        int pos = elements.length;
        for (int i = elements.length - 1; i >= 0; i--) {
            if ((elements[i].getClassName().startsWith
                 ("org.apache.catalina.core.ApplicationFilterChain"))
                && (elements[i].getMethodName().equals("internalDoFilter"))) {
                pos = i;
                break;
            }
        }
        for (int i = 0; i < pos; i++) {
            if (!(elements[i].getClassName().startsWith
                  ("org.apache.catalina.core."))) {
                trace.append('\t').append(elements[i].toString()).append('\n');
            }
        }
        return trace.toString();
    }
}
