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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a lambda expression in EL. A lambda expression has a list of formal parameters
 * and a body expression. When invoked, the lambda binds the actual arguments to its formal
 * parameters and evaluates the body expression within a scoped {@link ELContext}. Lambda
 * expressions support nesting, with inner lambdas having access to outer lambda arguments.
 *
 * @since EL 3.0
 */
public class LambdaExpression {

    private final List<String> formalParameters;
    private final ValueExpression expression;
    private final Map<String,Object> nestedArguments = new HashMap<>();
    private ELContext context = null;

    /**
     * Constructs a LambdaExpression with the given formal parameters and body expression.
     *
     * @param formalParameters the list of formal parameter names for the lambda
     * @param expression       the body expression to evaluate when the lambda is invoked
     */
    public LambdaExpression(List<String> formalParameters, ValueExpression expression) {
        this.formalParameters = formalParameters;
        this.expression = expression;

    }

    /**
     * Sets the {@link ELContext} to be used when this lambda is invoked without an explicit
     * context argument. This is called automatically when the lambda is coerced to a
     * functional interface type.
     *
     * @param context the ELContext to use for evaluation
     */
    public void setELContext(ELContext context) {
        this.context = context;
    }

    /**
     * Invokes the lambda expression with the given arguments in the specified EL context.
     * The formal parameters are bound to the corresponding arguments, and the body
     * expression is evaluated within a lambda scope. If the result is another lambda,
     * the current arguments are made available to the nested lambda.
     *
     * @param context the ELContext in which to evaluate the lambda
     * @param args    the actual arguments to bind to the formal parameters
     *
     * @return the result of evaluating the lambda body expression
     *
     * @throws ELException if the number of arguments is less than the number of formal parameters
     */
    @SuppressWarnings("null") // args[i] can't be null due to earlier checks
    public Object invoke(ELContext context, Object... args) throws ELException {

        Objects.requireNonNull(context);

        int formalParamCount = 0;
        if (formalParameters != null) {
            formalParamCount = formalParameters.size();
        }

        int argCount = 0;
        if (args != null) {
            argCount = args.length;
        }

        if (formalParamCount > argCount) {
            throw new ELException(Util.message(context, "lambdaExpression.tooFewArgs", Integer.valueOf(argCount),
                    Integer.valueOf(formalParamCount)));
        }

        // Build the argument map
        // Start with the arguments from any outer expressions so if there is
        // any overlap the local arguments have priority
        Map<String,Object> lambdaArguments = new HashMap<>(nestedArguments);
        for (int i = 0; i < formalParamCount; i++) {
            lambdaArguments.put(formalParameters.get(i), args[i]);
        }

        context.enterLambdaScope(lambdaArguments);

        try {
            Object result = expression.getValue(context);
            // Make arguments from this expression available to any nested
            // expression
            if (result instanceof LambdaExpression) {
                ((LambdaExpression) result).nestedArguments.putAll(lambdaArguments);
            }
            return result;
        } finally {
            context.exitLambdaScope();
        }
    }

    /**
     * Invokes the lambda expression with the given arguments using the EL context set by
     * {@link #setELContext(ELContext)}. This is a convenience method for use when the
     * lambda has been assigned to a functional interface variable.
     *
     * @param args the actual arguments to bind to the formal parameters
     *
     * @return the result of evaluating the lambda body expression
     *
     * @throws ELException if the number of arguments is less than the number of formal parameters
     */
    public Object invoke(Object... args) {
        return invoke(context, args);
    }
}
