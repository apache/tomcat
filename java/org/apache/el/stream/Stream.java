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
package org.apache.el.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import jakarta.el.ELException;
import jakarta.el.LambdaExpression;

import org.apache.el.lang.ELArithmetic;
import org.apache.el.lang.ELSupport;
import org.apache.el.util.MessageFactory;

/**
 * A stream of elements supporting sequential operations such as filter, map,
 * reduce, and various terminal operations.
 */
public class Stream {

    private final Iterator<Object> iterator;


    /**
     * Constructs a new Stream from the given iterator.
     *
     * @param iterator the iterator providing the stream elements
     */
    public Stream(Iterator<Object> iterator) {
        this.iterator = iterator;
    }


    /**
     * Returns a stream consisting of the elements that match the given predicate.
     *
     * @param le the predicate to apply to each element
     * @return the new filtered stream
     */
    public Stream filter(final LambdaExpression le) {
        Iterator<Object> downStream = new OpIterator() {
            @Override
            protected void findNext() {
                while (iterator.hasNext()) {
                    Object obj = iterator.next();
                    if (ELSupport.coerceToBoolean(null, le.invoke(obj), true).booleanValue()) {
                        next = obj;
                        foundNext = true;
                        break;
                    }
                }
            }
        };
        return new Stream(downStream);
    }


    /**
     * Returns a stream consisting of the results of applying the given function
     * to the elements of this stream.
     *
     * @param le the function to apply to each element
     * @return the new mapped stream
     */
    public Stream map(final LambdaExpression le) {
        Iterator<Object> downStream = new OpIterator() {
            @Override
            protected void findNext() {
                if (iterator.hasNext()) {
                    Object obj = iterator.next();
                    next = le.invoke(obj);
                    foundNext = true;
                }
            }
        };
        return new Stream(downStream);
    }


    /**
     * Returns a stream consisting of the results of replacing each element of
     * this stream with the contents of a stream produced by applying the provided
     * function to each element.
     *
     * @param le the function to apply to each element which produces a new stream
     * @return the new flattened stream
     */
    public Stream flatMap(final LambdaExpression le) {
        Iterator<Object> downStream = new OpIterator() {

            private Iterator<?> inner;

            @Override
            protected void findNext() {
                while (iterator.hasNext() || (inner != null && inner.hasNext())) {
                    if (inner == null || !inner.hasNext()) {
                        inner = ((Stream) le.invoke(iterator.next())).iterator;
                    }

                    if (inner.hasNext()) {
                        next = inner.next();
                        foundNext = true;
                        break;
                    }
                }
            }
        };
        return new Stream(downStream);
    }


    /**
     * Returns a stream consisting of the distinct elements of this stream.
     *
     * @return the new stream with distinct elements
     */
    public Stream distinct() {
        Iterator<Object> downStream = new OpIterator() {

            private final Set<Object> values = new HashSet<>();

            @Override
            protected void findNext() {
                while (iterator.hasNext()) {
                    Object obj = iterator.next();
                    if (values.add(obj)) {
                        next = obj;
                        foundNext = true;
                        break;
                    }
                }
            }
        };
        return new Stream(downStream);
    }


    /**
     * Returns a stream consisting of the elements of this stream, sorted
     * according to natural order.
     *
     * @return the new sorted stream
     */
    public Stream sorted() {
        Iterator<Object> downStream = new OpIterator() {

            private Iterator<Object> sorted = null;

            @Override
            protected void findNext() {
                if (sorted == null) {
                    sort();
                }
                if (sorted.hasNext()) {
                    next = sorted.next();
                    foundNext = true;
                }
            }

            @SuppressWarnings({ "rawtypes", "unchecked" })
            private void sort() {
                List list = new ArrayList<>();
                while (iterator.hasNext()) {
                    list.add(iterator.next());
                }
                Collections.sort(list);
                sorted = list.iterator();
            }
        };
        return new Stream(downStream);
    }


    /**
     * Returns a stream consisting of the elements of this stream, sorted
     * according to the order induced by the provided comparator.
     *
     * @param le the comparator to determine sort order
     * @return the new sorted stream
     */
    public Stream sorted(final LambdaExpression le) {
        Iterator<Object> downStream = new OpIterator() {

            private Iterator<Object> sorted = null;

            @Override
            protected void findNext() {
                if (sorted == null) {
                    sort(le);
                }
                if (sorted.hasNext()) {
                    next = sorted.next();
                    foundNext = true;
                }
            }

            @SuppressWarnings({ "rawtypes", "unchecked" })
            private void sort(LambdaExpression le) {
                List list = new ArrayList<>();
                Comparator<Object> c = new LambdaExpressionComparator(le);
                while (iterator.hasNext()) {
                    list.add(iterator.next());
                }
                list.sort(c);
                sorted = list.iterator();
            }
        };
        return new Stream(downStream);
    }


