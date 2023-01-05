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
package org.apache.tomcat.util.buf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Checks that all MessageBytes getters are consistent with most recently used
 * setter.
 */
@RunWith(Parameterized.class)
public class TestMessageBytesConversion {

    private static final String PREVIOUS_STRING = "previous-string";
    private static final byte[] PREVIOUS_BYTES = "previous-bytes".getBytes(StandardCharsets.ISO_8859_1);
    private static final char[] PREVIOUS_CHARS = "previous-chars".toCharArray();

    private static final String EXPECTED_STRING = "expected";
    private static final byte[] EXPECTED_BYTES = "expected".getBytes(StandardCharsets.ISO_8859_1);
    private static final char[] EXPECTED_CHARS = "expected".toCharArray();

    @Parameters(name = "{index}: previous({0}, {1}, {2}, {3}), set {4}, check({5}, {6}, {7}")
    public static Collection<Object[]> parameters() {
        List<MessageBytesType> previousTypes = new ArrayList<>();
        previousTypes.add(MessageBytesType.BYTE);
        previousTypes.add(MessageBytesType.CHAR);
        previousTypes.add(MessageBytesType.STRING);
        previousTypes.add(MessageBytesType.NULL);

        List<MessageBytesType> setTypes = new ArrayList<>();
        setTypes.add(MessageBytesType.BYTE);
        setTypes.add(MessageBytesType.CHAR);
        setTypes.add(MessageBytesType.STRING);

        List<Object[]> parameterSets = new ArrayList<>();

        List<List<MessageBytesType>> previousPermutations = permutations(previousTypes);
        List<List<MessageBytesType>> checkPermutations = permutations(setTypes);

        for (List<MessageBytesType> setPermutation : previousPermutations) {
            for (MessageBytesType setType : setTypes) {
                for (List<MessageBytesType> checkPermutation : checkPermutations) {
                    parameterSets.add(new Object[] {
                            setPermutation.get(0), setPermutation.get(1), setPermutation.get(2), setPermutation.get(3),
                            setType,
                            checkPermutation.get(0), checkPermutation.get(1), checkPermutation.get(2)});
                }
            }
        }


        return parameterSets;
    }

    @Parameter(0)
    public MessageBytesType setFirst;
    @Parameter(1)
    public MessageBytesType setSecond;
    @Parameter(2)
    public MessageBytesType setThird;
    @Parameter(3)
    public MessageBytesType setFourth;

    @Parameter(4)
    public MessageBytesType expected;

    @Parameter(5)
    public MessageBytesType checkFirst;
    @Parameter(6)
    public MessageBytesType checkSecond;
    @Parameter(7)
    public MessageBytesType checkThird;


    @Test
    public void testConversion() {
        MessageBytes mb = MessageBytes.newInstance();

        setFirst.setPrevious(mb);
        setSecond.setPrevious(mb);
        setThird.setPrevious(mb);
        setFourth.setPrevious(mb);

        expected.setExpected(mb);

        checkFirst.checkExpected(mb);
        checkSecond.checkExpected(mb);
        checkThird.checkExpected(mb);
    }


    @Test
    public void testConversionNull() {
        MessageBytes mb = MessageBytes.newInstance();

        setFirst.setPrevious(mb);
        setSecond.setPrevious(mb);
        setThird.setPrevious(mb);
        setFourth.setPrevious(mb);

        mb.setString(null);

        checkFirst.checkNull(mb);
        checkSecond.checkNull(mb);
        checkThird.checkNull(mb);
    }


    public static enum MessageBytesType {
        BYTE(setBytes(PREVIOUS_BYTES),
                setBytes(EXPECTED_BYTES),
                assertBytes(EXPECTED_BYTES),
                assertBytes(null)),

        CHAR(setChars(PREVIOUS_CHARS),
                setChars(EXPECTED_CHARS),
                assertChars(EXPECTED_CHARS),
                assertChars(null)),

