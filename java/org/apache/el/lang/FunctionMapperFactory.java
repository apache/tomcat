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
package org.apache.el.lang;

import java.lang.reflect.Method;

import jakarta.el.FunctionMapper;

import org.apache.el.util.MessageFactory;

/**
 * Factory that wraps a target FunctionMapper, capturing all function mappings
 * so that an immutable snapshot can be created.
 */
public class FunctionMapperFactory extends FunctionMapper {

    /**
     * The memento that stores captured function mappings.
     */
    protected FunctionMapperImpl memento = null;
    /**
     * The target function mapper being wrapped.
     */
    protected final FunctionMapper target;

    /**
     * Creates a new factory wrapping the given function mapper.
     *
     * @param mapper The target function mapper to wrap
     */
    public FunctionMapperFactory(FunctionMapper mapper) {
        if (mapper == null) {
            throw new NullPointerException(MessageFactory.get("error.noFunctionMapperTarget"));
        }
        this.target = mapper;
    }


    @Override
    public Method resolveFunction(String prefix, String localName) {
        if (this.memento == null) {
            this.memento = new FunctionMapperImpl();
        }
        Method m = this.target.resolveFunction(prefix, localName);
        if (m != null) {
            this.memento.mapFunction(prefix, localName, m);
        }
        return m;
    }


    @Override
    public void mapFunction(String prefix, String localName, Method method) {
        if (this.memento == null) {
            this.memento = new FunctionMapperImpl();
        }
        memento.mapFunction(prefix, localName, method);
    }


    /**
     * Creates an immutable snapshot of all function mappings captured so far.
     *
     * @return The captured function mappings as a FunctionMapper
     */
    public FunctionMapper create() {
        return this.memento;
    }

}
