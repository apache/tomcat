/*
 * Copyright 2006 The Apache Software Foundation.
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

package org.apache.el.lang;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

import javax.el.ValueExpression;
import javax.el.VariableMapper;

public class VariableMapperImpl extends VariableMapper implements Externalizable {

    private static final long serialVersionUID = 1L;
    
    private Map vars = new HashMap();
    
    public VariableMapperImpl() {
        super();
    }

    public ValueExpression resolveVariable(String variable) {
        return (ValueExpression) this.vars.get(variable);
    }

    public ValueExpression setVariable(String variable,
            ValueExpression expression) {
        return (ValueExpression) this.vars.put(variable, expression);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.vars = (Map) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(this.vars);
    }
}
