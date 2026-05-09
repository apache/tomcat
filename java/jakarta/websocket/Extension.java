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
package jakarta.websocket;

import java.util.List;

/**
 * Represents a WebSocket extension that can be negotiated during the handshake.
 */
public interface Extension {

    /**
     * Returns the name of this extension.
     *
     * @return The extension name
     */
    String getName();

    /**
     * Returns the parameters associated with this extension.
     *
     * @return The list of extension parameters
     */
    List<Parameter> getParameters();

    /**
     * Represents a parameter of a WebSocket extension.
     */
    interface Parameter {

        /**
         * Returns the name of this parameter.
         *
         * @return The parameter name
         */
        String getName();

        /**
         * Returns the value of this parameter.
         *
         * @return The parameter value
         */
        String getValue();
    }
}
