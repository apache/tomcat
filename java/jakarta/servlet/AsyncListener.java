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
import java.util.EventListener;

/**
 * Listener for events associated with an {@link AsyncContext}.
 *
 * @since Servlet 3.0
 */
public interface AsyncListener extends EventListener {

    /**
     * This event is fired after the call to {@link AsyncContext#complete()}
     * has been processed by the container.
     *
     * @param event Provides access to the objects associated with the event
     *
     * @throws IOException Should be thrown if an I/O error occurs during the
     *                     processing of the event
     */
    void onComplete(AsyncEvent event) throws IOException;

    /**
     * This event is fired if an asynchronous operation times out but before
     * the container takes any action as a result of the timeout.
     *
     * @param event Provides access to the objects associated with the event
     *
     * @throws IOException Should be thrown if an I/O error occurs during the
     *                     processing of the event
     */
    void onTimeout(AsyncEvent event) throws IOException;

    /**
     * This event is fired if an error occurs during an asynchronous operation
     * but before the container takes any action as a result of the error.
     *
     * @param event Provides access to the objects associated with the event
     *
     * @throws IOException Should be thrown if an I/O error occurs during the
     *                     processing of the event
     */
    void onError(AsyncEvent event) throws IOException;

    /**
     * This event is fired if new call is made to
     * {@link ServletRequest#startAsync()} after the completion of the
     * {@link AsyncContext} to which this listener was added.
     *
     * @param event Provides access to the objects associated with the event
     *
     * @throws IOException Should be thrown if an I/O error occurs during the
     *                     processing of the event
     */
    void onStartAsync(AsyncEvent event) throws IOException;
}
