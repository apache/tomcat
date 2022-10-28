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

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.EvaluationListener;
import jakarta.el.FunctionMapper;
import jakarta.el.ImportHandler;
import jakarta.el.VariableMapper;

import org.apache.el.util.MessageFactory;

public final class EvaluationContext extends ELContext {

    private final ELContext elContext;

    private final FunctionMapper fnMapper;

    private final VariableMapper varMapper;

    private LambdaExpressionNestedState lambdaExpressionNestedState;

    public EvaluationContext(ELContext elContext, FunctionMapper fnMapper,
            VariableMapper varMapper) {
        this.elContext = elContext;
        this.fnMapper = fnMapper;
        this.varMapper = varMapper;
    }

    public ELContext getELContext() {
        return elContext;
    }

    @Override
    public FunctionMapper getFunctionMapper() {
        return fnMapper;
    }

    @Override
    public VariableMapper getVariableMapper() {
        return varMapper;
    }

    @Override
    public Object getContext(Class<?> key) {
        return elContext.getContext(key);
    }

    @Override
    public ELResolver getELResolver() {
        return elContext.getELResolver();
    }

    @Override
    public boolean isPropertyResolved() {
        return elContext.isPropertyResolved();
    }

    @Override
    public void putContext(Class<?> key, Object contextObject) {
        elContext.putContext(key, contextObject);
    }

    @Override
    public void setPropertyResolved(boolean resolved) {
        elContext.setPropertyResolved(resolved);
    }

    @Override
    public Locale getLocale() {
        return elContext.getLocale();
        }

    @Override
    public void setLocale(Locale locale) {
        elContext.setLocale(locale);
    }

    @Override
    public void setPropertyResolved(Object base, Object property) {
        elContext.setPropertyResolved(base, property);
    }

    @Override
    public ImportHandler getImportHandler() {
        return elContext.getImportHandler();
    }

    @Override
    public void addEvaluationListener(EvaluationListener listener) {
        elContext.addEvaluationListener(listener);
    }

    @Override
    public List<EvaluationListener> getEvaluationListeners() {
        return elContext.getEvaluationListeners();
    }

    @Override
    public void notifyBeforeEvaluation(String expression) {
        elContext.notifyBeforeEvaluation(expression);
    }

    @Override
    public void notifyAfterEvaluation(String expression) {
        elContext.notifyAfterEvaluation(expression);
    }

    @Override
    public void notifyPropertyResolved(Object base, Object property) {
        elContext.notifyPropertyResolved(base, property);
    }

    @Override
    public boolean isLambdaArgument(String name) {
        return elContext.isLambdaArgument(name);
    }

    @Override
    public Object getLambdaArgument(String name) {
        return elContext.getLambdaArgument(name);
    }

    @Override
    public void enterLambdaScope(Map<String, Object> arguments) {
        elContext.enterLambdaScope(arguments);
    }

    @Override
    public void exitLambdaScope() {
        elContext.exitLambdaScope();
    }

    @Override
    public <T> T convertToType(Object obj, Class<T> type) {
        return elContext.convertToType(obj, type);
    }


    public LambdaExpressionNestedState getLambdaExpressionNestedState() {
        // State is stored in the EvaluationContext instance associated with the
        // outermost lambda expression of a set of nested expressions.

        if (lambdaExpressionNestedState != null) {
            // This instance is storing state so it must be associated with the
            // outermost lambda expression.
            return lambdaExpressionNestedState;
        }

        // Check to see if the associated lambda expression is nested as state
        // will be stored in the EvaluationContext associated with the outermost
        // lambda expression.
        if (elContext instanceof EvaluationContext) {
            return ((EvaluationContext) elContext).getLambdaExpressionNestedState();
        }

        return null;
    }


    public void setLambdaExpressionNestedState(LambdaExpressionNestedState lambdaExpressionNestedState) {
      if (this.lambdaExpressionNestedState != null) {
          // Should never happen
          throw new IllegalStateException(MessageFactory.get("error.lambda.wrongNestedState"));
      }

        this.lambdaExpressionNestedState = lambdaExpressionNestedState;
    }
}
