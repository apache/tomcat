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
import java.util.Random;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Provides basic CSRF protection for a web application. The filter assumes
 * that:
 * <ul>
 * <li>The filter is mapped to /*</li>
 * <li>{@link HttpServletResponse#encodeRedirectURL(String)} and
 * {@link HttpServletResponse#encodeURL(String)} are used to encode all URLs
 * returned to the client
 * </ul>
 */
public class CsrfPreventionFilter extends FilterBase {

    private static final Log log =
        LogFactory.getLog(CsrfPreventionFilter.class);
    
    private final Random randomSource = new Random();

    @Override
    protected Log getLogger() {
        return log;
    }

    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        ServletResponse wResponse = null;
        
        if (request instanceof HttpServletRequest &&
                response instanceof HttpServletResponse) {
            
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            String previousNonce =
                req.getParameter(Constants.CSRF_NONCE_REQUEST_PARAM);
            String expectedNonce = (String) req.getSession(true).getAttribute(
                    Constants.CSRF_NONCE_SESSION_ATTR_NAME);
            
            if (expectedNonce != null && !expectedNonce.equals(previousNonce)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            
            String newNonce = generateNonce();
            
            req.getSession(true).setAttribute(
                    Constants.CSRF_NONCE_SESSION_ATTR_NAME, newNonce);
            
            wResponse = new CsrfResponseWrapper(res, newNonce);
        } else {
            wResponse = response;
        }
        
        chain.doFilter(request, wResponse);
    }

    /**
     * Generate a once time token (nonce) for authenticating subsequent
     * requests. This will also add the token to the session. The nonce
     * generation is a simplified version of ManagerBase.generateSessionId().
     * 
     */
    protected String generateNonce() {
        byte random[] = new byte[16];

        // Render the result as a String of hexadecimal digits
        StringBuilder buffer = new StringBuilder();

        randomSource.nextBytes(random);
       
        for (int j = 0; j < random.length; j++) {
            byte b1 = (byte) ((random[j] & 0xf0) >> 4);
            byte b2 = (byte) (random[j] & 0x0f);
            if (b1 < 10)
                buffer.append((char) ('0' + b1));
            else
                buffer.append((char) ('A' + (b1 - 10)));
            if (b2 < 10)
                buffer.append((char) ('0' + b2));
            else
                buffer.append((char) ('A' + (b2 - 10)));
        }

        return buffer.toString();
    }

    private static class CsrfResponseWrapper
            extends HttpServletResponseWrapper {

        private String nonce;

        public CsrfResponseWrapper(HttpServletResponse response, String nonce) {
            super(response);
            this.nonce = nonce;
        }

        @Override
        @Deprecated
        public String encodeRedirectUrl(String url) {
            return encodeRedirectURL(url);
        }

        @Override
        public String encodeRedirectURL(String url) {
            return addNonce(super.encodeRedirectURL(url));
        }

        @Override
        @Deprecated
        public String encodeUrl(String url) {
            return encodeURL(url);
        }

        @Override
        public String encodeURL(String url) {
            return addNonce(super.encodeURL(url));
        }
        
        /**
         * Return the specified URL with the nonce added to the query string
         *
         * @param url URL to be modified
         * @param nonce The nonce to add
         */
        private String addNonce(String url) {

            if ((url == null) || (nonce == null))
                return (url);

            String path = url;
            String query = "";
            String anchor = "";
            int question = url.indexOf('?');
            if (question >= 0) {
                path = url.substring(0, question);
                query = url.substring(question);
            }
            int pound = path.indexOf('#');
            if (pound >= 0) {
                anchor = path.substring(pound);
                path = path.substring(0, pound);
            }
            StringBuilder sb = new StringBuilder(path);
            sb.append(anchor);
            if (query.length() >0) {
                sb.append(query);
                sb.append('&');
            } else {
                sb.append('?');
            }
            sb.append(Constants.CSRF_NONCE_REQUEST_PARAM);
            sb.append('=');
            sb.append(nonce);
            return (sb.toString());
        }
    }
}
