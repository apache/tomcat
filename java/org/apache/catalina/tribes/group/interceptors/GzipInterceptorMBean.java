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
package org.apache.catalina.tribes.group.interceptors;

/**
 * MBean interface for managing a GzipInterceptor.
 */
public interface GzipInterceptorMBean {

    // Config
    /**
     * Returns the option flag used by this interceptor.
     *
     * @return The option flag value
     */
    int getOptionFlag();

    /**
     * Sets the option flag used by this interceptor.
     *
     * @param optionFlag The new option flag value
     */
    void setOptionFlag(int optionFlag);

    /**
     * Returns the minimum payload size for compression to be enabled.
     *
     * @return The minimum payload size in bytes
     */
    int getCompressionMinSize();

    /**
     * Set the minimum payload size for compression to be enabled. A value of zero or less means compression will always
     * be used. If not explicitly configured, a default of zero will be used.
     *
     * @param compressionMinSize The new minimum payload size
     */
    void setCompressionMinSize(int compressionMinSize);

    /**
     * Returns whether the interceptor is configured to collect statistics.
     *
     * @return {@code true} if statistics collection is enabled, otherwise {@code false}
     */
    boolean getStatsEnabled();

    /**
     * Configure whether the interceptor collects statistics.
     *
     * @param statsEnabled {@code true} to enable statistics collections, otherwise {@code false}
     */
    void setStatsEnabled(boolean statsEnabled);

    /**
     * Returns the number of messages between statistics reports being written to the log.
     *
     * @return The interval between statistics reports
     */
    int getInterval();

    /**
     * If statistics collection is enabled, set the number of messages between statistics reports being written to the
     * log. A value of zero or less means no statistics reports are written.
     *
     * @param interval The new interval between reports
     */
    void setInterval(int interval);

    // Stats
    /**
     * Returns the total message count.
     *
     * @return Total number of messages processed
     */
    int getCount();

    /**
     * Returns the count of compressed transmitted messages.
     *
     * @return Number of compressed TX messages
     */
    int getCountCompressedTX();

    /**
     * Returns the count of uncompressed transmitted messages.
     *
     * @return Number of uncompressed TX messages
     */
    int getCountUncompressedTX();

    /**
     * Returns the count of compressed received messages.
     *
     * @return Number of compressed RX messages
     */
    int getCountCompressedRX();

    /**
     * Returns the count of uncompressed received messages.
     *
     * @return Number of uncompressed RX messages
     */
    int getCountUncompressedRX();

    /**
     * Returns the total transmitted data size in bytes.
     *
     * @return Total TX size in bytes
     */
    long getSizeTX();

    /**
     * Returns the total compressed transmitted data size in bytes.
     *
     * @return Total compressed TX size in bytes
     */
    long getCompressedSizeTX();

    /**
     * Returns the total uncompressed transmitted data size in bytes.
     *
     * @return Total uncompressed TX size in bytes
     */
    long getUncompressedSizeTX();

    /**
     * Returns the total received data size in bytes.
     *
     * @return Total RX size in bytes
     */
    long getSizeRX();

    /**
     * Returns the total compressed received data size in bytes.
     *
     * @return Total compressed RX size in bytes
     */
    long getCompressedSizeRX();

    /**
     * Returns the total uncompressed received data size in bytes.
     *
     * @return Total uncompressed RX size in bytes
     */
    long getUncompressedSizeRX();

    /**
     * Resets all statistics counters to zero.
     */
    void reset();

    /**
     * Writes the current statistics report to the log.
     */
    void report();
}