    /**
     * Performs an action for each element of this stream.
     *
     * @param le the action to perform for each element
     * @return null
     */
    public Object forEach(final LambdaExpression le) {
        while (iterator.hasNext()) {
            le.invoke(iterator.next());
        }
        return null;
    }


    /**
     * Returns a stream consisting of the elements of this stream, additionally
     * performing the provided action on each element as elements are consumed.
     *
     * @param le the action to perform on each element
     * @return the new stream
     */
    public Stream peek(final LambdaExpression le) {
        Iterator<Object> downStream = new OpIterator() {
            @Override
            protected void findNext() {
                if (iterator.hasNext()) {
                    Object obj = iterator.next();
                    le.invoke(obj);
                    next = obj;
                    foundNext = true;
                }
            }
        };
        return new Stream(downStream);
    }


    /**
     * Returns the underlying iterator for this stream.
     *
     * @return the underlying iterator
     */
    public Iterator<?> iterator() {
        return iterator;
    }


    /**
     * Returns a stream consisting of the first {@code count} elements of this stream.
     *
     * @param count the number of elements to limit to
     * @return the new limited stream
     */
    public Stream limit(final Number count) {
        return substream(Integer.valueOf(0), count);
    }


    /**
     * Returns a stream consisting of the elements of this stream from the
     * specified start position to the end.
     *
     * @param start the start position (inclusive)
     * @return the new substream
     */
    public Stream substream(final Number start) {
        return substream(start, Integer.valueOf(Integer.MAX_VALUE));
    }

    /**
     * Returns a stream consisting of the elements of this stream from the
     * specified start position up to, but not including, the specified end position.
     *
     * @param start the start position (inclusive)
     * @param end the end position (exclusive)
     * @return the new substream
     */
    public Stream substream(final Number start, final Number end) {

        Iterator<Object> downStream = new OpIterator() {

            private final int startPos = start.intValue();
            private final int endPos = end.intValue();
            private int itemCount = 0;

            @Override
            protected void findNext() {
                while (itemCount < startPos && iterator.hasNext()) {
                    iterator.next();
                    itemCount++;
                }
                if (itemCount < endPos && iterator.hasNext()) {
                    itemCount++;
                    next = iterator.next();
                    foundNext = true;
                }
            }
        };
        return new Stream(downStream);
    }


    /**
     * Returns a list containing all the elements of this stream.
     *
     * @return the list of elements
     */
    public List<Object> toList() {
        List<Object> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }


    /**
     * Returns an array containing all the elements of this stream.
     *
     * @return the array of elements
     */
    public Object[] toArray() {
        List<Object> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result.toArray(new Object[0]);
    }


    /**
     * Performs a reduction on the elements of this stream, using the provided
     * accumulator function. The first element is used as the initial value.
     *
     * @param le the accumulator function
     * @return an Optional describing the reduced value, or empty if this stream is empty
     */
    public Optional reduce(LambdaExpression le) {
        Object seed = null;

        if (iterator.hasNext()) {
            seed = iterator.next();
        }

        if (seed == null) {
            return Optional.EMPTY;
        } else {
            return new Optional(reduce(seed, le));
        }
    }


    /**
     * Performs a reduction on the elements of this stream, using the provided
     * initial value and accumulator function.
     *
     * @param seed the initial value for the reduction
     * @param le the accumulator function
     * @return the result of the reduction
     */
    public Object reduce(Object seed, LambdaExpression le) {
        Object result = seed;

        while (iterator.hasNext()) {
            result = le.invoke(result, iterator.next());
        }

        return result;
    }


    /**
     * Returns the maximum element of this stream according to natural order.
     *
     * @return an Optional describing the maximum element, or empty if this stream is empty
     */
    public Optional max() {
        return compare(true);
    }


    /**
     * Returns the maximum element of this stream according to the provided comparator.
     *
     * @param le the comparator to determine the maximum
     * @return an Optional describing the maximum element, or empty if this stream is empty
     */
    public Optional max(LambdaExpression le) {
        return compare(true, le);
    }


    /**
     * Returns the minimum element of this stream according to natural order.
     *
     * @return an Optional describing the minimum element, or empty if this stream is empty
     */
    public Optional min() {
        return compare(false);
    }


    /**
     * Returns the minimum element of this stream according to the provided comparator.
     *
     * @param le the comparator to determine the minimum
     * @return an Optional describing the minimum element, or empty if this stream is empty
     */
    public Optional min(LambdaExpression le) {
        return compare(false, le);
    }


    /**
     * Calculates the average of all numeric elements in the stream.
     *
     * @return an Optional containing the average, or empty if the stream is empty
     */
    public Optional average() {
        long count = 0;
        Number sum = Long.valueOf(0);

        while (iterator.hasNext()) {
            count++;
            sum = ELArithmetic.add(sum, iterator.next());
        }

        if (count == 0) {
            return Optional.EMPTY;
        } else {
            return new Optional(ELArithmetic.divide(sum, Long.valueOf(count)));
        }
    }


