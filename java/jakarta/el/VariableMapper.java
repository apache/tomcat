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

/**
 * Manages the mapping between variable names and their corresponding {@link ValueExpression} instances within an
 * {@link ELContext}. Variable mappings allow EL expressions to reference named variables that resolve to computed
 * values.
 */
public abstract class VariableMapper {

    /**
     * Default constructor for subclasses.
     */
    public VariableMapper() {
    }

    /**
     * Resolves a variable name to its corresponding ValueExpression.
     *
     * @param variable the name of the variable to resolve
     *
     * @return the ValueExpression for the variable, or {@code null} if not found
     */
    public abstract ValueExpression resolveVariable(String variable);

    /**
     * Sets or removes a variable mapping.
     *
     * @param variable   the name of the variable
     * @param expression the ValueExpression to associate with the variable, or {@code null} to remove the mapping
     *
     * @return the previous ValueExpression for the variable, or {@code null} if there was no mapping
     */
    public abstract ValueExpression setVariable(String variable, ValueExpression expression);
}
