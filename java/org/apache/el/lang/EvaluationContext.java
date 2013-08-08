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

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.EvaluationListener;
import javax.el.FunctionMapper;
import javax.el.ImportHandler;
import javax.el.VariableMapper;

public final class EvaluationContext extends ELContext {

    private final ELContext elContext;

    private final FunctionMapper fnMapper;

    private final VariableMapper varMapper;

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
    // Can't use Class<?> because API needs to match specification in superclass
    public Object getContext(@SuppressWarnings("rawtypes") Class key) {
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
    // Can't use Class<?> because API needs to match specification in superclass
    public void putContext(@SuppressWarnings("rawtypes") Class key,
            Object contextObject) {
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
    public Object convertToType(Object obj, Class<?> type) {
        return elContext.convertToType(obj, type);
    }
}
