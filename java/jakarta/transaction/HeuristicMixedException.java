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
 * Thrown when a transaction has been heuristically committed in part and
 * heuristically rolled back in part. A heuristic mixed result indicates that
 * some resource managers committed the transaction while others rolled it
 * back, typically as a result of a failure during the commit process.
 */
public class HeuristicMixedException extends Exception {

    @Serial
    private static final long serialVersionUID = 2345014349685956666L;

    /**
     * Constructs a {@code HeuristicMixedException} with no detail message.
     */
    public HeuristicMixedException() {
        super();
    }

    /**
     * Constructs a {@code HeuristicMixedException} with the specified detail message.
     *
     * @param msg the detail message
     */
    public HeuristicMixedException(String msg) {
        super(msg);
    }
}
