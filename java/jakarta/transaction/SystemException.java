/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.transaction;

import java.io.Serial;

/**
 * Thrown to indicate a system error during transaction processing.
 * When this exception is thrown, the state of the transaction is
 * unknown and may need to be rolled back.
 */
public class SystemException extends Exception {

    @Serial
    private static final long serialVersionUID = 8615483418828223571L;

    /**
     * The error code associated with this exception.
     */
    public int errorCode;

    /**
     * Constructs a {@code SystemException} with no detail message.
     */
    public SystemException() {
        super();
    }

    /**
     * Constructs a {@code SystemException} with the specified detail message.
     *
     * @param s the detail message
     */
    public SystemException(String s) {
        super(s);
    }

    /**
     * Constructs a {@code SystemException} with the specified error code.
     *
     * @param errcode the error code
     */
    public SystemException(int errcode) {
        super();
        errorCode = errcode;
    }

}
