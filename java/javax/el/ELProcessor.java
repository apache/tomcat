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
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * @since EL 3.0
 */
public class ELProcessor {

    private static final Set<String> PRIMITIVES = new HashSet<>();
    static {
        PRIMITIVES.add("boolean");
        PRIMITIVES.add("byte");
        PRIMITIVES.add("char");
        PRIMITIVES.add("double");
        PRIMITIVES.add("float");
        PRIMITIVES.add("int");
        PRIMITIVES.add("long");
        PRIMITIVES.add("short");
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final ELManager manager = new ELManager();
    private final ELContext context = manager.getELContext();
    private final ExpressionFactory factory = ELManager.getExpressionFactory();


    public ELManager getELManager() {
        return manager;
    }


    public Object eval(String expression) {
        return getValue(expression, Object.class);
    }


    public Object getValue(String expression, Class<?> expectedType) {
        ValueExpression ve = factory.createValueExpression(
                context, bracket(expression), expectedType);
        return ve.getValue(context);
    }


    public void setValue(String expression, Object value) {
        ValueExpression ve = factory.createValueExpression(
                context, bracket(expression), Object.class);
        ve.setValue(context, value);
    }


    public void setVariable(String variable, String expression) {
        if (expression == null) {
            manager.setVariable(variable, null);
        } else {
            ValueExpression ve = factory.createValueExpression(
                    context, bracket(expression), Object.class);
            manager.setVariable(variable, ve);
        }
    }


    public void defineFunction(String prefix, String function, String className,
            String methodName) throws ClassNotFoundException,
            NoSuchMethodException {

        if (prefix == null || function == null || className == null ||
                methodName == null) {
            throw new NullPointerException(Util.message(
                    context, "elProcessor.defineFunctionNullParams"));
        }

        // Check the imports
        Class<?> clazz = context.getImportHandler().resolveClass(className);

        if (clazz == null) {
            clazz = Class.forName(className, true,
                    Thread.currentThread().getContextClassLoader());
        }

        if (!Modifier.isPublic(clazz.getModifiers())) {
            throw new ClassNotFoundException(Util.message(context,
                    "elProcessor.defineFunctionInvalidClass", className));
        }

        MethodSignature sig =
                new MethodSignature(context, methodName, className);

        if (function.length() == 0) {
            function = sig.getName();
        }

        Method methods[] = clazz.getMethods();
        for (Method method : methods) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getName().equals(sig.getName())) {
                if (sig.getParamTypeNames() == null) {
                    // Only a name provided, no signature so map the first
                    // method declared
                    manager.mapFunction(prefix, function, method);
                    return;
                }
                if (sig.getParamTypeNames().length != method.getParameterTypes().length) {
                    continue;
                }
                if (sig.getParamTypeNames().length == 0) {
                    manager.mapFunction(prefix, function, method);
                    return;
                } else {
                    Class<?>[] types = method.getParameterTypes();
                    String[] typeNames = sig.getParamTypeNames();
                    if (types.length == typeNames.length) {
                        boolean match = true;
                        for (int i = 0; i < types.length; i++) {
                            if (i == types.length -1 && method.isVarArgs()) {
                                String typeName = typeNames[i];
                                if (typeName.endsWith("...")) {
                                    typeName = typeName.substring(0, typeName.length() - 3);
                                    if (!typeName.equals(types[i].getName())) {
                                        match = false;
                                    }
                                } else {
                                    match = false;
                                }
                            } else if (!types[i].getName().equals(typeNames[i])) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            manager.mapFunction(prefix, function, method);
                            return;
                        }
                    }
                }
            }
        }

