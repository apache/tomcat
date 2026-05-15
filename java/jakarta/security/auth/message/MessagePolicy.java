/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jakarta.security.auth.message;

/**
 * Defines the security policy requirements for a message-based
 * authentication module.  A {@code MessagePolicy} specifies the target
 * policies that determine which messages require authentication and the
 * level of protection required.
 */
public class MessagePolicy {

    private final TargetPolicy[] targetPolicies;
    private final boolean mandatory;

    /**
     * Creates a new {@code MessagePolicy} with the specified target
     * policies and mandatory flag.
     *
     * @param targetPolicies the array of {@code TargetPolicy} objects
     *        defining the policy requirements; must not be {@code null}
     * @param mandatory {@code true} if the policy is mandatory and
     *        authentication must succeed; {@code false} if the policy
     *        is optional
     * @throws IllegalArgumentException if {@code targetPolicies} is {@code null}
     */
    public MessagePolicy(TargetPolicy[] targetPolicies, boolean mandatory) {
        if (targetPolicies == null) {
            throw new IllegalArgumentException("targetPolicies is null");
        }
        this.targetPolicies = targetPolicies;
        this.mandatory = mandatory;
    }

    /**
     * Indicates whether this policy is mandatory.  If mandatory, the
     * authentication module must enforce the policy and authentication
     * will fail if the policy cannot be satisfied.
     *
     * @return {@code true} if the policy is mandatory, {@code false} otherwise
     */
    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * Returns the target policies associated with this message policy.
     *
     * @return an array of {@code TargetPolicy} objects, or {@code null}
     *         if no target policies are defined
     */
    public TargetPolicy[] getTargetPolicies() {
        if (targetPolicies.length == 0) {
            return null;
        }
        return targetPolicies;
    }

    /**
     * Defines the level of protection to be applied to a message.
     * A protection policy specifies whether the sender, content,
     * or recipient of a message must be authenticated.
     */
    public interface ProtectionPolicy {

        /**
         * Identifier for a policy that requires authentication of the sender.
         */
        String AUTHENTICATE_SENDER = "#authenticateSender";

        /**
         * Identifier for a policy that requires authentication of the message content.
         */
        String AUTHENTICATE_CONTENT = "#authenticateContent";

        /**
         * Identifier for a policy that requires authentication of the recipient.
         */
        String AUTHENTICATE_RECIPIENT = "#authenticateRecipient";

        /**
         * Returns the unique identifier for this protection policy.
         *
         * @return the string identifier for this policy
         */
        String getID();
    }

    /**
     * Defines how to extract target information from a message for
     * policy evaluation.  A target determines which messages are
     * subject to a particular {@code TargetPolicy}.
     */
    public interface Target {

        /**
         * Retrieves the target value from the given message.
         *
         * @param messageInfo the message containing the target information
         * @return the target value extracted from the message
         */
        Object get(MessageInfo messageInfo);

        /**
         * Removes the target value from the given message.
         *
         * @param messageInfo the message from which to remove the target
         */
        void remove(MessageInfo messageInfo);

        /**
         * Stores the target value in the given message.
         *
         * @param messageInfo the message in which to store the target
         * @param data the target value to store
         */
        void put(MessageInfo messageInfo, Object data);
    }

    /**
     * Associates a set of targets with a protection policy.  A
     * {@code TargetPolicy} defines the protection requirements for
     * messages that match the specified targets.
     */
    public static class TargetPolicy {

        private final Target[] targets;
        private final ProtectionPolicy protectionPolicy;

        /**
         * Creates a new {@code TargetPolicy} with the specified targets
         * and protection policy.
         *
         * @param targets the array of {@code Target} objects, or {@code null}
         *        if not applicable
         * @param protectionPolicy the {@code ProtectionPolicy} to apply;
         *        must not be {@code null}
         * @throws IllegalArgumentException if {@code protectionPolicy} is {@code null}
         */
        public TargetPolicy(Target[] targets, ProtectionPolicy protectionPolicy) {
            if (protectionPolicy == null) {
                throw new IllegalArgumentException("protectionPolicy is null");
            }
            this.targets = targets;
            this.protectionPolicy = protectionPolicy;
        }

        /**
         * Returns the targets associated with this target policy.
         *
         * @return an array of {@code Target} objects, or {@code null}
         *         if no targets are defined
         */
        public Target[] getTargets() {
            if (targets == null || targets.length == 0) {
                return null;
            }
            return targets;
        }

        /**
         * Returns the protection policy associated with this target policy.
         *
         * @return the {@code ProtectionPolicy} for this target policy
         */
        public ProtectionPolicy getProtectionPolicy() {
            return protectionPolicy;
        }
    }
}
