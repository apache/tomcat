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

/**
 * Represents a parsed EL expression that can be evaluated to produce a value.
 * <p>
 * A value expression is created by parsing an EL expression string and can be
 * evaluated multiple times in different contexts. It supports reading values,
 * writing values, and introspecting the expression's type and mutability.
 *
 * @since EL 2.1
 */
public abstract class ValueExpression extends Expression {

    @Serial
    private static final long serialVersionUID = 8577809572381654673L;

    /**
     * Constructs a new value expression.
     */
    public ValueExpression() {
    }

    /**
     * Evaluates this expression and returns the result.
     *
     * @param <T>     The expected type for the result of evaluating this value expression
     * @param context The EL context for this evaluation
     *
     * @return The result of evaluating this value expression
     *
     * @throws NullPointerException      If the supplied context is <code>null</code>
     * @throws PropertyNotFoundException If a property/variable resolution failed because no match was found or a match
     *                                       was found but was not readable
     * @throws ELException               Wraps any exception throw whilst resolving a property or variable
     */
    public abstract <T> T getValue(ELContext context);

    /**
     * Sets the value of the property referenced by this expression.
     *
     * @param context The EL context for this evaluation
     * @param value   The value to set the property to which this value expression refers
     *
     * @throws NullPointerException         If the supplied context is <code>null</code>
     * @throws PropertyNotFoundException    If a property/variable resolution failed because no match was found
     * @throws PropertyNotWritableException If a property/variable resolution failed because a match was found but was
     *                                          not writable
     * @throws ELException                  Wraps any exception throw whilst resolving a property or variable
     */
    public abstract void setValue(ELContext context, Object value);

    /**
     * Determines whether the property referenced by this expression is read-only.
     *
     * @param context The EL context for this evaluation
     *
     * @return <code>true</code> if this expression is read only otherwise <code>false</code>
     *
     * @throws NullPointerException      If the supplied context is <code>null</code>
     * @throws PropertyNotFoundException If a property/variable resolution failed because no match was found or a match
     *                                       was found but was not readable
     * @throws ELException               Wraps any exception throw whilst resolving a property or variable
     */
    public abstract boolean isReadOnly(ELContext context);

    /**
     * Returns the type of the result produced by evaluating this expression.
     *
     * @param context The EL context for this evaluation
     *
     * @return The type of the result of this value expression
     *
     * @throws NullPointerException      If the supplied context is <code>null</code>
     * @throws PropertyNotFoundException If a property/variable resolution failed because no match was found or a match
     *                                       was found but was not readable
     * @throws ELException               Wraps any exception throw whilst resolving a property or variable
     */
    public abstract Class<?> getType(ELContext context);

    /**
     * Returns the expected type of the result of this expression.
     *
     * @return The expected result type, or {@code Object.class} if no specific type is expected
     */
    public abstract Class<?> getExpectedType();

    /**
     * Returns a reference to the resolved property for this expression.
     * <p>
     * This default implementation always returns {@code null}. Subclasses should
     * override this method to provide the resolved base object and property.
     *
     * @param context The EL context for this evaluation
     *
     * @return This default implementation always returns <code>null</code>
     *
     * @since EL 2.2
     */
    public ValueReference getValueReference(ELContext context) {
        // Expected to be over-ridden by implementation
        context.notifyBeforeEvaluation(getExpressionString());
        context.notifyAfterEvaluation(getExpressionString());
        return null;
    }
}
