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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to identify generated code.
 *
 * @since Common Annotations 1.0
 */
@Documented
@Target({ ElementType.PACKAGE, ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD,
        ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.PARAMETER })
@Retention(RetentionPolicy.SOURCE)
public @interface Generated {
    /**
     * Specifies the name(s) of the tool(s) that generated the annotated element.
     * It is recommended that the fully qualified name of the code generator is used.
     *
     * @return the array of code generator names
     */
    String[] value();

    /**
     * Specifies the date and time when the code was generated, typically in
     * ISO 8601 format. An empty string indicates the date is not specified.
     *
     * @return the code generation date, or an empty string if not specified
     */
    String date() default "";

    /**
     * Provides additional comments or metadata related to the code generation
     * process, such as version information or configuration details.
     *
     * @return additional comments about the code generation, or an empty string if none
     */
    String comments() default "";
}
