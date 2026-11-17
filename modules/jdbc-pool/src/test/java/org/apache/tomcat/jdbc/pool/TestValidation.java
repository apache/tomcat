/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import org.junit.jupiter.api.Nested;

/**
 * Thematic grouping of the validation tests. Each nested class keeps the
 * original test class as its base, so every test method, assertion and
 * helper is preserved; the nested classes only add the thematic grouping.
 */
class TestValidation {

    @Nested
    class QueryValidation extends ValidationBase {
    }

    @Nested
    class BlankQuery extends BlankValidationQueryBase {
    }

    @Nested
    class BuiltInFailures extends ThrowingBuiltInValidationBase {
    }

    @Nested
    class CustomValidatorFailures extends ThrowingValidatorBase {
    }

    @Nested
    class DiscardedHandle extends IsValidOnDiscardedHandleBase {
    }

    @Nested
    class LastValidated extends LastValidatedLifecycleBase {
    }
}
