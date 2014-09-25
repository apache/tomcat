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
package org.apache.catalina;

/**
 * This interface is used by the {@link Realm} to compare the user provided
 * credentials with the credentials stored in the {@link Realm} for that user.
 */
public interface CredentialHandler {

    /**
     * Checks to see if the input credentials match the stored credentials
     *
     * @param inputCredentials  User provided credentials
     * @param storedCredentials Credentials stored in the {@link Realm}
     *
     * @return <code>true</code> if the inputCredentials match the
     *         storedCredentials, otherwise <code>false</code>
     */
    boolean matches(String inputCredentials, String storedCredentials);

    /**
     * Generates the equivalent stored credentials for the given input
     * credentials.
     *
     * @param inputCredentials  User provided credentials
     *
     * @return  The equivalent stored credentials for the given input
     *          credentials
     */
    String mutate(String inputCredentials);
}
