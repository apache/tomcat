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
package jakarta.annotation.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the security roles that are allowed to access the annotated enterprise
 * bean or business method. When applied at the class level, only the listed roles
 * may invoke any business method of the bean. When applied at the method level,
 * only the listed roles may invoke the specified method. This annotation is
 * mutually exclusive with {@link PermitAll} and {@link DenyAll}.
 *
 * @since Common Annotations 1.0
 */
@Documented
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RolesAllowed {

    /**
     * Specifies the security role names that are authorized to access the
     * annotated component or method. Only callers assigned one of these roles
     * will be permitted access.
     *
     * @return the array of authorized role names
     */
    String[] value();
}
