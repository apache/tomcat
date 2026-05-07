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
 * MBean interface for managing the TcpFailureDetector interceptor.
 */
public interface TcpFailureDetectorMBean {

    /**
     * Returns the socket option flag used for member checks.
     * @return the option flag
     */
    int getOptionFlag();

    /**
     * Returns the connection timeout in milliseconds.
     * @return the connection timeout
     */
    long getConnectTimeout();

    /**
     * Returns whether send tests are performed.
     * @return true if send tests are enabled
     */
    boolean getPerformSendTest();

    /**
     * Returns whether read tests are performed.
     * @return true if read tests are enabled
     */
    boolean getPerformReadTest();

    /**
     * Returns the read test timeout in milliseconds.
     * @return the read test timeout
     */
    long getReadTestTimeout();

    /**
     * Returns the timeout for removing suspects.
     * @return the remove suspects timeout
     */
    int getRemoveSuspectsTimeout();

    /**
     * Sets whether read tests should be performed.
     * @param performReadTest true to enable read tests
     */
    void setPerformReadTest(boolean performReadTest);

    /**
     * Sets whether send tests should be performed.
     * @param performSendTest true to enable send tests
     */
    void setPerformSendTest(boolean performSendTest);

    /**
     * Sets the read test timeout in milliseconds.
     * @param readTestTimeout the timeout value
     */
    void setReadTestTimeout(long readTestTimeout);

    /**
     * Sets the connection timeout in milliseconds.
     * @param connectTimeout the timeout value
     */
    void setConnectTimeout(long connectTimeout);

    /**
     * Sets the timeout for removing suspects.
     * @param removeSuspectsTimeout the timeout value
     */
    void setRemoveSuspectsTimeout(int removeSuspectsTimeout);

    /**
     * Checks the status of cluster members.
     * @param checkAll true to check all members, false to check only suspects
     */
    void checkMembers(boolean checkAll);
}