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
package javax.el;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class ELContext {

    private Locale locale;

    private Map<Class<?>, Object> map;

    private boolean resolved;

    private ImportHandler importHandler = null;

    private List<EvaluationListener> listeners = new ArrayList<>();

    private Deque<Map<String,Object>> lambdaArguments = new LinkedList<>();

    public ELContext() {
        this.resolved = false;
    }

    public void setPropertyResolved(boolean resolved) {
        this.resolved = resolved;
    }

    /**
     * @since EL 3.0
     */
    public void setPropertyResolved(Object base, Object property) {
        setPropertyResolved(true);
        notifyPropertyResolved(base, property);
    }

    public boolean isPropertyResolved() {
        return this.resolved;
    }

    // Can't use Class<?> because API needs to match specification
    /**
     * @throws NullPointerException
     *              If the supplied key or context is <code>null</code>
     */
    public void putContext(@SuppressWarnings("rawtypes") Class key,
            Object contextObject) {
        if (key == null || contextObject == null) {
            throw new NullPointerException();
        }

        if (this.map == null) {
            this.map = new HashMap<>();
        }

        this.map.put(key, contextObject);
    }

    // Can't use Class<?> because API needs to match specification
    /**
     * @throws NullPointerException
     *              If the supplied key is <code>null</code>
     */
    public Object getContext(@SuppressWarnings("rawtypes") Class key) {
        if (key == null) {
            throw new NullPointerException();
        }
        if (this.map == null) {
            return null;
        }
        return this.map.get(key);
    }

    public abstract ELResolver getELResolver();

    /**
     * @since EL 3.0
     */
    public ImportHandler getImportHandler() {
        if (importHandler == null) {
            importHandler = new ImportHandler();
        }
        return importHandler;
    }

    public abstract FunctionMapper getFunctionMapper();

    public Locale getLocale() {
        return this.locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public abstract VariableMapper getVariableMapper();

    /**
     * @since EL 3.0
     */
    public void addEvaluationListener(EvaluationListener listener) {
        listeners.add(listener);
    }

    /**
     * @since EL 3.0
     */
    public List<EvaluationListener> getEvaluationListeners() {
        return listeners;
    }

    /**
     * @since EL 3.0
     */
    public void notifyBeforeEvaluation(String expression) {
        for (EvaluationListener listener : listeners) {
            try {
                listener.beforeEvaluation(this, expression);
            } catch (Throwable t) {
                Util.handleThrowable(t);
                // Ignore - no option to log
            }
        }
    }

    /**
     * @since EL 3.0
     */
    public void notifyAfterEvaluation(String expression) {
        for (EvaluationListener listener : listeners) {
            try {
                listener.afterEvaluation(this, expression);
            } catch (Throwable t) {
                Util.handleThrowable(t);
                // Ignore - no option to log
            }
        }
    }

    /**
     * @since EL 3.0
     */
    public void notifyPropertyResolved(Object base, Object property) {
        for (EvaluationListener listener : listeners) {
            try {
                listener.propertyResolved(this, base, property);
            } catch (Throwable t) {
                Util.handleThrowable(t);
                // Ignore - no option to log
            }
        }
    }

    /**
     * @since EL 3.0
     */
    public boolean isLambdaArgument(String name) {
        for (Map<String,Object> arguments : lambdaArguments) {
            if (arguments.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @since EL 3.0
     */
    public Object getLambdaArgument(String name) {
        for (Map<String,Object> arguments : lambdaArguments) {
            Object result = arguments.get(name);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * @since EL 3.0
     */
    public void enterLambdaScope(Map<String,Object> arguments) {
        lambdaArguments.push(arguments);
    }

    /**
     * @since EL 3.0
     */
    public void exitLambdaScope() {
        lambdaArguments.pop();
    }

    /**
     * @since EL 3.0
     */
    public Object convertToType(Object obj, Class<?> type) {

        boolean originalResolved = isPropertyResolved();
        setPropertyResolved(false);
        try {
            ELResolver resolver = getELResolver();
            if (resolver != null) {
                Object result = resolver.convertToType(this, obj, type);
                if (isPropertyResolved()) {
                    return result;
                }
            }
        } finally {
            setPropertyResolved(originalResolved);
        }

        return ELManager.getExpressionFactory().coerceToType(obj, type);
    }
}