        throw new NoSuchMethodException(Util.message(context,
                "elProcessor.defineFunctionNoMethod", methodName, className));
    }


    /**
     * Map a method to a function name.
     *
     * @param prefix    Function prefix
     * @param function  Function name
     * @param method    Method
     *
     * @throws NullPointerException
     *              If any of the arguments are null
     * @throws NoSuchMethodException
     *              If the method is not static
     */
    public void defineFunction(String prefix, String function, Method method)
            throws java.lang.NoSuchMethodException {

        if (prefix == null || function == null || method == null) {
            throw new NullPointerException(Util.message(
                    context, "elProcessor.defineFunctionNullParams"));
        }

        int modifiers = method.getModifiers();

        // Check for public method as well as being static
        if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
            throw new NoSuchMethodException(Util.message(context,
                    "elProcessor.defineFunctionInvalidMethod", method.getName(),
                    method.getDeclaringClass().getName()));
        }

        manager.mapFunction(prefix, function, method);
    }


    public void defineBean(String name, Object bean) {
        manager.defineBean(name, bean);
    }


    private static String bracket(String expression) {
        return "${" + expression + "}";
    }

    private static class MethodSignature {

        private final String name;
        private final String[] parameterTypeNames;

        public MethodSignature(ELContext context, String methodName,
                String className) throws NoSuchMethodException {

            int paramIndex = methodName.indexOf('(');

            if (paramIndex == -1) {
                name = methodName.trim();
                parameterTypeNames = null;
            } else {
                String returnTypeAndName = methodName.substring(0, paramIndex).trim();
                // Assume that the return type and the name are separated by
                // whitespace. Given the use of trim() above, there should only
                // be one sequence of whitespace characters.
                int wsPos = -1;
                for (int i = 0; i < returnTypeAndName.length(); i++) {
                    if (Character.isWhitespace(returnTypeAndName.charAt(i))) {
                        wsPos = i;
                        break;
                    }
                }
                if (wsPos == -1) {
                    throw new NoSuchMethodException();
                }
                name = returnTypeAndName.substring(wsPos).trim();

                String paramString = methodName.substring(paramIndex).trim();
                // We know the params start with '(', check they end with ')'
                if (!paramString.endsWith(")")) {
                    throw new NoSuchMethodException(Util.message(context,
                            "elProcessor.defineFunctionInvalidParameterList",
                            paramString, methodName, className));
                }
                // Trim '(' and ')'
                paramString = paramString.substring(1, paramString.length() - 1).trim();
                if (paramString.length() == 0) {
                    parameterTypeNames = EMPTY_STRING_ARRAY;
                } else {
                    parameterTypeNames = paramString.split(",");
                    ImportHandler importHandler = context.getImportHandler();
                    for (int i = 0; i < parameterTypeNames.length; i++) {
                        String parameterTypeName = parameterTypeNames[i].trim();
                        int dimension = 0;
                        int bracketPos = parameterTypeName.indexOf('[');
                        if (bracketPos > -1) {
                            String parameterTypeNameOnly =
                                    parameterTypeName.substring(0, bracketPos).trim();
                            while (bracketPos > -1) {
                                dimension++;
                                bracketPos = parameterTypeName.indexOf('[', bracketPos+ 1);
                            }
                            parameterTypeName = parameterTypeNameOnly;
                        }
                        boolean varArgs = false;
                        if (parameterTypeName.endsWith("...")) {
                            varArgs = true;
                            dimension = 1;
                            parameterTypeName = parameterTypeName.substring(
                                    0, parameterTypeName.length() -3).trim();
                        }
                        boolean isPrimitive = PRIMITIVES.contains(parameterTypeName);
                        if (isPrimitive && dimension > 0) {
                            // When in an array, class name changes for primitive
                            switch(parameterTypeName)
                            {
                                case "boolean":
                                    parameterTypeName = "Z";
                                    break;
                                case "byte":
                                    parameterTypeName = "B";
                                    break;
                                case "char":
                                    parameterTypeName = "C";
                                    break;
                                case "double":
                                    parameterTypeName = "D";
                                    break;
                                case "float":
                                    parameterTypeName = "F";
                                    break;
                                case "int":
                                    parameterTypeName = "I";
                                    break;
                                case "long":
                                    parameterTypeName = "J";
                                    break;
                                case "short":
                                    parameterTypeName = "S";
                                    break;
                                default:
                                    // Should never happen
                                    break;
                            }
                        } else  if (!isPrimitive &&
                                !parameterTypeName.contains(".")) {
                            Class<?> clazz = importHandler.resolveClass(
                                    parameterTypeName);
                            if (clazz == null) {
                                throw new NoSuchMethodException(Util.message(
                                        context,
                                        "elProcessor.defineFunctionInvalidParameterTypeName",
                                        parameterTypeNames[i], methodName,
                                        className));
                            }
                            parameterTypeName = clazz.getName();
                        }
                        if (dimension > 0) {
                            // Convert to array form of class name
                            StringBuilder sb = new StringBuilder();
                            for (int j = 0; j < dimension; j++) {
                                sb.append('[');
                            }
                            if (!isPrimitive) {
                                sb.append('L');
                            }
                            sb.append(parameterTypeName);
                            if (!isPrimitive) {
                                sb.append(';');
                            }
                            parameterTypeName = sb.toString();
                        }
                        if (varArgs) {
                            parameterTypeName += "...";
                        }
                        parameterTypeNames[i] = parameterTypeName;
                    }
                }
            }

        }

        public String getName() {
            return name;
        }

        /**
         * @return <code>null</code> if just the method name was specified, an
         *         empty List if an empty parameter list was specified - i.e. ()
         *         - otherwise an ordered list of parameter type names
         */
        public String[] getParamTypeNames() {
            return parameterTypeNames;
        }
    }
}
