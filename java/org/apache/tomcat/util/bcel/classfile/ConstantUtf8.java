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
package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;
import java.util.Objects;

import org.apache.tomcat.util.bcel.Const;

/**
 * Extends the abstract {@link Constant} to represent a reference to a UTF-8 encoded string.
 *
 * @see Constant
 */
public final class ConstantUtf8 extends Constant {

    /**
     * Gets a new or cached instance of the given value.
     * <p>
     * See {@link ConstantUtf8} class Javadoc for details.
     * </p>
     *
     * @param dataInput the value.
     * @return a new or cached instance of the given value.
     * @throws IOException if an I/O error occurs.
     */
    static ConstantUtf8 getInstance(final DataInput dataInput) throws IOException {
        return new ConstantUtf8(dataInput.readUTF());
    }

    private final String value;

    /**
     * @param value Data
     */
    private ConstantUtf8(final String value) {
        super(Const.CONSTANT_Utf8);
        this.value = Objects.requireNonNull(value, "value");
    }

    /**
     * @return Data converted to string.
     */
    public String getBytes() {
        return value;
    }
}
