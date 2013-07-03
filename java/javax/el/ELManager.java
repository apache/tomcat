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

import java.lang.reflect.Method;
import java.util.Map;

/**
 * @since EL 3.0
 */
public class ELManager {

    private StandardELContext context = null;

    public static ExpressionFactory getExpressionFactory() {
        return Util.getExpressionFactory();
    }

    public StandardELContext getELContext() {
        if (context == null) {
            context = new StandardELContext(getExpressionFactory());
        }

        return context;
    }

    public ELContext setELContext(ELContext context) {
        StandardELContext oldContext = this.context;
        this.context = new StandardELContext(context);
        return oldContext;
    }

    public void addBeanNameResolver(BeanNameResolver beanNameResolver) {
        getELContext().addELResolver(new BeanNameELResolver(beanNameResolver));
    }

    public void addELResolver(ELResolver resolver) {
        getELContext().addELResolver(resolver);
    }

    public void mapFunction(String prefix, String function, Method method) {
        getELContext().getFunctionMapper().mapFunction(
                prefix, function, method);
    }

    public void setVariable(String variable, ValueExpression expression) {
        getELContext().getVariableMapper().setVariable(variable, expression);
    }

    public void importStatic(String staticMemberName)
            throws javax.el.ELException {
        getELContext().getImportHandler().importStatic(staticMemberName);
    }

    public void importClass(String className) throws javax.el.ELException {
        getELContext().getImportHandler().importClass(className);
    }

    public void importPackage(String packageName) {
        getELContext().getImportHandler().importPackage(packageName);
    }

    public Object defineBean(String name, Object bean) {
        Map<String,Object> localBeans = getELContext().getLocalBeans();

        if (bean == null) {
            return localBeans.remove(name);
        } else {
            return localBeans.put(name, bean);
        }
    }

    public void addEvaluationListener(EvaluationListener listener) {
        getELContext().addEvaluationListener(listener);
    }
}
