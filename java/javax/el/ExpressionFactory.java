/*
 * Copyright 2006 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.el;

/**
 * 
 * @since 2.1
 */
public abstract class ExpressionFactory {

    public abstract Object coerceToType(Object obj, Class<?> expectedType)
            throws ELException;

    public abstract ValueExpression createValueExpression(ELContext context,
            String expression, Class<?> expectedType)
            throws NullPointerException, ELException;

    public abstract ValueExpression createValueExpression(Object instance,
            Class<?> expectedType);

    public abstract MethodExpression createMethodExpression(ELContext context,
            String expression, Class<?> expectedReturnType,
            Class<?>[] expectedParamTypes) throws ELException,
            NullPointerException;
}
