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
package org.apache.tomcat.bayeux;

import org.apache.tomcat.bayeux.HttpError;

/**
 * An interface that defines methods for managing Bayeux request meta
 * messages.
 *
 * @author Guy A. Molinari
 * @author Filip Hanik
 * @version 0.9
 */
public interface BayeuxRequest {

    public static final String LAST_REQ_ATTR = "org.apache.cometd.bayeux.last_request";
    public static final String CURRENT_REQ_ATTR = "org.apache.cometd.bayeux.current_request";
    public static final String JSON_MSG_ARRAY = "org.apache.cometd.bayeux.json_msg_array";

    /**
     * Validates a specific request.
     * This method must be called prior to process()
     * as a request can do pre processing in the validate method.
     * <br/>
     * Should the validation fail, an error object is returned
     * containing an error message, and potentially a stack trace
     * if an exception was generated
     * @return HttpError - null if no error was detected, an HttpError object containing information about the error.
     */
    public HttpError validate();

    /**
     * processes a remote client Bayeux message
     * @param prevops - the operation requested by the previous request, in case of chained requests.
     * @return int - returns the interest operation for a CometEvent. Currently not used
     * @throws BayeuxException - if an error was detected, and the appropriate error response couldn't be delivered to the client.
     */
    public int process(int prevops) throws BayeuxException;
}
