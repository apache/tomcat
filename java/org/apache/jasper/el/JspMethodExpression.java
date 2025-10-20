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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.MethodExpression;
import jakarta.el.MethodInfo;
import jakarta.el.MethodNotFoundException;
import jakarta.el.MethodReference;
import jakarta.el.PropertyNotFoundException;

public final class JspMethodExpression extends MethodExpression implements Externalizable {

    private String mark;

    private MethodExpression target;

    public JspMethodExpression() {
        super();
    }

    public JspMethodExpression(String mark, MethodExpression target) {
        this.target = target;
        this.mark = mark;
    }

    @Override
    public MethodInfo getMethodInfo(ELContext context)
            throws NullPointerException, PropertyNotFoundException, MethodNotFoundException, ELException {
        context.notifyBeforeEvaluation(getExpressionString());
        try {
            MethodInfo result = this.target.getMethodInfo(context);
            context.notifyAfterEvaluation(getExpressionString());
            return result;
        } catch (MethodNotFoundException e) {
            if (e instanceof JspMethodNotFoundException) {
                throw e;
            }
            throw new JspMethodNotFoundException(this.mark, e);
        } catch (PropertyNotFoundException e) {
            if (e instanceof JspPropertyNotFoundException) {
                throw e;
            }
            throw new JspPropertyNotFoundException(this.mark, e);
        } catch (ELException e) {
            if (e instanceof JspELException) {
                throw e;
            }
            throw new JspELException(this.mark, e);
        }
    }

    @Override
    public Object invoke(ELContext context, Object[] params)
            throws NullPointerException, PropertyNotFoundException, MethodNotFoundException, ELException {
        context.notifyBeforeEvaluation(getExpressionString());
        try {
            Object result = this.target.invoke(context, params);
            context.notifyAfterEvaluation(getExpressionString());
            return result;
        } catch (MethodNotFoundException e) {
            if (e instanceof JspMethodNotFoundException) {
                throw e;
            }
            throw new JspMethodNotFoundException(this.mark, e);
        } catch (PropertyNotFoundException e) {
            if (e instanceof JspPropertyNotFoundException) {
                throw e;
            }
            throw new JspPropertyNotFoundException(this.mark, e);
        } catch (ELException e) {
            if (e instanceof JspELException) {
                throw e;
            }
            throw new JspELException(this.mark, e);
        }
    }

    @Override
    public MethodReference getMethodReference(ELContext context) {
        context.notifyBeforeEvaluation(getExpressionString());
        try {
            MethodReference result = this.target.getMethodReference(context);
            context.notifyAfterEvaluation(getExpressionString());
            return result;
        } catch (MethodNotFoundException e) {
            if (e instanceof JspMethodNotFoundException) {
                throw e;
            }
            throw new JspMethodNotFoundException(this.mark, e);
        } catch (PropertyNotFoundException e) {
            if (e instanceof JspPropertyNotFoundException) {
                throw e;
            }
            throw new JspPropertyNotFoundException(this.mark, e);
        } catch (ELException e) {
            if (e instanceof JspELException) {
                throw e;
            }
            throw new JspELException(this.mark, e);
        }
    }

    @Override
    public boolean isParametersProvided() {
        return this.target.isParametersProvided();
    }

    @Override
    public boolean equals(Object obj) {
        return this.target.equals(obj);
    }

    @Override
    public int hashCode() {
        return this.target.hashCode();
    }

    @Override
    public String getExpressionString() {
        return this.target.getExpressionString();
    }

    @Override
    public boolean isLiteralText() {
        return this.target.isLiteralText();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.mark);
        out.writeObject(this.target);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.mark = in.readUTF();
        this.target = (MethodExpression) in.readObject();
    }

}
