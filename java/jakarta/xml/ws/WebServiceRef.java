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
package jakarta.xml.ws;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a reference to a web service. The WebServiceRef annotation can be applied to a field or method to inject
 * a reference to a web service, or to a class to declare web service dependencies.
 */
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface WebServiceRef {

    /**
     * The name for the web service reference. The name is relative to the default initial context.
     *
     * @return the name of the web service reference
     */
    String name() default "";

    /**
     * The type of the injected object, typically the service endpoint interface.
     *
     * @return the type class
     */
    @SuppressWarnings("rawtypes") // Can't use Class<?> because API needs to match specification
    Class type() default Object.class;

    /**
     * The web service class, typically the service endpoint interface class.
     *
     * @return the web service class
     */
    @SuppressWarnings("rawtypes") // Can't use Class<?> because API needs to match specification
    Class value() default Object.class;

    /**
     * The location of the WSDL document for the web service.
     *
     * @return the WSDL location
     */
    String wsdlLocation() default "";

    /**
     * A provider-specific name that this reference should be mapped to.
     *
     * @return the provider-specific mapped name
     */
    String mappedName() default "";
}
