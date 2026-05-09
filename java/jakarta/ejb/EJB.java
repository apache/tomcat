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
package jakarta.ejb;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a dependency on an enterprise bean. The EJB annotation can be applied to a field or method to inject
 * a reference to an enterprise bean, or to a class to declare bean dependencies for the entire bean.
 */
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface EJB {

    /**
     * The name element for the annotation. The name of an enterprise bean is relative to the javax.np.Context of
     * the bean. The name must be unique within the bean.
     *
     * @return the name of the enterprise bean
     */
    String name() default "";

    /**
     * A description of the enterprise bean reference.
     *
     * @return the description
     */
    String description() default "";

    /**
     * The interface of the enterprise bean.
     *
     * @return the bean interface class
     */
    @SuppressWarnings("rawtypes") // Can't use Class<?> because API needs to match specification
    Class beanInterface() default Object.class;

    /**
     * The name of the enterprise bean. The beanName is relative to the javax.naming.Context of the bean that
     * owns this reference.
     *
     * @return the name of the enterprise bean
     */
    String beanName() default "";

    /**
     * A provider-specific name that this reference should be mapped to. The entire name is provider-specific.
     *
     * @return the provider-specific mapped name
     */
    String mappedName() default "";

    /**
     * A JNDI name used to locate the enterprise bean. The lookup element overrides the default JNDI lookup name.
     *
     * @return the JNDI lookup string
     */
    String lookup() default "";
}
