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
package jakarta.websocket;

import java.util.List;
import java.util.Map;

/**
 * Provides configuration information for a WebSocket endpoint, including encoders, decoders,
 * and user properties.
 */
public interface EndpointConfig {

    /**
     * Returns the list of encoder classes for this endpoint.
     *
     * @return The list of encoder classes
     */
    List<Class<? extends Encoder>> getEncoders();

    /**
     * Returns the list of decoder classes for this endpoint.
     *
     * @return The list of decoder classes
     */
    List<Class<? extends Decoder>> getDecoders();

    /**
     * Returns a map of user properties associated with this endpoint configuration.
     *
     * @return The user properties map
     */
    Map<String,Object> getUserProperties();
}
