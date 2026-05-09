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
 * Thrown when a transaction has been heuristically committed. A heuristic
 * commit indicates that the transaction manager has committed the transaction
 * without full agreement from all resource managers, typically as a result of
 * a failure during the commit process.
 */
public class HeuristicCommitException extends Exception {

    @Serial
    private static final long serialVersionUID = -3977609782149921760L;

    /**
     * Constructs a {@code HeuristicCommitException} with no detail message.
     */
    public HeuristicCommitException() {
        super();
    }

    /**
     * Constructs a {@code HeuristicCommitException} with the specified detail message.
     *
     * @param msg the detail message
     */
    public HeuristicCommitException(String msg) {
        super(msg);
    }
}
