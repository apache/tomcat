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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * @since EL 3.0
 */
public class StaticFieldELResolver extends ELResolver {

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof ELClass && property instanceof String) {
            context.setPropertyResolved(base, property);

            Class<?> clazz = ((ELClass) base).getKlass();
            String name = (String) property;
            Exception exception = null;
            try {
                Field field = clazz.getField(name);
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) && Util.canAccess(null, field)) {
                    return field.get(null);
                }
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
                exception = e;
            }
            String msg = Util.message(context, "staticFieldELResolver.notFound", name, clazz.getName());
            if (exception == null) {
                throw new PropertyNotFoundException(msg);
            } else {
                throw new PropertyNotFoundException(msg, exception);
            }
        }
        return null;
    }


    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        Objects.requireNonNull(context);

        if (base instanceof ELClass && property instanceof String) {
            Class<?> clazz = ((ELClass) base).getKlass();
            String name = (String) property;

            throw new PropertyNotWritableException(
                    Util.message(context, "staticFieldELResolver.notWritable", name, clazz.getName()));
        }
    }


    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        Objects.requireNonNull(context);

        if (base instanceof ELClass && method instanceof String) {
            context.setPropertyResolved(base, method);

            Class<?> clazz = ((ELClass) base).getKlass();
            String methodName = (String) method;

            if ("<init>".equals(methodName)) {
                Constructor<?> match = Util.findConstructor(context, clazz, paramTypes, params);

                Object[] parameters =
                        Util.buildParameters(context, match.getParameterTypes(), match.isVarArgs(), params);

                Object result = null;

                try {
                    result = match.newInstance(parameters);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    Util.handleThrowable(cause);
                    throw new ELException(cause);
                } catch (ReflectiveOperationException e) {
                    throw new ELException(e);
                }
                return result;

            } else {
                // Static method so base should be null
                Method match = Util.findMethod(context, clazz, null, methodName, paramTypes, params);

                if (match == null) {
                    throw new MethodNotFoundException(
                            Util.message(context, "staticFieldELResolver.methodNotFound", methodName, clazz.getName()));
                }

                Object[] parameters =
                        Util.buildParameters(context, match.getParameterTypes(), match.isVarArgs(), params);

                Object result = null;
                try {
                    result = match.invoke(null, parameters);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new ELException(e);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    Util.handleThrowable(cause);
                    throw new ELException(cause);
                }
                return result;
            }
        }
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof ELClass && property instanceof String) {
            context.setPropertyResolved(base, property);

            Class<?> clazz = ((ELClass) base).getKlass();
            String name = (String) property;
            Exception exception = null;
            try {
                Field field = clazz.getField(name);
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) && Util.canAccess(null, field)) {
                    // Resolver is read-only so returns null for resolved fields
                    return null;
                }
            } catch (IllegalArgumentException | NoSuchFieldException | SecurityException e) {
                exception = e;
            }
            String msg = Util.message(context, "staticFieldELResolver.notFound", name, clazz.getName());
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
        Objects.requireNonNull(context);

        if (base instanceof ELClass && property instanceof String) {
            context.setPropertyResolved(base, property);
        }
        return true;
    }


    /**
     * @return This resolver always returns <code>String.class</code>
     */
    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return String.class;
    }
}
