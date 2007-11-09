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
package org.apache.jasper.el;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

/**
 * Implementation of ELContext
 * 
 * @author Jacob Hookom
 */
public final class ELContextImpl extends ELContext {

    private final static FunctionMapper NullFunctionMapper = new FunctionMapper() {
        public Method resolveFunction(String prefix, String localName) {
            return null;
        }
    };

    private final static class VariableMapperImpl extends VariableMapper {

        private Map<String, ValueExpression> vars;

        public ValueExpression resolveVariable(String variable) {
            if (vars == null) {
                return null;
            }
            return vars.get(variable);
        }

        public ValueExpression setVariable(String variable,
                ValueExpression expression) {
            if (vars == null)
                vars = new HashMap<String, ValueExpression>();
            return vars.put(variable, expression);
        }

    }

    private final ELResolver resolver;

    private FunctionMapper functionMapper = NullFunctionMapper; // immutable

    private VariableMapper variableMapper;

    public ELContextImpl() {
        this(ELResolverImpl.DefaultResolver);
    }

    public ELContextImpl(ELResolver resolver) {
        this.resolver = resolver;
    }

    public ELResolver getELResolver() {
        return this.resolver;
    }

    public FunctionMapper getFunctionMapper() {
        return this.functionMapper;
    }

    public VariableMapper getVariableMapper() {
        if (this.variableMapper == null) {
            this.variableMapper = new VariableMapperImpl();
        }
        return this.variableMapper;
    }

    public void setFunctionMapper(FunctionMapper functionMapper) {
        this.functionMapper = functionMapper;
    }

    public void setVariableMapper(VariableMapper variableMapper) {
        this.variableMapper = variableMapper;
    }

}
