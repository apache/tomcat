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
 * @since Common Annotations 1.0
 */
@Documented
@Target({ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
    ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD,
    ElementType.PACKAGE, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface Generated {
    /**
     * @return The name of the code generator. It is recommended that the fully
     *         qualified name of the code generator is used.
     */
    public String[] value();
    /**
     * @return The date the code was generated
     */
    public String date() default "";
    /**
     * @return Additional comments (if any) related to the code generation
     */
    public String comments() default "";
}
