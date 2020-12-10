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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.NetMask;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public final class RemoteCIDRValve extends RequestFilterValve {

    /**
     * Our logger
     */
    private static final Log log = LogFactory.getLog(RemoteCIDRValve.class);

    /**
     * The list of allowed {@link NetMask}s
     */
    private final List<NetMask> allow = new ArrayList<>();

    /**
     * The list of denied {@link NetMask}s
     */
    private final List<NetMask> deny = new ArrayList<>();


    public RemoteCIDRValve() {
    }


    /**
     * Return a string representation of the {@link NetMask} list in #allow.
     *
     * @return the #allow list as a string, without the leading '[' and trailing
     *         ']'
     */
    @Override
    public String getAllow() {
        return allow.toString().replace("[", "").replace("]", "");
    }


    /**
     * Fill the #allow list with the list of netmasks provided as an argument,
     * if any. Calls #fillFromInput.
     *
     * @param input The list of netmasks, as a comma separated string
     * @throws IllegalArgumentException One or more netmasks are invalid
     */
    @Override
    public void setAllow(final String input) {
        final List<String> messages = fillFromInput(input, allow);

        if (messages.isEmpty()) {
            return;
        }

        allowValid = false;
        for (final String message : messages) {
            log.error(message);
        }

        throw new IllegalArgumentException(sm.getString("remoteCidrValve.invalid", "allow"));
    }


    /**
     * Return a string representation of the {@link NetMask} list in #deny.
     *
     * @return the #deny list as a string, without the leading '[' and trailing
     *         ']'
     */
    @Override
    public String getDeny() {
        return deny.toString().replace("[", "").replace("]", "");
    }


    /**
     * Fill the #deny list with the list of netmasks provided as an argument, if
     * any. Calls #fillFromInput.
     *
     * @param input The list of netmasks, as a comma separated string
     * @throws IllegalArgumentException One or more netmasks are invalid
     */
    @Override
    public void setDeny(final String input) {
        final List<String> messages = fillFromInput(input, deny);

        if (messages.isEmpty()) {
            return;
        }

        denyValid = false;
        for (final String message : messages) {
            log.error(message);
        }

        throw new IllegalArgumentException(sm.getString("remoteCidrValve.invalid", "deny"));
    }


    @Override
    public void invoke(final Request request, final Response response) throws IOException, ServletException {
        String property;
        if (getUsePeerAddress()) {
            property = request.getPeerAddr();
        } else {
            property = request.getRequest().getRemoteAddr();
        }
        if (getAddConnectorPort()) {
            property = property + ";" +
                request.getConnector().getPortWithOffset();
        }
        process(property, request, response);
    }

    @Override
    public boolean isAllowed(final String property) {

        final int portIdx = property.indexOf(';');
        final int port;
        final String nonPortPart;

        if (portIdx == -1) {
            if (getAddConnectorPort()) {
                log.error(sm.getString("remoteCidrValve.noPort"));
                return false;
            }
            port = -1;
            nonPortPart = property;
        } else {
            if (!getAddConnectorPort()) {
                log.error(sm.getString("remoteCidrValve.unexpectedPort"));
                return false;
            }
            nonPortPart = property.substring(0, portIdx);
            try {
                port = Integer.parseInt(property.substring(portIdx + 1));
            } catch (NumberFormatException e) {
                // This should be in the 'could never happen' category but handle it
                // to be safe.
                log.error(sm.getString("remoteCidrValve.noPort"), e);
                return false;
            }
        }

        final InetAddress addr;
        try {
            addr = InetAddress.getByName(nonPortPart);
        } catch (UnknownHostException e) {
            // This should be in the 'could never happen' category but handle it
            // to be safe.
            log.error(sm.getString("remoteCidrValve.noRemoteIp"), e);
            return false;
        }

        for (final NetMask nm : deny) {
            if (getAddConnectorPort()) {
                if (nm.matches(addr, port)) {
                    return false;
                }
            } else {
                if (nm.matches(addr)) {
                    return false;
                }
            }
        }

        for (final NetMask nm : allow) {
            if (getAddConnectorPort()) {
                if (nm.matches(addr, port)) {
                    return true;
                }
            } else {
                if (nm.matches(addr)) {
                    return true;
                }
            }
        }

        // Allow if deny is specified but allow isn't
        if (!deny.isEmpty() && allow.isEmpty()) {
            return true;
        }

        // Deny this request
        return false;
    }


    @Override
    protected Log getLog() {
        return log;
    }


    /**
     * Fill a {@link NetMask} list from a string input containing a
     * comma-separated list of (hopefully valid) {@link NetMask}s.
     *
     * @param input The input string
     * @param target The list to fill
     * @return a string list of processing errors (empty when no errors)
     */

    private List<String> fillFromInput(final String input, final List<NetMask> target) {
        target.clear();
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> messages = new LinkedList<>();
        NetMask nm;

        for (final String s : input.split("\\s*,\\s*")) {
            try {
                nm = new NetMask(s);
                target.add(nm);
            } catch (IllegalArgumentException e) {
                messages.add(s + ": " + e.getMessage());
            }
        }

        return Collections.unmodifiableList(messages);
    }
}
