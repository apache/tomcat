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
     * @return a String with the name of the resource
     */
    String name() default "";

    /**
     * Uses generics since Common Annotations 1.2.
     *
     * @return The type for instances of this resource
     */
    Class<?> type() default Object.class;

    /**
     * @return the AuthenticationType of the resource default CONTAINER
     */
    AuthenticationType authenticationType() default AuthenticationType.CONTAINER;

    /**
     * @return true (default) if the resource is shareable, or false if not
     */
    boolean shareable() default true;

    /**
     * @return a string with the description for the resource
     */
    String description() default "";

    /**
     * @return a string with the mappedName of the resource
     */
    String mappedName() default "";

    /**
     * @since Common Annotations 1.1
     *
     * @return The name of the entry, if any, to use for this resource
     */
    String lookup() default "";
}
