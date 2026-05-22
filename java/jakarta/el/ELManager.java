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

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Provides a simplified API for managing EL context, resolvers, functions, and variables. This class maintains a
 * {@link StandardELContext} and provides convenience methods for adding resolvers, mapping functions, setting
 * variables, and managing imports. It is used internally by {@link ELProcessor}.
 *
 * @since EL 3.0
 */
public class ELManager {

    private StandardELContext context = null;

    /**
     * Constructs an ELManager with a lazily initialized {@link StandardELContext}.
     */
    public ELManager() {
    }

    /**
     * Returns the {@link ExpressionFactory} used to create and evaluate EL expressions.
     * The factory is obtained through the standard service discovery mechanism.
     *
     * @return the ExpressionFactory instance
     */
    public static ExpressionFactory getExpressionFactory() {
        return Util.getExpressionFactory();
    }

    /**
     * Returns the {@link StandardELContext} managed by this ELManager, creating it on first access
     * if it does not already exist.
     *
     * @return the StandardELContext instance
     */
    public StandardELContext getELContext() {
        if (context == null) {
            context = new StandardELContext(getExpressionFactory());
        }

        return context;
    }

    /**
     * Replaces the current EL context with a new {@link StandardELContext} initialized from the
     * given context. The new context copies the ELResolver chain, FunctionMapper, VariableMapper,
     * and other settings from the provided context.
     *
     * @param context the ELContext from which to initialize the new context
     *
     * @return the previous StandardELContext, or {@code null} if none existed
     */
    public ELContext setELContext(ELContext context) {
        StandardELContext oldContext = this.context;
        this.context = new StandardELContext(context);
        return oldContext;
    }

    /**
     * Adds a {@link BeanNameELResolver} to the resolver chain that uses the given
     * {@link BeanNameResolver} to resolve top-level identifiers to managed beans.
     *
     * @param beanNameResolver the resolver for bean names
     */
    public void addBeanNameResolver(BeanNameResolver beanNameResolver) {
        getELContext().addELResolver(new BeanNameELResolver(beanNameResolver));
    }

    /**
     * Adds an {@link ELResolver} to the front of the resolver chain in the managed EL context.
     * Resolvers added first have higher priority during property resolution.
     *
     * @param resolver the ELResolver to add
     */
    public void addELResolver(ELResolver resolver) {
        getELContext().addELResolver(resolver);
    }

    /**
     * Maps a static method to an EL function name with the given prefix and local name.
     * The function can then be invoked in EL expressions as {@code prefix:function(args)}.
     *
     * @param prefix   the namespace prefix for the function
     * @param function the local function name
     * @param method   the static {@link java.lang.reflect.Method} to map
     */
    public void mapFunction(String prefix, String function, Method method) {
        getELContext().getFunctionMapper().mapFunction(prefix, function, method);
    }

    /**
     * Registers a variable in the EL context's variable mapper. The variable can then be
     * referenced by name in EL expressions. Passing a {@code null} expression removes
     * the variable.
     *
     * @param variable   the variable name
     * @param expression the ValueExpression associated with the variable, or {@code null} to remove
     */
    public void setVariable(String variable, ValueExpression expression) {
        getELContext().getVariableMapper().setVariable(variable, expression);
    }

    /**
     * Imports a static field or method so it can be referenced by its simple name in EL
     * expressions. The argument must be a fully qualified name in the form
     * {@code className.staticMemberName}.
     *
     * @param staticMemberName the fully qualified name of the static member to import
     *
     * @throws ELException if the static member cannot be found or is ambiguous
     */
    public void importStatic(String staticMemberName) throws ELException {
        getELContext().getImportHandler().importStatic(staticMemberName);
    }

    /**
     * Imports a class so it can be referenced by its simple name in EL expressions.
     * The argument must be a fully qualified class name.
     *
     * @param className the fully qualified class name to import
     *
     * @throws ELException if the class name is invalid or conflicts with an existing import
     */
    public void importClass(String className) throws ELException {
        getELContext().getImportHandler().importClass(className);
    }

    /**
     * Imports all classes from a package so they can be referenced by their simple names
     * in EL expressions.
     *
     * @param packageName the package name to import
     */
    public void importPackage(String packageName) {
        getELContext().getImportHandler().importPackage(packageName);
    }

    /**
     * Defines or removes a bean accessible by name in EL expressions. When a non-null bean
     * is provided, it is stored under the given name. When {@code null} is provided, the
     * bean with the given name is removed.
     *
     * @param name  the bean name
     * @param bean  the bean object to define, or {@code null} to remove the bean
     *
     * @return the previous bean associated with the name, or {@code null} if there was none
     */
    public Object defineBean(String name, Object bean) {
        Map<String,Object> localBeans = getELContext().getLocalBeans();

        if (bean == null) {
            return localBeans.remove(name);
        } else {
            return localBeans.put(name, bean);
        }
    }

    /**
     * Registers an {@link EvaluationListener} with the managed EL context. The listener
     * will be notified before and after expression evaluation, and when properties are resolved.
     *
     * @param listener the EvaluationListener to register
     */
    public void addEvaluationListener(EvaluationListener listener) {
        getELContext().addEvaluationListener(listener);
    }
}
