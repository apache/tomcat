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
package org.apache.el.stream;

import jakarta.el.ELException;
import jakarta.el.LambdaExpression;

import org.apache.el.util.MessageFactory;

public class Optional {

    private final Object obj;

    static final Optional EMPTY = new Optional(null);

    Optional(Object obj) {
        this.obj = obj;
    }


    public Object get() throws ELException {
        if (obj == null) {
            throw new ELException(MessageFactory.get("stream.optional.empty"));
        } else {
            return obj;
        }
    }


    public void ifPresent(LambdaExpression le) {
        if (obj != null) {
            le.invoke(obj);
        }
    }


    public Object orElse(Object other) {
        if (obj == null) {
            return other;
        } else {
            return obj;
        }
    }


    public Object orElseGet(Object le) {
        if (obj == null) {
            // EL 3.0 specification says parameter is LambdaExpression but it
            // may already have been evaluated. If that is the case, the
            // original parameter will have been checked to ensure it was a
            // LambdaExpression before it was evaluated.

            if (le instanceof LambdaExpression) {
                return ((LambdaExpression) le).invoke((Object[]) null);
            } else {
                return le;
            }
        } else {
            return obj;
        }
    }
}
