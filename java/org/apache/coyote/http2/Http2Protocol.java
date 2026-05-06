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
package org.apache.coyote.http2;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import javax.management.ObjectName;

import org.apache.coyote.Adapter;
import org.apache.coyote.ContinueResponseTiming;
import org.apache.coyote.Processor;
import org.apache.coyote.Request;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.Response;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.UpgradeToken;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http11.upgrade.UpgradeProcessorInternal;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * HTTP/2 protocol handler. Implements the {@link UpgradeProtocol} interface to allow HTTP/2 to be used as an
 * upgrade from HTTP/1.1 or via ALPN.
 */
public class Http2Protocol implements UpgradeProtocol {

    /**
     * Creates a new instance of the HTTP/2 protocol handler.
     */
    public Http2Protocol() {
        super();
    }


    private static final Log log = LogFactory.getLog(Http2Protocol.class);
    private static final StringManager sm = StringManager.getManager(Http2Protocol.class);

    static final long DEFAULT_READ_TIMEOUT = 5000;
    static final long DEFAULT_WRITE_TIMEOUT = 5000;
    static final long DEFAULT_KEEP_ALIVE_TIMEOUT = 20000;
    static final long DEFAULT_STREAM_READ_TIMEOUT = 20000;
    static final long DEFAULT_STREAM_WRITE_TIMEOUT = 20000;
    // The HTTP/2 specification recommends a minimum default of 100
    static final long DEFAULT_MAX_CONCURRENT_STREAMS = 100;
    // Maximum amount of streams which can be concurrently executed over
    // a single connection
    static final int DEFAULT_MAX_CONCURRENT_STREAM_EXECUTION = 20;
    // Default factor used when adjusting overhead count for overhead frames
    static final int DEFAULT_OVERHEAD_COUNT_FACTOR = 10;
    // Default factor used when adjusting overhead count for reset frames
    static final int DEFAULT_OVERHEAD_RESET_FACTOR = 50;
    // Not currently configurable. This makes the practical limit for
    // overheadCountFactor to be ~20. The exact limit will vary with traffic
    // patterns.
    static final int DEFAULT_OVERHEAD_REDUCTION_FACTOR = -20;
    static final int DEFAULT_OVERHEAD_CONTINUATION_THRESHOLD = 1024;
    static final int DEFAULT_OVERHEAD_DATA_THRESHOLD = 1024;
    static final int DEFAULT_OVERHEAD_WINDOW_UPDATE_THRESHOLD = 1024;

    private static final String HTTP_UPGRADE_NAME = "h2c";
    private static final String ALPN_NAME = "h2";
    private static final byte[] ALPN_IDENTIFIER = ALPN_NAME.getBytes(StandardCharsets.UTF_8);

    // All timeouts in milliseconds
    // These are the socket level timeouts
    private long readTimeout = DEFAULT_READ_TIMEOUT;
    private long writeTimeout = DEFAULT_WRITE_TIMEOUT;
    private long keepAliveTimeout = DEFAULT_KEEP_ALIVE_TIMEOUT;
    // These are the stream level timeouts
    private long streamReadTimeout = DEFAULT_STREAM_READ_TIMEOUT;
    private long streamWriteTimeout = DEFAULT_STREAM_WRITE_TIMEOUT;

    private long maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private int maxConcurrentStreamExecution = DEFAULT_MAX_CONCURRENT_STREAM_EXECUTION;
    // To advertise a different default to the client specify it here but DO NOT
    // change the default defined in ConnectionSettingsBase.
    private int initialWindowSize = ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE;
    // Limits
    private int maxHeaderCount = Constants.DEFAULT_MAX_HEADER_COUNT;
    private int maxTrailerCount = Constants.DEFAULT_MAX_TRAILER_COUNT;
    private int overheadCountFactor = DEFAULT_OVERHEAD_COUNT_FACTOR;
    private int overheadResetFactor = DEFAULT_OVERHEAD_RESET_FACTOR;
    private int overheadContinuationThreshold = DEFAULT_OVERHEAD_CONTINUATION_THRESHOLD;
    private int overheadDataThreshold = DEFAULT_OVERHEAD_DATA_THRESHOLD;
    private int overheadWindowUpdateThreshold = DEFAULT_OVERHEAD_WINDOW_UPDATE_THRESHOLD;

    private boolean initiatePingDisabled = false;
    private boolean useSendfile = true;
    // Reference to HTTP/1.1 protocol that this instance is configured under
    private AbstractHttp11Protocol<?> http11Protocol = null;

    private final RequestGroupInfo global = new RequestGroupInfo();