        STRING(setString(PREVIOUS_STRING),
                setString(EXPECTED_STRING),
                assertString(EXPECTED_STRING),
                assertString(null)),

        NULL(setString(null),
                setString(null),
                assertNull(),
                assertNull());

        private final MessageBytesConsumer setPrevious;
        private final MessageBytesConsumer setExpected;
        private final MessageBytesConsumer checkExpected;
        private final MessageBytesConsumer checkNull;

        private MessageBytesType(MessageBytesConsumer setPrevious, MessageBytesConsumer setExpected,
                MessageBytesConsumer checkExpected, MessageBytesConsumer checkNull) {
            this.setPrevious = setPrevious;
            this.setExpected = setExpected;
            this.checkExpected = checkExpected;
            this.checkNull = checkNull;
        }

        public void setPrevious(MessageBytes mb) {
            setPrevious.accept(mb);
        }

        public void setExpected(MessageBytes mb) {
            setExpected.accept(mb);
        }

        public void checkExpected(MessageBytes mb) {
            checkExpected.accept(mb);
        }

        public void checkNull(MessageBytes mb) {
            checkNull.accept(mb);
        }
    }


    private static <T> List<List<T>> permutations(List<T> items) {
        List<List<T>> results = new ArrayList<>();

        if (items.size() == 1) {
            results.add(items);
        } else {
            List<T> others = new ArrayList<>(items);
            T first = others.remove(0);
            List<List<T>> subPermutations = permutations(others);

            for (List<T> subPermutation : subPermutations) {
                for (int i = 0; i <= subPermutation.size(); i++) {
                    List<T> result = new ArrayList<>(subPermutation);
                    result.add(i, first);
                    results.add(result);
                }
            }
        }

        return results;
    }


    private interface MessageBytesConsumer {
        void accept(MessageBytes mb);
    }


    private static MessageBytesConsumer setBytes(final byte[] b) {
        return new MessageBytesConsumer() {
            @Override
            public void accept(MessageBytes mb) {
                mb.setBytes(b, 0, b.length);
            }
        };
    }


    private static MessageBytesConsumer assertBytes(final byte[] b) {
        return new MessageBytesConsumer() {
            @Override
            public void accept(MessageBytes mb) {
                mb.toBytes();
                if (b == null) {
                    Assert.assertTrue(mb.getByteChunk().isNull());
                } else {
                    Assert.assertTrue(mb.getByteChunk().equals(b, 0, b.length));
                }
            }
        };
    }


    private static MessageBytesConsumer setChars(final char[] c) {
        return new MessageBytesConsumer() {
            @Override
            public void accept(MessageBytes mb) {
                mb.setChars(c, 0, c.length);
            }
        };
    }


    private static MessageBytesConsumer assertChars(final char[] c) {
        return new MessageBytesConsumer() {
            @Override
            public void accept(MessageBytes mb) {
                mb.toChars();
                if (c == null) {
                    Assert.assertTrue(mb.getCharChunk().isNull());
                } else {
                    Assert.assertArrayEquals(c, mb.getCharChunk().getChars());
                }
            }
        };
    }


    private static MessageBytesConsumer setString(final String s) {
        return new MessageBytesConsumer() {
            @Override
            public void accept(MessageBytes mb) {
                mb.setString(s);
            }
        };
    }


    private static MessageBytesConsumer assertString(final String s) {
        return new MessageBytesConsumer() {
            @Override
            public void accept(MessageBytes mb) {
                if (s == null) {
                    Assert.assertNull(mb.toString());
                } else {
                    Assert.assertEquals(s, mb.toString());
                }
            }
        };
    }


    private static MessageBytesConsumer assertNull() {
        return new MessageBytesConsumer() {
            @Override
            public void accept(MessageBytes mb) {
                Assert.assertTrue(mb.isNull());
            }
        };
    }
}
