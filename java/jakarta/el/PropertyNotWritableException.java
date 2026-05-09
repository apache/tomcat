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
 * Exception thrown when an EL expression attempts to write to a read-only property.
 * <p>
 * This exception is raised during setValue operations when the target property
 * exists but cannot be modified.
 *
 * @since EL 2.1
 */
public class PropertyNotWritableException extends ELException {

    @Serial
    private static final long serialVersionUID = 827987155471214717L;

    /**
     * Constructs a new instance with no detail message or cause.
     */
    public PropertyNotWritableException() {
        super();
    }

    /**
     * Constructs a new instance with the specified detail message and no cause.
     *
     * @param message the detail message
     */
    public PropertyNotWritableException(String message) {
        super(message);
    }

    /**
     * Constructs a new instance with the specified cause and no detail message.
     *
     * @param cause the underlying cause
     */
    public PropertyNotWritableException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new instance with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public PropertyNotWritableException(String message, Throwable cause) {
        super(message, cause);
    }
}
