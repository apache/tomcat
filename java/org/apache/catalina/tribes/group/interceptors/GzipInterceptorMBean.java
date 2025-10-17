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

public interface GzipInterceptorMBean {

    // Config
    int getOptionFlag();

    void setOptionFlag(int optionFlag);

    /**
     * @return the minimum payload size for compression to be enabled.
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
     * @return {@code true} if the interceptor is configured to collect statistics, otherwise {@code false}
     */
    boolean getStatsEnabled();

    /**
     * Configure whether the interceptor collects statistics.
     *
     * @param statsEnabled {@code true} to enable statistics collections, otherwise {@code false}
     */
    void setStatsEnabled(boolean statsEnabled);

    /**
     * @return If statistics collection is enabled, the number of messages between statistics reports being written to
     *             the log.
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
    int getCount();

    int getCountCompressedTX();

    int getCountUncompressedTX();

    int getCountCompressedRX();

    int getCountUncompressedRX();

    long getSizeTX();

    long getCompressedSizeTX();

    long getUncompressedSizeTX();

    long getSizeRX();

    long getCompressedSizeRX();

    long getUncompressedSizeRX();

    void reset();

    void report();
}
