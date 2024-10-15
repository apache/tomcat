/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet;

import java.io.IOException;

/**
 * Receives notification of write events when using non-blocking IO.
 *
 * @since Servlet 3.1
 */
public interface WriteListener extends java.util.EventListener {

    /**
     * Invoked when it it possible to write data without blocking. The container will invoke this method the first time
     * for a request as soon as data can be written. Subsequent invocations will only occur if a call to
     * {@link ServletOutputStream#isReady()} has returned false and it has since become possible to write data.
     *
     * @throws IOException if an I/O error occurs while processing this event
     */
    void onWritePossible() throws IOException;

    /**
     * Invoked if an error occurs while writing the response.
     *
     * @param throwable The throwable that represents the error that occurred
     */
    void onError(Throwable throwable);
}