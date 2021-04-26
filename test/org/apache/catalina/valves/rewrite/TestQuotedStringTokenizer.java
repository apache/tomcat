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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestQuotedStringTokenizer {

    private String inputText;
    private List<String> tokens;

    @Parameters(name = "{index}: tokenize({0}) = {1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { { null, Collections.emptyList() }, { "", Collections.emptyList() },
                { " \t\r\n", Collections.emptyList() }, { "simple", Arrays.asList("simple") },
                { "more than one word", Arrays.asList("more", "than", "one", "word") },
                { "\"quoted text\"", Arrays.asList("quoted text") },
                { "  mixed \t\"words with\\\"\" escapes", Arrays.asList("mixed", "words with\"", "escapes") },
                { "# comment", Collections.emptyList() },
                { "Something # and then a comment", Arrays.asList("Something") },
                { "\"Quoted with a #\" which is not a comment",
                        Arrays.asList("Quoted with a #", "which", "is", "not", "a", "comment") } });
    }

    public TestQuotedStringTokenizer(String inputText, List<String> tokens) {
        this.inputText = inputText;
        this.tokens = tokens;
    }

    @Test
    public void testTokenize() {
        QuotedStringTokenizer tokenizer = new QuotedStringTokenizer(inputText);
        List<String> result = new ArrayList<>();
        int count = tokens.size();
        while (tokenizer.hasMoreTokens()) {
            MatcherAssert.assertThat(Integer.valueOf(tokenizer.countTokens()), CoreMatchers.is(Integer.valueOf(count)));
            result.add(tokenizer.nextToken());
            count--;
        }
        MatcherAssert.assertThat(Integer.valueOf(tokenizer.countTokens()), CoreMatchers.is(Integer.valueOf(0)));
        MatcherAssert.assertThat(tokens, CoreMatchers.is(result));
    }

}
