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
package org.apache.jasper.optimizations;

import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.StringInterpreterFactory.DefaultStringInterpreter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Provides an optimised conversion of string values to Enums. It bypasses the check for registered PropertyEditor.
 */
public class StringInterpreterEnum extends DefaultStringInterpreter {

    // Can't be static
    private final Log log = LogFactory.getLog(ELInterpreterTagSetters.class);

    @Override
    protected String coerceToOtherType(Class<?> c, String s, boolean isNamedAttribute) {
        if (c.isEnum() && !isNamedAttribute) {
            try {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Enum<?> enumValue = Enum.valueOf((Class<? extends Enum>) c, s);
                return c.getName() + "." + enumValue.name();
            } catch (IllegalArgumentException iae) {
                log.debug(Localizer.getMessage("jsp.error.typeConversion", s, "Enum[" + c.getName() + "]"), iae);
                // Continue and resolve the value at runtime
            }
        }

        return null;
    }
}
