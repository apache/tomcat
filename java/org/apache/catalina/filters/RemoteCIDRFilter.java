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
package org.apache.catalina.filters;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.util.NetMask;
import org.apache.catalina.util.NetMaskSet;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public final class RemoteCIDRFilter extends FilterBase {

    /**
     * text/plain MIME type: this is the MIME type we return when a {@link ServletResponse} is not an
     * {@link HttpServletResponse}
     */
    private static final String PLAIN_TEXT_MIME_TYPE = "text/plain";

    /**
     * Our logger
     */
    private final Log log = LogFactory.getLog(RemoteCIDRFilter.class); // must not be static

    /**
     * The allowed {@link NetMask}s.
     */
    private final NetMaskSet allow = new NetMaskSet();

    /**
     * The denied {@link NetMask}s.
     */
    private final NetMaskSet deny = new NetMaskSet();


    /**
     * Return a string representation of the {@link NetMask} list in #allow.
     *
     * @return the #allow list as a string, without the leading '[' and trailing ']'
     */
    public String getAllow() {
        return allow.toString().replace("[", "").replace("]", "");
    }


    /**
     * Fill the #allow list with the list of netmasks provided as an argument, if any. Calls #fillFromInput.
     *
     * @param input The list of netmasks, as a comma separated string
     *
     * @throws IllegalArgumentException One or more netmasks are invalid
     */
    public void setAllow(final String input) {
        final List<String> messages = allow.addAll(input);

        if (messages.isEmpty()) {
            return;
        }

        for (final String message : messages) {
            log.error(message);
        }

        throw new IllegalArgumentException(sm.getString("remoteCidrFilter.invalid", "allow"));
    }


    /**
     * Return a string representation of the {@link NetMask} list in #deny.
     *
     * @return the #deny list as string, without the leading '[' and trailing ']'
     */
    public String getDeny() {
        return deny.toString().replace("[", "").replace("]", "");
    }


    /**
     * Fill the #deny list with the list of netmasks provided as an argument, if any. Calls #fillFromInput.
     *
     * @param input The list of netmasks, as a comma separated string
     *
     * @throws IllegalArgumentException One or more netmasks are invalid
     */
    public void setDeny(final String input) {
        final List<String> messages = deny.addAll(input);

        if (messages.isEmpty()) {
            return;
        }

        for (final String message : messages) {
            log.error(message);
        }

        throw new IllegalArgumentException(sm.getString("remoteCidrFilter.invalid", "deny"));
    }


    @Override
    protected boolean isConfigProblemFatal() {
        // Failure to configure a security related component should always be
        // fatal.
        return true;
    }


    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        if (isAllowed(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }

        if (!(response instanceof HttpServletResponse)) {
            sendErrorWhenNotHttp(response);
            return;
        }

        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }


    @Override
    public Log getLogger() {
        return log;
    }


    /**
     * Test if a remote's IP address is allowed to proceed.
     *
     * @param property The remote's IP address, as a string
     *
     * @return true if allowed
     */
    private boolean isAllowed(final String property) {
        final InetAddress addr;

        try {
            addr = InetAddress.getByName(property);
        } catch (UnknownHostException e) {
            // This should be in the 'could never happen' category but handle it
            // to be safe.
            log.error(sm.getString("remoteCidrFilter.noRemoteIp"), e);
            return false;
        }

        if (deny.contains(addr)) {
            return false;
        }

        if (allow.contains(addr)) {
            return true;
        }

        // Allow if deny is specified but allow isn't
        // Deny this request otherwise
        return !deny.isEmpty() && allow.isEmpty();
    }


    private void sendErrorWhenNotHttp(ServletResponse response) throws IOException {
        final PrintWriter writer = response.getWriter();
        response.setContentType(PLAIN_TEXT_MIME_TYPE);
        writer.write(sm.getString("http.403"));
        writer.flush();
    }
}