    /*
     * Setting discardRequestsAndResponses can have a significant performance impact. The magnitude of the impact is
     * very application dependent but with a simple Spring Boot application[1] returning a short JSON response running
     * on markt's desktop in 2024 the difference was 108k req/s with this set to true compared to 124k req/s with this
     * set to false. The larger the response and/or the larger the request processing time, the smaller the performance
     * impact of this setting.
     *
     * [1] https://github.com/markt-asf/spring-boot-http2
     */
    private boolean discardRequestsAndResponses = false;
    private final SynchronizedStack<Request> recycledRequestsAndResponses = new SynchronizedStack<>();

    /*
     * Additional time in nanoseconds between sending the first graceful GOAWAY (max stream id) and the final GOAWAY
     * (last seen stream id). During this time the server will continue to process new streams on the connection. This
     * is to mitigate the race of client-buffered/sent packets for new streams and the final GOAWAY (with last seen
     * stream id). By default, Tomcat uses the last computed RTT for this interval, but the RTT might have fluctuated
     * due to network or server load conditions, or the client (e.g. nghttp2) might have already buffered frames for
     * opening new streams on a connection.
     *
     * The name "drainTimeout" is taken from Envoy proxy's identical HTTP Connection Manager property and means exactly
     * the same.
     */
    private long drainTimeout;

    @Override
    public String getHttpUpgradeName(boolean isSSLEnabled) {
        if (isSSLEnabled) {
            return null;
        } else {
            return HTTP_UPGRADE_NAME;
        }
    }

    @Override
    public byte[] getAlpnIdentifier() {
        return ALPN_IDENTIFIER;
    }

    @Override
    public String getAlpnName() {
        return ALPN_NAME;
    }

    @Override
    public Processor getProcessor(SocketWrapperBase<?> socketWrapper, Adapter adapter) {
        return new UpgradeProcessorInternal(socketWrapper, new UpgradeToken(
                getInternalUpgradeHandler(socketWrapper, adapter, null), null, null, getUpgradeProtocolName()), null);
    }


    @Override
    public InternalHttpUpgradeHandler getInternalUpgradeHandler(SocketWrapperBase<?> socketWrapper, Adapter adapter,
            Request coyoteRequest) {
        return socketWrapper.hasAsyncIO() ? new Http2AsyncUpgradeHandler(this, adapter, coyoteRequest, socketWrapper) :
                new Http2UpgradeHandler(this, adapter, coyoteRequest, socketWrapper);
    }


    @Override
    public boolean accept(Request request) {
        // Should only be one HTTP2-Settings header
        Enumeration<String> settings = request.getMimeHeaders().values("HTTP2-Settings");
        int count = 0;
        while (settings.hasMoreElements()) {
            count++;
            settings.nextElement();
        }
        if (count != 1) {
            return false;
        }

        Enumeration<String> connection = request.getMimeHeaders().values("Connection");
        boolean found = false;
        while (connection.hasMoreElements() && !found) {
            found = connection.nextElement().contains("HTTP2-Settings");
        }
        return found;
    }


    /**
     * Returns the read timeout in milliseconds.
     *
     * @return the read timeout
     */
    public long getReadTimeout() {
        return readTimeout;
    }