    /**
     * Returns the sum of all numeric elements in the stream.
     *
     * @return the sum of all elements
     */
    public Number sum() {
        Number sum = Long.valueOf(0);

        while (iterator.hasNext()) {
            sum = ELArithmetic.add(sum, iterator.next());
        }

        return sum;
    }


    /**
     * Counts the number of elements in the stream.
     *
     * @return the number of elements
     */
    public Long count() {
        long count = 0;

        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        return Long.valueOf(count);
    }


    /**
     * Checks if any element in the stream matches the given predicate.
     *
     * @param le the predicate lambda expression
     * @return an Optional containing true if any element matches, or empty if the stream is empty
     */
    public Optional anyMatch(LambdaExpression le) {
        if (!iterator.hasNext()) {
            return Optional.EMPTY;
        }

        Boolean match = Boolean.FALSE;

        while (!match.booleanValue() && iterator.hasNext()) {
            match = (Boolean) le.invoke(iterator.next());
        }

        return new Optional(match);
    }


    /**
     * Checks if all elements in the stream match the given predicate.
     *
     * @param le the predicate lambda expression
     * @return an Optional containing true if all elements match, or empty if the stream is empty
     */
    public Optional allMatch(LambdaExpression le) {
        if (!iterator.hasNext()) {
            return Optional.EMPTY;
        }

        Boolean match = Boolean.TRUE;

        while (match.booleanValue() && iterator.hasNext()) {
            match = (Boolean) le.invoke(iterator.next());
        }

        return new Optional(match);
    }


    /**
     * Checks if no elements of this stream match the given predicate.
     *
     * @param le the predicate lambda expression
     * @return an Optional containing true if no elements match, or empty if the stream is empty
     */
    public Optional noneMatch(LambdaExpression le) {
        if (!iterator.hasNext()) {
            return Optional.EMPTY;
        }

        Boolean match = Boolean.FALSE;

        while (!match.booleanValue() && iterator.hasNext()) {
            match = (Boolean) le.invoke(iterator.next());
        }

        return new Optional(Boolean.valueOf(!match.booleanValue()));
    }


    /**
     * Returns an Optional describing the first element of this stream,
     * or an empty Optional if the stream is empty.
     *
     * @return an Optional describing the first element of this stream
     */
    public Optional findFirst() {
        if (iterator.hasNext()) {
            return new Optional(iterator.next());
        } else {
            return Optional.EMPTY;
        }
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Optional compare(boolean isMax) {
        Comparable result = null;

        if (iterator.hasNext()) {
            Object obj = iterator.next();
            if ((obj instanceof Comparable)) {
                result = (Comparable) obj;
            } else {
                throw new ELException(MessageFactory.get("stream.compare.notComparable"));
            }
        }

        while (iterator.hasNext()) {
            Object obj = iterator.next();
            if ((obj instanceof Comparable)) {
                if (isMax && ((Comparable) obj).compareTo(result) > 0) {
                    result = (Comparable) obj;
                } else if (!isMax && ((Comparable) obj).compareTo(result) < 0) {
                    result = (Comparable) obj;
                }
            } else {
                throw new ELException(MessageFactory.get("stream.compare.notComparable"));
            }
        }

        if (result == null) {
            return Optional.EMPTY;
        } else {
            return new Optional(result);
        }
    }


    private Optional compare(boolean isMax, LambdaExpression le) {
        Object result = null;

        if (iterator.hasNext()) {
            result = iterator.next();
        }

        while (iterator.hasNext()) {
            Object obj = iterator.next();
            if (isMax && ELSupport.coerceToNumber(null, le.invoke(obj, result), Integer.class).intValue() > 0) {
                result = obj;
            } else if (!isMax && ELSupport.coerceToNumber(null, le.invoke(obj, result), Integer.class).intValue() < 0) {
                result = obj;
            }
        }

        if (result == null) {
            return Optional.EMPTY;
        } else {
            return new Optional(result);
        }
    }


    private record LambdaExpressionComparator(LambdaExpression le) implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            return ELSupport.coerceToNumber(null, le.invoke(o1, o2), Integer.class).intValue();
        }
    }


    private abstract static class OpIterator implements Iterator<Object> {
        protected boolean foundNext = false;
        protected Object next;

        @Override
        public boolean hasNext() {
            if (foundNext) {
                return true;
            }
            findNext();
            return foundNext;
        }

        @Override
        public Object next() {
            if (foundNext) {
                foundNext = false;
                return next;
            }
            findNext();
            if (foundNext) {
                foundNext = false;
                return next;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        protected abstract void findNext();
    }
}
