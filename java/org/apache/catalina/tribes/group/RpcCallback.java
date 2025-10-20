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
package org.apache.catalina.tribes.group;

import java.io.Serializable;

import org.apache.catalina.tribes.Member;

/**
 * The RpcCallback interface is an interface for the Tribes channel to request a response object to a request that came
 * in.
 */
public interface RpcCallback {

    /**
     * Allows sending a response to a received message.
     *
     * @param msg    The message
     * @param sender Member
     *
     * @return Serializable object, <code>null</code> if no reply should be sent
     */
    Serializable replyRequest(Serializable msg, Member sender);

    /**
     * If the reply has already been sent to the requesting thread, the rpc callback can handle any data that comes in
     * after the fact.
     *
     * @param msg    The message
     * @param sender Member
     */
    void leftOver(Serializable msg, Member sender);

}