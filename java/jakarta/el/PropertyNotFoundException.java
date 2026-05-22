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
package jakarta.el;

import java.io.Serial;

/**
 * Exception thrown when a property is not found during EL evaluation.
 * <p>
 * This exception is raised when an expression attempts to access a property
 * that does not exist on the target object.
 *
 * @since EL 2.1
 */
public class PropertyNotFoundException extends ELException {

    @Serial
    private static final long serialVersionUID = -3799200961303506745L;

    /**
     * Constructs a new instance with no detail message or cause.
     */
    public PropertyNotFoundException() {
        super();
    }

    /**
     * Constructs a new instance with the specified detail message and no cause.
     *
     * @param message the detail message
     */
    public PropertyNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new instance with the specified cause and no detail message.
     *
     * @param cause the underlying cause
     */
    public PropertyNotFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new instance with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public PropertyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
