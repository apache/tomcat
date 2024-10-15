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

public class RewriteCond {

    public abstract static class Condition {
        public abstract boolean evaluate(String value, Resolver resolver);
    }

    public static class PatternCondition extends Condition {
        public Pattern pattern;
        private ThreadLocal<Matcher> matcher = new ThreadLocal<>();

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

        public Matcher getMatcher() {
            return matcher.get();
        }
    }

    public static class LexicalCondition extends Condition {
        /**
         * <pre>
         * -1: &lt;
         *  0: =
         *  1: &gt;
         * </pre>
         */
        public int type = 0;
        public String condition;

        @Override
        public boolean evaluate(String value, Resolver resolver) {
            int result = value.compareTo(condition);
            switch (type) {
                case -1:
                    return (result < 0);
                case 0:
                    return (result == 0);
                case 1:
                    return (result > 0);
                default:
                    return false;
            }

        }
    }

    public static class ResourceCondition extends Condition {
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

    protected String testString = null;
    protected String condPattern = null;
    protected String flagsString = null;

    public String getCondPattern() {
        return condPattern;
    }

    public void setCondPattern(String condPattern) {
        this.condPattern = condPattern;
    }

    public String getTestString() {
        return testString;
    }

    public void setTestString(String testString) {
        this.testString = testString;
    }

    public final String getFlagsString() {
        return flagsString;
    }

    public final void setFlagsString(String flagsString) {
        this.flagsString = flagsString;
    }

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


    protected boolean positive = true;

    protected Substitution test = null;

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

    public boolean isNocase() {
        return nocase;
    }

    public void setNocase(boolean nocase) {
        this.nocase = nocase;
    }

    public boolean isOrnext() {
        return ornext;
    }

    public void setOrnext(boolean ornext) {
        this.ornext = ornext;
    }

    public boolean isPositive() {
        return positive;
    }

    public void setPositive(boolean positive) {
        this.positive = positive;
    }
}
