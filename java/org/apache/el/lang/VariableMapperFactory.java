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
package org.apache.el.lang;

import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

import org.apache.el.util.MessageFactory;

/**
 * Factory for creating variable mappers that track variable resolutions.
 */
public class VariableMapperFactory extends VariableMapper {

    /**
     * The target variable mapper to delegate to.
     */
    private final VariableMapper target;
    /**
     * The momento mapper that tracks resolved variables.
     */
    private VariableMapper momento;

    /**
     * Constructs a new VariableMapperFactory.
     *
     * @param target The target variable mapper to delegate to
     *
     * @throws NullPointerException if target is null
     */
    public VariableMapperFactory(VariableMapper target) {
        if (target == null) {
            throw new NullPointerException(MessageFactory.get("error.noVariableMapperTarget"));
        }
        this.target = target;
    }

    /**
     * Returns the momento mapper containing all resolved variables.
     *
     * @return the momento mapper, or {@code null} if no variables have been resolved
     */
    public VariableMapper create() {
        return this.momento;
    }

    @Override
    public ValueExpression resolveVariable(String variable) {
        ValueExpression expr = this.target.resolveVariable(variable);
        if (expr != null) {
            if (this.momento == null) {
                this.momento = new VariableMapperImpl();
            }
            this.momento.setVariable(variable, expr);
        }
        return expr;
    }

    @Override
    public ValueExpression setVariable(String variable, ValueExpression expression) {
        throw new UnsupportedOperationException(MessageFactory.get("error.cannotSetVariables"));
    }
}
