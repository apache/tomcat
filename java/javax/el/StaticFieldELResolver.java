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

import java.beans.FeatureDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;

/**
 * @since EL 3.0
 */
public class StaticFieldELResolver extends ELResolver {

    @Override
    public Object getValue(ELContext context, Object base, Object property) {

        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof ELClass && property instanceof String) {
            context.setPropertyResolved(base, property);

            Class<?> clazz = ((ELClass) base).getKlass();
            String name = (String) property;
            Exception exception = null;
            try {
                Field field = clazz.getField(name);
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) &&
                        Modifier.isPublic(modifiers)) {
                    return field.get(null);
                }
            } catch (IllegalArgumentException | IllegalAccessException
                    | NoSuchFieldException | SecurityException e) {
                exception = e;
            }
            String msg = Util.message(context, "staticFieldELResolver.notFound",
                    name, clazz.getName());
            if (exception == null) {
                throw new PropertyNotFoundException(msg);
            } else {
                throw new PropertyNotFoundException(msg, exception);
            }
        }
        return null;
    }


    @Override
    public void setValue(ELContext context, Object base, Object property,
            Object value) {

        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof ELClass && property instanceof String) {
            Class<?> clazz = ((ELClass) base).getKlass();
            String name = (String) property;

            throw new PropertyNotWritableException(Util.message(context,
                    "staticFieldELResolver.notWriteable", name,
                    clazz.getName()));
        }
    }


    @Override
    public Object invoke(ELContext context, Object base, Object method,
            Class<?>[] paramTypes, Object[] params) {

        if (context == null) {
            throw new NullPointerException();
        }

        Object result = null;

        if (base instanceof ELClass && method instanceof String) {
            context.setPropertyResolved(base, method);

            String methodName = (String) method;
            if ("<init>".equals(methodName)) {
                // java.beans.Expression uses 'new' for constructors
                methodName = "new";
            }
            Class<?> clazz = ((ELClass) base).getKlass();

            java.beans.Expression beanExpression =
                    new java.beans.Expression(clazz, methodName, params);

            try {
                result = beanExpression.getValue();
            } catch (Exception e) {
                throw new ELException(e);
            }
        }

        return result;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof ELClass && property instanceof String) {
            context.setPropertyResolved(base, property);

            Class<?> clazz = ((ELClass) base).getKlass();
            String name = (String) property;
            Exception exception = null;
            try {
                Field field = clazz.getField(name);
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) &&
                        Modifier.isPublic(modifiers)) {
                    return field.getType();
                }
            } catch (IllegalArgumentException | NoSuchFieldException |
                    SecurityException e) {
                exception = e;
            }
            String msg = Util.message(context, "staticFieldELResolver.notFound",
                    name, clazz.getName());
            if (exception == null) {
                throw new PropertyNotFoundException(msg);
            } else {
                throw new PropertyNotFoundException(msg, exception);
            }
        }
        return null;
    }


    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof ELClass && property instanceof String) {
            context.setPropertyResolved(base, property);
        }
        return true;
    }


    /**
     * Always returns <code>null</code>.
     */
    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context,
            Object base) {
        return null;
    }

    /**
     * Always returns <code>String.class</code>.
     */
    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return String.class;
    }
}
