/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.ResponseUtil;

public class CompressionConfig {

    private int compressionLevel = 0;
    private Pattern noCompressionUserAgents = null;
    private String compressibleMimeType = "text/html,text/xml,text/plain,text/css," +
            "text/javascript,application/javascript,application/json,application/xml";
    private String[] compressibleMimeTypes = null;
    private int compressionMinSize = 2048;


    /**
     * Set compression level.
     *
     * @param compression One of <code>on</code>, <code>force</code>,
     *                    <code>off</code> or the minimum compression size in
     *                    bytes which implies <code>on</code>
     */
    public void setCompression(String compression) {
        if (compression.equals("on")) {
            this.compressionLevel = 1;
        } else if (compression.equals("force")) {
            this.compressionLevel = 2;
        } else if (compression.equals("off")) {
            this.compressionLevel = 0;
        } else {
            try {
                // Try to parse compression as an int, which would give the
                // minimum compression size
                setCompressionMinSize(Integer.parseInt(compression));
                this.compressionLevel = 1;
            } catch (Exception e) {
                this.compressionLevel = 0;
            }
        }
    }


    /**
     * Return compression level.
     *
     * @return The current compression level in string form (off/on/force)
     */
    public String getCompression() {
        switch (compressionLevel) {
        case 0:
            return "off";
        case 1:
            return "on";
        case 2:
            return "force";
        }
        return "off";
    }


    public int getCompressionLevel() {
        return compressionLevel;
    }


    /**
     * Obtain the String form of the regular expression that defines the user
     * agents to not use gzip with.
     *
     * @return The regular expression as a String
     */
    public String getNoCompressionUserAgents() {
        if (noCompressionUserAgents == null) {
            return null;
        } else {
            return noCompressionUserAgents.toString();
        }
    }


    public Pattern getNoCompressionUserAgentsPattern() {
        return noCompressionUserAgents;
    }


    /**
     * Set no compression user agent pattern. Regular expression as supported
     * by {@link Pattern}. e.g.: <code>gorilla|desesplorer|tigrus</code>.
     *
     * @param noCompressionUserAgents The regular expression for user agent
     *                                strings for which compression should not
     *                                be applied
     */
    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        if (noCompressionUserAgents == null || noCompressionUserAgents.length() == 0) {
            this.noCompressionUserAgents = null;
        } else {
            this.noCompressionUserAgents =
                Pattern.compile(noCompressionUserAgents);
        }
    }


    public String getCompressibleMimeType() {
        return compressibleMimeType;
    }


    public void setCompressibleMimeType(String valueS) {
        compressibleMimeType = valueS;
        compressibleMimeTypes = null;
    }


    public String[] getCompressibleMimeTypes() {
        String[] result = compressibleMimeTypes;
        if (result != null) {
            return result;
        }
        List<String> values = new ArrayList<>();
        StringTokenizer tokens = new StringTokenizer(compressibleMimeType, ",");
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken().trim();
            if (token.length() > 0) {
                values.add(token);
            }
        }
        result = values.toArray(new String[values.size()]);
        compressibleMimeTypes = result;
        return result;
    }


    public int getCompressionMinSize() {
        return compressionMinSize;
    }


    /**
     * Set Minimum size to trigger compression.
     *
     * @param compressionMinSize The minimum content length required for
     *                           compression in bytes
     */
    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }


    /**
     * Determines if compression should be enabled for the given response and if
     * it is, sets any necessary headers to mark it as such.
     *
     * @param request  The request that triggered the response
     * @param response The response to consider compressing
     *
     * @return {@code true} if compression was enabled for the given response,
     *         otherwise {@code false}
     */
    public boolean useCompression(Request request, Response response) {
        // Check if compression is enabled
        if (compressionLevel == 0) {
            return false;
        }

        MimeHeaders responseHeaders = response.getMimeHeaders();

        // Check if content is not already compressed
        MessageBytes contentEncodingMB = responseHeaders.getValue("Content-Encoding");
        if (contentEncodingMB != null &&
                (contentEncodingMB.indexOf("gzip") != -1 ||
                        contentEncodingMB.indexOf("br") != -1)) {
            return false;
        }

        // If force mode, the length and MIME type checks are skipped
        if (compressionLevel != 2) {
            // Check if the response is of sufficient length to trigger the compression
            long contentLength = response.getContentLengthLong();
            if (contentLength != -1 && contentLength < compressionMinSize) {
                return false;
            }

            // Check for compatible MIME-TYPE
            String[] compressibleMimeTypes = getCompressibleMimeTypes();
            if (compressibleMimeTypes != null &&
                    !startsWithStringArray(compressibleMimeTypes, response.getContentType())) {
                return false;
            }
        }

        // If processing reaches this far, the response might be compressed.
        // Therefore, set the Vary header to keep proxies happy
        ResponseUtil.addVaryFieldName(responseHeaders, "accept-encoding");

        // Check if browser support gzip encoding
        MessageBytes acceptEncodingMB = request.getMimeHeaders().getValue("accept-encoding");
        if ((acceptEncodingMB == null) || (acceptEncodingMB.indexOf("gzip") == -1)) {
            return false;
        }

        // If force mode, the browser checks are skipped
        if (compressionLevel != 2) {
            // Check for incompatible Browser
            Pattern noCompressionUserAgents = this.noCompressionUserAgents;
            if (noCompressionUserAgents != null) {
                MessageBytes userAgentValueMB = request.getMimeHeaders().getValue("user-agent");
                if(userAgentValueMB != null) {
                    String userAgentValue = userAgentValueMB.toString();
                    if (noCompressionUserAgents.matcher(userAgentValue).matches()) {
                        return false;
                    }
                }
            }
        }

        // All checks have passed. Compression is enabled.

        // Compressed content length is unknown so mark it as such.
        response.setContentLength(-1);
        // Configure the content encoding for compressed content
        responseHeaders.setValue("Content-Encoding").setString("gzip");

        return true;
    }


    /**
     * Checks if any entry in the string array starts with the specified value
     *
     * @param sArray the StringArray
     * @param value string
     */
    private static boolean startsWithStringArray(String sArray[], String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < sArray.length; i++) {
            if (value.startsWith(sArray[i])) {
                return true;
            }
        }
        return false;
    }
}
