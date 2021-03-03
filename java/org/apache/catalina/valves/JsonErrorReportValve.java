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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>Implementation of a Valve that outputs error jsons.</p>
 *
 * <p>This Valve should be attached at the Host level, although it will work
 * if attached to a Context.</p>
 *
 */
public class JsonErrorReportValve extends ErrorReportValve {

    public JsonErrorReportValve() {
        super();
    }

    @Override
    protected void report(Request request, Response response, Throwable throwable) {

        int statusCode = response.getStatus();

        // Do nothing on a 1xx, 2xx and 3xx status
        // Do nothing if anything has been written already
        // Do nothing if the response hasn't been explicitly marked as in error
        //    and that error has not been reported.
        if (statusCode < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }

        // If an error has occurred that prevents further I/O, don't waste time
        // producing an error report that will never be read
        AtomicBoolean result = new AtomicBoolean(false);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, result);
        if (!result.get()) {
            return;
        }

        StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());
        response.setLocale(smClient.getLocale());
        String type = null;
        if (throwable != null) {
            type = smClient.getString("errorReportValve.exceptionReport");
        } else {
            type = smClient.getString("errorReportValve.statusReport");
        }
        String message = response.getMessage();
        if (message == null && throwable != null) {
            message = throwable.getMessage();
        }
        String description = null;
        description = smClient.getString("http." + statusCode + ".desc");
        if (description == null) {
            if (message == null || message.isEmpty()) {
                return;
            } else {
                description = smClient.getString("errorReportValve.noDescription");
            }
        }
        String jsonReport = "{\n" +
                            "  \"type\": \"" + type + "\",\n" +
                            "  \"message\": \"" + message + "\",\n" +
                            "  \"description\": \"" + description + "\"\n" +
                            "}";
        try {
            try {
                response.setContentType("application/json");
                response.setCharacterEncoding("utf-8");
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                if (container.getLogger().isDebugEnabled()) {
                    container.getLogger().debug("status.setContentType", t);
                }
            }
            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write(jsonReport);
                response.finishResponse();
                return;
            }
        } catch (IOException | IllegalStateException e) {
            // Ignore
        }
    }
}
