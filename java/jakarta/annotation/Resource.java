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
package jakarta.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a resource required by the application. Annotated classes will be used as resources. Annotated fields
 * and/or methods will have resources injected.
 *
 * @since Common Annotations 1.0
 */
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Resources.class)
public @interface Resource {

    /**
     * The AuthenticationType, either CONTAINER or APPLICATION
     */
    enum AuthenticationType {
        /**
         * Container authentication
         */
        CONTAINER,
        /**
         * Application authentication
         */
        APPLICATION
    }

    /**
     * Specifies the logical name of the resource. If not specified, the container
     * derives the name from the field or method to which the annotation is applied.
     *
     * @return the logical name of the resource
     */
    String name() default "";

    /**
     * Uses generics since Common Annotations 1.2.
     *
     * @return The type for instances of this resource
     */
    Class<?> type() default Object.class;

    /**
     * Specifies the authentication type for the resource. Container authentication
     * means the container handles authentication, while application authentication
     * means the application code is responsible for authentication.
     *
     * @return the authentication type, defaulting to {@link AuthenticationType#CONTAINER}
     */
    AuthenticationType authenticationType() default AuthenticationType.CONTAINER;

    /**
     * Indicates whether the resource can be shared among multiple injection points.
     * When true, the same resource instance may be injected into multiple components.
     * When false, each injection point receives a distinct resource instance.
     *
     * @return true if the resource is shareable across injection points, false otherwise
     */
    boolean shareable() default true;

    /**
     * Provides a human-readable description of the resource for documentation
     * and administrative purposes.
     *
     * @return the resource description
     */
    String description() default "";

    /**
     * Specifies a product-dependent name for the resource, allowing the container
     * to map the logical resource name to a physical resource in a vendor-specific
     * manner. This is typically used for environment-specific configuration.
     *
     * @return the product-specific mapped name for the resource
     */
    String mappedName() default "";

    /**
     * Specifies the JNDI lookup name for the resource. This attribute allows
     * explicit control over the JNDI name used to locate the resource, overriding
     * the default name derivation. The lookup name is typically prefixed with
     * "java:comp/env/" for component environment entries.
     *
     * @since Common Annotations 1.1
     *
     * @return the JNDI lookup name for the resource
     */
    String lookup() default "";
}
