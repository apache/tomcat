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
package org.apache.tomcat.util.http;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.res.StringManager;

/**
 *
 * @author Costin Manolache
 */
public final class Parameters {

    private static final Log log = LogFactory.getLog(Parameters.class);

    private static final UserDataHelper userDataLog = new UserDataHelper(log);

    private static final UserDataHelper maxParamCountLog = new UserDataHelper(log);

    private static final StringManager sm =
        StringManager.getManager("org.apache.tomcat.util.http");

    private final Map<String,ArrayList<String>> paramHashValues =
            new LinkedHashMap<>();
    private boolean didQueryParameters=false;

    private MessageBytes queryMB;

    private UDecoder urlDec;
    private final MessageBytes decodedQuery = MessageBytes.newInstance();

    private Charset charset = StandardCharsets.ISO_8859_1;
    private Charset queryStringCharset = StandardCharsets.UTF_8;

    private int limit = -1;
    private int parameterCount = 0;

    /**
     * Set to the reason for the failure (the first failure if there is more
     * than one) if there were failures during parameter parsing.
     */
    private FailReason parseFailedReason = null;

    public Parameters() {
        // NO-OP
    }

    public void setQuery( MessageBytes queryMB ) {
        this.queryMB=queryMB;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        if (charset == null) {
            charset = DEFAULT_BODY_CHARSET;
        }
        this.charset = charset;
        if(log.isDebugEnabled()) {
            log.debug("Set encoding to " + charset.name());
        }
    }

    public void setQueryStringCharset(Charset queryStringCharset) {
        if (queryStringCharset == null) {
            queryStringCharset = DEFAULT_URI_CHARSET;
        }
        this.queryStringCharset = queryStringCharset;

        if(log.isDebugEnabled()) {
            log.debug("Set query string encoding to " + queryStringCharset.name());
        }
    }


    public boolean isParseFailed() {
        return parseFailedReason != null;
    }


    public FailReason getParseFailedReason() {
        return parseFailedReason;
    }


    public void setParseFailedReason(FailReason failReason) {
        if (this.parseFailedReason == null) {
            this.parseFailedReason = failReason;
        }
    }


    public void recycle() {
        parameterCount = 0;
        paramHashValues.clear();
        didQueryParameters = false;
        charset = DEFAULT_BODY_CHARSET;
        decodedQuery.recycle();
        parseFailedReason = null;
    }


    // -------------------- Data access --------------------
    // Access to the current name/values, no side effect ( processing ).
    // You must explicitly call handleQueryParameters and the post methods.

    public String[] getParameterValues(String name) {
        handleQueryParameters();
        // no "facade"
        ArrayList<String> values = paramHashValues.get(name);
        if (values == null) {
            return null;
        }
        return values.toArray(new String[0]);
    }

    public Enumeration<String> getParameterNames() {
        handleQueryParameters();
        return Collections.enumeration(paramHashValues.keySet());
    }

    public String getParameter(String name ) {
        handleQueryParameters();
        ArrayList<String> values = paramHashValues.get(name);
        if (values != null) {
            if(values.size() == 0) {
                return "";
            }
            return values.get(0);
        } else {
            return null;
        }
    }
    // -------------------- Processing --------------------
    /** Process the query string into parameters
     */
    public void handleQueryParameters() {
        if (didQueryParameters) {
            return;
        }

        didQueryParameters = true;

        if (queryMB == null || queryMB.isNull()) {
            return;
        }

        if(log.isDebugEnabled()) {
            log.debug("Decoding query " + decodedQuery + " " + queryStringCharset.name());
        }

        try {
            decodedQuery.duplicate(queryMB);
        } catch (IOException e) {
            // Can't happen, as decodedQuery can't overflow
            e.printStackTrace();
        }
        processParameters(decodedQuery, queryStringCharset);
    }


    public void addParameter( String key, String value )
            throws IllegalStateException {

        if( key==null ) {
            return;
        }

        parameterCount ++;
        if (limit > -1 && parameterCount > limit) {
            // Processing this parameter will push us over the limit. ISE is
            // what Request.parseParts() uses for requests that are too big
            setParseFailedReason(FailReason.TOO_MANY_PARAMETERS);
            throw new IllegalStateException(sm.getString(
                    "parameters.maxCountFail", Integer.valueOf(limit)));
        }

        ArrayList<String> values = paramHashValues.get(key);
        if (values == null) {
            values = new ArrayList<>(1);
            paramHashValues.put(key, values);
        }
        values.add(value);
    }

