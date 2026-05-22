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
import java.io.Serializable;

/**
 * Holds a reference to a resolved property, consisting of a base object and a property identifier. This class is
 * used to capture the result of property resolution during EL evaluation, allowing the caller to perform additional
 * operations on the resolved property.
 *
 * @since EL 2.2
 */
public class ValueReference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The base object on which the property was resolved. */
    private final Object base;

    /** The property identifier. */
    private final Object property;

    /**
     * Constructs a new value reference with the given base object and property.
     *
     * @param base     The base object on which the property was resolved
     * @param property The property identifier
     */
    public ValueReference(Object base, Object property) {
        this.base = base;
        this.property = property;
    }

    /**
     * Returns the base object on which the property was resolved.
     *
     * @return The base object
     */
    public Object getBase() {
        return base;
    }

    /**
     * Returns the property identifier.
     *
     * @return The property identifier
     */
    public Object getProperty() {
        return property;
    }
}
