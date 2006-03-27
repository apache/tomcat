/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import javax.el.CompositeELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ValueExpression;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.Expression;
import javax.servlet.jsp.el.VariableResolver;

import org.apache.jasper.runtime.JspApplicationContextImpl;

public final class ExpressionImpl extends Expression {

	private final ValueExpression ve;
	
	public ExpressionImpl(ValueExpression ve) {
		this.ve = ve;
	}

	public Object evaluate(VariableResolver vResolver) throws ELException {
		ELContext ctx = new ELContextImpl(new ELResolverImpl(vResolver));
		return ve.getValue(ctx);
	}

}
