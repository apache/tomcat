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
import java.io.Serializable;

/**
 * Base class for EL expression objects. An Expression represents a compiled EL expression that can be evaluated.
 * Subclasses include {@link ValueExpression} and {@link MethodExpression}.
 */
public abstract class Expression implements Serializable {

    @Serial
    private static final long serialVersionUID = -6663767980471823812L;

    /**
     * Constructs an Expression. Subclasses should invoke this constructor to initialize
     * the base expression state.
     */
    public Expression() {
    }

    /**
     * Returns the original string representation of this EL expression as it was parsed.
     *
     * @return the string representation of this expression
     */
    public abstract String getExpressionString();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    /**
     * Returns whether this expression is a literal text expression, meaning it does not
     * contain any variables, functions, or operators and evaluates to a constant value.
     *
     * @return {@code true} if this expression is a literal text, {@code false} otherwise
     */
    public abstract boolean isLiteralText();

}