    /**
     * Sets the read timeout in milliseconds.
     *
     * @param readTimeout the read timeout
     */
    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }


    /**
     * Returns the write timeout in milliseconds.
     *
     * @return the write timeout
     */
    public long getWriteTimeout() {
        return writeTimeout;
    }


    /**
     * Sets the write timeout in milliseconds.
     *
     * @param writeTimeout the write timeout
     */
    public void setWriteTimeout(long writeTimeout) {
        this.writeTimeout = writeTimeout;
    }


    /**
     * Returns the keep-alive timeout in milliseconds.
     *
     * @return the keep-alive timeout
     */
    public long getKeepAliveTimeout() {
        return keepAliveTimeout;
    }


    /**
     * Sets the keep-alive timeout in milliseconds.
     *
     * @param keepAliveTimeout the keep-alive timeout
     */
    public void setKeepAliveTimeout(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }


    /**
     * Returns the stream-level read timeout in milliseconds.
     *
     * @return the stream read timeout
     */
    public long getStreamReadTimeout() {
        return streamReadTimeout;
    }


    /**
     * Sets the stream-level read timeout in milliseconds.
     *
     * @param streamReadTimeout the stream read timeout
     */
    public void setStreamReadTimeout(long streamReadTimeout) {
        this.streamReadTimeout = streamReadTimeout;
    }


    /**
     * Returns the stream-level write timeout in milliseconds.
     *
     * @return the stream write timeout
     */
    public long getStreamWriteTimeout() {
        return streamWriteTimeout;
    }


    /**
     * Sets the stream-level write timeout in milliseconds.
     *
     * @param streamWriteTimeout the stream write timeout
     */
    public void setStreamWriteTimeout(long streamWriteTimeout) {
        this.streamWriteTimeout = streamWriteTimeout;
    }


    /**
     * Returns the maximum number of concurrent streams.
     *
     * @return the maximum concurrent streams
     */
    public long getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }


    /**
     * Sets the maximum number of concurrent streams.
     *
     * @param maxConcurrentStreams the maximum concurrent streams
     */
    public void setMaxConcurrentStreams(long maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }


    /**
     * Returns the maximum number of concurrently executing streams.
     *
     * @return the maximum concurrent stream execution count
     */
    public int getMaxConcurrentStreamExecution() {
        return maxConcurrentStreamExecution;
    }


    /**
     * Sets the maximum number of concurrently executing streams.
     *
     * @param maxConcurrentStreamExecution the maximum concurrent stream execution count
     */
    public void setMaxConcurrentStreamExecution(int maxConcurrentStreamExecution) {
        this.maxConcurrentStreamExecution = maxConcurrentStreamExecution;
    }


    /**
     * Returns the initial window size advertised to the client.
     *
     * @return the initial window size
     */
    public int getInitialWindowSize() {
        return initialWindowSize;
    }


    /**
     * Sets the initial window size advertised to the client.
     *
     * @param initialWindowSize the initial window size
     */
    public void setInitialWindowSize(int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }


    /**
     * Returns whether sendfile is enabled.
     *
     * @return {@code true} if sendfile is enabled
     */
    public boolean getUseSendfile() {
        return useSendfile;
    }


    /**
     * Enables or disables sendfile.
     *
     * @param useSendfile {@code true} to enable sendfile
     */
    public void setUseSendfile(boolean useSendfile) {
        this.useSendfile = useSendfile;
    }


    boolean isTrailerHeaderAllowed(String headerName) {
        return http11Protocol.isTrailerHeaderAllowed(headerName);
    }


    /**
     * Sets the maximum number of headers allowed per request.
     *
     * @param maxHeaderCount the maximum header count
     */
    public void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }


    /**
     * Returns the maximum number of headers allowed per request.
     *
     * @return the maximum header count
     */
    public int getMaxHeaderCount() {
        return maxHeaderCount;
    }


    /**
     * Returns the maximum size of request headers in bytes.
     *
     * @return the maximum header size
     */
    public int getMaxHeaderSize() {
        return http11Protocol.getMaxHttpRequestHeaderSize();
    }


    /**
     * Sets the maximum number of trailer headers allowed per request.
     *
     * @param maxTrailerCount the maximum trailer count
     */
    public void setMaxTrailerCount(int maxTrailerCount) {
        this.maxTrailerCount = maxTrailerCount;
    }


    /**
     * Returns the maximum number of trailer headers allowed per request.
     *
     * @return the maximum trailer count
     */
    public int getMaxTrailerCount() {
        return maxTrailerCount;
    }


    /**
     * Returns the maximum size of trailer headers in bytes.
     *
     * @return the maximum trailer size
     */
    public int getMaxTrailerSize() {
        return http11Protocol.getMaxTrailerSize();
    }


    /**
     * Returns the overhead count factor used for overhead frame tracking.
     *
     * @return the overhead count factor
     */
    public int getOverheadCountFactor() {
        return overheadCountFactor;
    }


    /**
     * Sets the overhead count factor used for overhead frame tracking.
     *
     * @param overheadCountFactor the overhead count factor
     */
    public void setOverheadCountFactor(int overheadCountFactor) {
        this.overheadCountFactor = overheadCountFactor;
    }


    /**
     * Returns the overhead reset factor used for RST frame tracking.
     *
     * @return the overhead reset factor
     */
    public int getOverheadResetFactor() {
        return overheadResetFactor;
    }


    /**
     * Sets the overhead reset factor used for RST frame tracking.
     *
     * @param overheadResetFactor the overhead reset factor
     */
    public void setOverheadResetFactor(int overheadResetFactor) {
        this.overheadResetFactor = Math.max(overheadResetFactor, 0);
    }


    /**
     * Returns the payload size threshold for CONTINUATION frame overhead tracking.
     *
     * @return the continuation threshold
     */
    public int getOverheadContinuationThreshold() {
        return overheadContinuationThreshold;
    }


    /**
     * Sets the payload size threshold for CONTINUATION frame overhead tracking.
     *
     * @param overheadContinuationThreshold the continuation threshold
     */
    public void setOverheadContinuationThreshold(int overheadContinuationThreshold) {
        this.overheadContinuationThreshold = overheadContinuationThreshold;
    }


    /**
     * Returns the payload size threshold for DATA frame overhead tracking.
     *
     * @return the data threshold
     */
    public int getOverheadDataThreshold() {
        return overheadDataThreshold;
    }


    /**
     * Sets the payload size threshold for DATA frame overhead tracking.
     *
     * @param overheadDataThreshold the data threshold
     */
    public void setOverheadDataThreshold(int overheadDataThreshold) {
        this.overheadDataThreshold = overheadDataThreshold;
    }


    /**
     * Returns the payload size threshold for WINDOW_UPDATE frame overhead tracking.
     *
     * @return the window update threshold
     */
    public int getOverheadWindowUpdateThreshold() {
        return overheadWindowUpdateThreshold;
    }


    /**
     * Sets the payload size threshold for WINDOW_UPDATE frame overhead tracking.
     *
     * @param overheadWindowUpdateThreshold the window update threshold
     */
    public void setOverheadWindowUpdateThreshold(int overheadWindowUpdateThreshold) {
        this.overheadWindowUpdateThreshold = overheadWindowUpdateThreshold;
    }


    /**
     * Disables or enables the periodic PING frames used to keep the connection alive.
     *
     * @param initiatePingDisabled {@code true} to disable periodic PING frames
     */
    public void setInitiatePingDisabled(boolean initiatePingDisabled) {
        this.initiatePingDisabled = initiatePingDisabled;
    }


    /**
     * Returns whether periodic PING frames are disabled.
     *
     * @return {@code true} if periodic PING frames are disabled
     */
    public boolean getInitiatePingDisabled() {
        return initiatePingDisabled;
    }


    /**
     * Determines whether compression should be used for the given request/response pair.
     *
     * @param request  The request
     * @param response The response
     *
     * @return {@code true} if compression should be used
     */
    public boolean useCompression(Request request, Response response) {
        return http11Protocol.useCompression(request, response);
    }


    /**
     * Returns the timing for 100-continue responses.
     *
     * @return the continue response timing
     */
    public ContinueResponseTiming getContinueResponseTimingInternal() {
        return http11Protocol.getContinueResponseTimingInternal();
    }


    /**
     * Returns the parent HTTP/1.1 protocol handler.
     *
     * @return the HTTP/1.1 protocol handler
     */
    public AbstractHttp11Protocol<?> getHttp11Protocol() {
        return this.http11Protocol;
    }


    @Override
    public void setHttp11Protocol(AbstractHttp11Protocol<?> http11Protocol) {
        this.http11Protocol = http11Protocol;
        recycledRequestsAndResponses.setLimit(http11Protocol.getMaxConnections());

        try {
            ObjectName oname = this.http11Protocol.getONameForUpgrade(getUpgradeProtocolName());
            // This can be null when running the testsuite
            if (oname != null) {
                Registry.getRegistry(null).registerComponent(global, oname, null);
            }
        } catch (Exception e) {
            log.warn(sm.getString("http2Protocol.jmxRegistration.fail"), e);
        }
    }


    /**
     * Returns the name of the upgrade protocol (h2 for SSL, h2c for plain).
     *
     * @return the upgrade protocol name
     */
    public String getUpgradeProtocolName() {
        if (http11Protocol.isSSLEnabled()) {
            return ALPN_NAME;
        } else {
            return HTTP_UPGRADE_NAME;
        }
    }


    /**
     * Returns the global request group info for JMX statistics.
     *
     * @return the global request group info
     */
    public RequestGroupInfo getGlobal() {
        return global;
    }


    /**
     * Returns whether requests and responses are discarded after processing.
     *
     * @return {@code true} if requests and responses are discarded
     */
    public boolean getDiscardRequestsAndResponses() {
        return discardRequestsAndResponses;
    }


    /**
     * Sets whether requests and responses should be discarded after processing.
     *
     * @param discardRequestsAndResponses {@code true} to discard requests and responses
     */
    public void setDiscardRequestsAndResponses(boolean discardRequestsAndResponses) {
        this.discardRequestsAndResponses = discardRequestsAndResponses;
    }


    /**
     * Returns the drain timeout in nanoseconds.
     *
     * @return the drain timeout
     */
    public long getDrainTimeout() {
        return drainTimeout;
    }


    /**
     * Sets the drain timeout in nanoseconds.
     *
     * @param drainTimeout the drain timeout
     */
    public void setDrainTimeout(long drainTimeout) {
        this.drainTimeout = drainTimeout;
    }


    Request popRequestAndResponse() {
        Request requestAndResponse = null;
        if (!discardRequestsAndResponses) {
            requestAndResponse = recycledRequestsAndResponses.pop();
        }
        if (requestAndResponse == null) {
            requestAndResponse = new Request();
            Response response = new Response();
            requestAndResponse.setResponse(response);
        }
        return requestAndResponse;
    }


    void pushRequestAndResponse(Request requestAndResponse) {
        if (!discardRequestsAndResponses) {
            recycledRequestsAndResponses.push(requestAndResponse);
        }
    }
}