    public void setURLDecoder( UDecoder u ) {
        urlDec=u;
    }

    // -------------------- Parameter parsing --------------------
    // we are called from a single thread - we can do it the hard way
    // if needed
    private final ByteChunk tmpName=new ByteChunk();
    private final ByteChunk tmpValue=new ByteChunk();
    private final ByteChunk origName=new ByteChunk();
    private final ByteChunk origValue=new ByteChunk();
    private static final Charset DEFAULT_BODY_CHARSET = StandardCharsets.ISO_8859_1;
    private static final Charset DEFAULT_URI_CHARSET = StandardCharsets.UTF_8;


    public void processParameters( byte bytes[], int start, int len ) {
        processParameters(bytes, start, len, charset);
    }

    private void processParameters(byte bytes[], int start, int len, Charset charset) {

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("parameters.bytes",
                    new String(bytes, start, len, DEFAULT_BODY_CHARSET)));
        }

        int decodeFailCount = 0;

        int pos = start;
        int end = start + len;

        while(pos < end) {
            int nameStart = pos;
            int nameEnd = -1;
            int valueStart = -1;
            int valueEnd = -1;

            boolean parsingName = true;
            boolean decodeName = false;
            boolean decodeValue = false;
            boolean parameterComplete = false;

            do {
                switch(bytes[pos]) {
                    case '=':
                        if (parsingName) {
                            // Name finished. Value starts from next character
                            nameEnd = pos;
                            parsingName = false;
                            valueStart = ++pos;
                        } else {
                            // Equals character in value
                            pos++;
                        }
                        break;
                    case '&':
                        if (parsingName) {
                            // Name finished. No value.
                            nameEnd = pos;
                        } else {
                            // Value finished
                            valueEnd  = pos;
                        }
                        parameterComplete = true;
                        pos++;
                        break;
                    case '%':
                    case '+':
                        // Decoding required
                        if (parsingName) {
                            decodeName = true;
                        } else {
                            decodeValue = true;
                        }
                        pos ++;
                        break;
                    default:
                        pos ++;
                        break;
                }
            } while (!parameterComplete && pos < end);

            if (pos == end) {
                if (nameEnd == -1) {
                    nameEnd = pos;
                } else if (valueStart > -1 && valueEnd == -1){
                    valueEnd = pos;
                }
            }

            if (log.isDebugEnabled() && valueStart == -1) {
                log.debug(sm.getString("parameters.noequal",
                        Integer.valueOf(nameStart), Integer.valueOf(nameEnd),
                        new String(bytes, nameStart, nameEnd-nameStart, DEFAULT_BODY_CHARSET)));
            }

            if (nameEnd <= nameStart ) {
                if (valueStart == -1) {
                    // &&
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("parameters.emptyChunk"));
                    }
                    // Do not flag as error
                    continue;
                }
                // &=foo&
                UserDataHelper.Mode logMode = userDataLog.getNextMode();
                if (logMode != null) {
                    String extract;
                    if (valueEnd > nameStart) {
                        extract = new String(bytes, nameStart, valueEnd - nameStart,
                                DEFAULT_BODY_CHARSET);
                    } else {
                        extract = "";
                    }
                    String message = sm.getString("parameters.invalidChunk",
                            Integer.valueOf(nameStart),
                            Integer.valueOf(valueEnd), extract);
                    switch (logMode) {
                        case INFO_THEN_DEBUG:
                            message += sm.getString("parameters.fallToDebug");
                            //$FALL-THROUGH$
                        case INFO:
                            log.info(message);
                            break;
                        case DEBUG:
                            log.debug(message);
                    }
                }
                setParseFailedReason(FailReason.NO_NAME);
                continue;
                // invalid chunk - it's better to ignore
            }

            tmpName.setBytes(bytes, nameStart, nameEnd - nameStart);
            if (valueStart >= 0) {
                tmpValue.setBytes(bytes, valueStart, valueEnd - valueStart);
            } else {
                tmpValue.setBytes(bytes, 0, 0);
            }

            // Take copies as if anything goes wrong originals will be
            // corrupted. This means original values can be logged.
            // For performance - only done for debug
            if (log.isDebugEnabled()) {
                try {
                    origName.append(bytes, nameStart, nameEnd - nameStart);
                    if (valueStart >= 0) {
                        origValue.append(bytes, valueStart, valueEnd - valueStart);
                    } else {
                        origValue.append(bytes, 0, 0);
                    }
                } catch (IOException ioe) {
                    // Should never happen...
                    log.error(sm.getString("parameters.copyFail"), ioe);
                }
            }

            try {
                String name;
                String value;

                if (decodeName) {
                    urlDecode(tmpName);
                }
                tmpName.setCharset(charset);
                name = tmpName.toString();

                if (valueStart >= 0) {
                    if (decodeValue) {
                        urlDecode(tmpValue);
                    }
                    tmpValue.setCharset(charset);
                    value = tmpValue.toString();
                } else {
                    value = "";
                }

                try {
                    addParameter(name, value);
                } catch (IllegalStateException ise) {
                    // Hitting limit stops processing further params but does
                    // not cause request to fail.
                    UserDataHelper.Mode logMode = maxParamCountLog.getNextMode();
                    if (logMode != null) {
                        String message = ise.getMessage();
                        switch (logMode) {
                            case INFO_THEN_DEBUG:
                                message += sm.getString(
                                        "parameters.maxCountFail.fallToDebug");
                                //$FALL-THROUGH$
                            case INFO:
                                log.info(message);
                                break;
                            case DEBUG:
                                log.debug(message);
                        }
                    }
                    break;
                }
            } catch (IOException e) {
                setParseFailedReason(FailReason.URL_DECODING);
                decodeFailCount++;
                if (decodeFailCount == 1 || log.isDebugEnabled()) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("parameters.decodeFail.debug",
                                origName.toString(), origValue.toString()), e);
                    } else if (log.isInfoEnabled()) {
                        UserDataHelper.Mode logMode = userDataLog.getNextMode();
                        if (logMode != null) {
                            String message = sm.getString(
                                    "parameters.decodeFail.info",
                                    tmpName.toString(), tmpValue.toString());
                            switch (logMode) {
                                case INFO_THEN_DEBUG:
                                    message += sm.getString("parameters.fallToDebug");
                                    //$FALL-THROUGH$
                                case INFO:
                                    log.info(message);
                                    break;
                                case DEBUG:
                                    log.debug(message);
                            }
                        }
                    }
                }
            }

            tmpName.recycle();
            tmpValue.recycle();
            // Only recycle copies if we used them
            if (log.isDebugEnabled()) {
                origName.recycle();
                origValue.recycle();
            }
        }

        if (decodeFailCount > 1 && !log.isDebugEnabled()) {
            UserDataHelper.Mode logMode = userDataLog.getNextMode();
            if (logMode != null) {
                String message = sm.getString(
                        "parameters.multipleDecodingFail",
                        Integer.valueOf(decodeFailCount));
                switch (logMode) {
                    case INFO_THEN_DEBUG:
                        message += sm.getString("parameters.fallToDebug");
                        //$FALL-THROUGH$
                    case INFO:
                        log.info(message);
                        break;
                    case DEBUG:
                        log.debug(message);
                }
            }
        }
    }

    private void urlDecode(ByteChunk bc)
        throws IOException {
        if( urlDec==null ) {
            urlDec=new UDecoder();
        }
        urlDec.convert(bc, true);
    }

    public void processParameters(MessageBytes data, Charset charset) {
        if( data==null || data.isNull() || data.getLength() <= 0 ) {
            return;
        }

        if( data.getType() != MessageBytes.T_BYTES ) {
            data.toBytes();
        }
        ByteChunk bc=data.getByteChunk();
        processParameters(bc.getBytes(), bc.getOffset(), bc.getLength(), charset);
    }

    /**
     * Debug purpose
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ArrayList<String>> e : paramHashValues.entrySet()) {
            sb.append(e.getKey()).append('=');
            StringUtils.join(e.getValue(), ',', sb);
            sb.append('\n');
        }
        return sb.toString();
    }


    public enum FailReason {
        CLIENT_DISCONNECT,
        MULTIPART_CONFIG_INVALID,
        INVALID_CONTENT_TYPE,
        IO_ERROR,
        NO_NAME,
        POST_TOO_LARGE,
        REQUEST_BODY_INCOMPLETE,
        TOO_MANY_PARAMETERS,
        UNKNOWN,
        URL_DECODING
    }
}
