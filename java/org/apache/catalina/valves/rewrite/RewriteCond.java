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
package org.apache.catalina.valves.rewrite;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrite condition.
 */
public class RewriteCond {

    /**
     * Default constructor.
     */
    public RewriteCond() {
    }

    /**
     * Abstract condition interface.
     */
    public abstract static class Condition {

        /**
         * Default constructor.
         */
        public Condition() {
        }
        /**
         * Evaluate the condition.
         *
         * @param value The value to evaluate
         * @param resolver The resolver
         * @return {@code true} if the condition is met
         */
        public abstract boolean evaluate(String value, Resolver resolver);
    }

    /**
     * Pattern-based condition.
     */
    public static class PatternCondition extends Condition {
        /**
         * The compiled pattern for matching.
         */
        public Pattern pattern;
        private final ThreadLocal<Matcher> matcher = new ThreadLocal<>();

        /**
         * Default constructor.
         */
        public PatternCondition() {
        }

        @Override
        public boolean evaluate(String value, Resolver resolver) {
            Matcher m = pattern.matcher(value);
            if (m.matches()) {
                matcher.set(m);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns the last matcher used for evaluation.
         *
         * @return the matcher
         */
        public Matcher getMatcher() {
            return matcher.get();
        }
    }

    /**
     * Lexical comparison condition.
     */
    public static class LexicalCondition extends Condition {
        /**
         * Comparison type.
         * <pre>
         * -1: &lt;
         *  0: =
         *  1: &gt;
         * </pre>
         */
        public int type = 0;

        /**
         * The condition value for comparison.
         */
        public String condition;

        /**
         * Default constructor.
         */
        public LexicalCondition() {
        }

        @Override
        public boolean evaluate(String value, Resolver resolver) {
            int result = value.compareTo(condition);
            return switch (type) {
                case -1 -> (result < 0);
                case 0 -> (result == 0);
                case 1 -> (result > 0);
                default -> false;
            };

        }
    }

    /**
     * Resource existence condition.
     */
    public static class ResourceCondition extends Condition {
        /**
         * Default constructor.
         */
        public ResourceCondition() {
        }
        /**
         * <pre>
         * 0: -d (is directory ?)
         * 1: -f (is regular file ?)
         * 2: -s (is regular file with size ?)
         * </pre>
         */
        public int type = 0;

        @Override
        public boolean evaluate(String value, Resolver resolver) {
            return resolver.resolveResource(type, value);
        }
    }

    /**
     * The test string.
     */
    protected String testString = null;

    /**
     * The condition pattern.
     */
    protected String condPattern = null;

    /**
     * The flags string.
     */
    protected String flagsString = null;

    /**
     * Returns the condition pattern.
     *
     * @return the condition pattern
     */
    public String getCondPattern() {
        return condPattern;
    }

    /**
     * Sets the condition pattern.
     *
     * @param condPattern the condition pattern
     */
    public void setCondPattern(String condPattern) {
        this.condPattern = condPattern;
    }

    /**
     * Returns the test string.
     *
     * @return the test string
     */
    public String getTestString() {
        return testString;
    }

    /**
     * Sets the test string.
     *
     * @param testString the test string
     */
    public void setTestString(String testString) {
        this.testString = testString;
    }

    /**
     * Returns the flags string.
     *
     * @return the flags string
     */
    public final String getFlagsString() {
        return flagsString;
    }

    /**
     * Sets the flags string.
     *
     * @param flagsString the flags string
     */
    public final void setFlagsString(String flagsString) {
        this.flagsString = flagsString;
    }

    /**
     * Parses the condition using the provided rewrite maps.
     *
     * @param maps the rewrite maps
     */
    public void parse(Map<String,RewriteMap> maps) {
        test = new Substitution();
        test.setSub(testString);
        test.parse(maps);
        if (condPattern.startsWith("!")) {
            positive = false;
            condPattern = condPattern.substring(1);
        }
        if (condPattern.startsWith("<")) {
            LexicalCondition ncondition = new LexicalCondition();
            ncondition.type = -1;
            ncondition.condition = condPattern.substring(1);
            this.condition = ncondition;
        } else if (condPattern.startsWith(">")) {
            LexicalCondition ncondition = new LexicalCondition();
            ncondition.type = 1;
            ncondition.condition = condPattern.substring(1);
            this.condition = ncondition;
        } else if (condPattern.startsWith("=")) {
            LexicalCondition ncondition = new LexicalCondition();
            ncondition.type = 0;
            ncondition.condition = condPattern.substring(1);
            this.condition = ncondition;
        } else if (condPattern.equals("-d")) {
            ResourceCondition ncondition = new ResourceCondition();
            ncondition.type = 0;
            this.condition = ncondition;
        } else if (condPattern.equals("-f")) {
            ResourceCondition ncondition = new ResourceCondition();
            ncondition.type = 1;
            this.condition = ncondition;
        } else if (condPattern.equals("-s")) {
            ResourceCondition ncondition = new ResourceCondition();
            ncondition.type = 2;
            this.condition = ncondition;
        } else {
            PatternCondition ncondition = new PatternCondition();
            int flags = Pattern.DOTALL;
            if (isNocase()) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            ncondition.pattern = Pattern.compile(condPattern, flags);
            this.condition = ncondition;
        }
    }

    /**
     * Returns the matcher for the condition, if it is a pattern-based condition.
     *
     * @return the matcher, or {@code null} if not a pattern condition
     */
    public Matcher getMatcher() {
        if (condition instanceof PatternCondition) {
            return ((PatternCondition) condition).getMatcher();
        }
        return null;
    }

    @Override
    public String toString() {
        return "RewriteCond " + testString + " " + condPattern + ((flagsString != null) ? (" " + flagsString) : "");
    }


    /**
     * Whether the condition is positive.
     */
    protected boolean positive = true;

    /**
     * The test substitution.
     */
    protected Substitution test = null;

    /**
     * The condition.
     */
    protected Condition condition = null;

    /**
     * This makes the test case-insensitive, i.e., there is no difference between 'A-Z' and 'a-z' both in the expanded
     * TestString and the CondPattern. This flag is effective only for comparisons between TestString and CondPattern.
     * It has no effect on filesystem and subrequest checks.
     */
    public boolean nocase = false;

    /**
     * Use this to combine rule conditions with a local OR instead of the implicit AND.
     */
    public boolean ornext = false;

    /**
     * Evaluate the condition based on the context
     *
     * @param rule     corresponding matched rule
     * @param cond     last matched condition
     * @param resolver Property resolver
     *
     * @return <code>true</code> if the condition matches
     */
    public boolean evaluate(Matcher rule, Matcher cond, Resolver resolver) {
        String value = test.evaluate(rule, cond, resolver);
        if (positive) {
            return condition.evaluate(value, resolver);
        } else {
            return !condition.evaluate(value, resolver);
        }
    }

    /**
     * Returns whether the test is case-insensitive.
     *
     * @return {@code true} if case-insensitive
     */
    public boolean isNocase() {
        return nocase;
    }

    /**
     * Sets whether the test is case-insensitive.
     *
     * @param nocase {@code true} if case-insensitive
     */
    public void setNocase(boolean nocase) {
        this.nocase = nocase;
    }

    /**
     * Returns whether to combine rule conditions with a local OR.
     *
     * @return {@code true} if OR is used
     */
    public boolean isOrnext() {
        return ornext;
    }

    /**
     * Sets whether to combine rule conditions with a local OR.
     *
     * @param ornext {@code true} if OR is used
     */
    public void setOrnext(boolean ornext) {
        this.ornext = ornext;
    }

    /**
     * Returns whether the condition is positive.
     *
     * @return {@code true} if positive
     */
    public boolean isPositive() {
        return positive;
    }

    /**
     * Sets whether the condition is positive.
     *
     * @param positive {@code true} if positive
     */
    public void setPositive(boolean positive) {
        this.positive = positive;
    }
}
