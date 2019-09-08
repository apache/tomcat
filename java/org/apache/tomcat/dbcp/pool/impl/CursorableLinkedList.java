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

package org.apache.tomcat.dbcp.pool.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * <p>
 * This class has been copied from Commons Collections, version 3.1 in order
 * to eliminate the dependency of pool on collections.  It has package scope
 * to prevent its inclusion in the pool public API. The class declaration below
 * should *not* be changed to public.
 * </p>
 *
 * A doubly-linked list implementation of the {@link List} interface,
 * supporting a {@link ListIterator} that allows concurrent modifications
 * to the underlying list.
 * <p>
 *
 * Implements all of the optional {@link List} operations, the
 * stack/queue/dequeue operations available in {@link java.util.LinkedList}
 * and supports a {@link ListIterator} that allows concurrent modifications
 * to the underlying list (see {@link #cursor}).
 * <p>
 * <b>Note that this implementation is not synchronized.</b>
 *
 * @param <E> the type of elements held in this collection
 *
 * @see java.util.LinkedList
 *
 * @author Rodney Waldhoff
 * @author Janek Bogucki
 * @author Simon Kitching
 */
class CursorableLinkedList<E> implements List<E>, Serializable {
    /** Ensure serialization compatibility */
    private static final long serialVersionUID = 8836393098519411393L;

    //--- public methods ---------------------------------------------
    // CHECKSTYLE: stop all checks

    /**
     * Appends the specified element to the end of this list.
     *
     * @param o element to be appended to this list.
     * @return <code>true</code>
     */
    @Override
    public boolean add(E o) {
        insertListable(_head.prev(),null,o);
        return true;
    }

    /**
     * Inserts the specified element at the specified position in this list.
     * Shifts the element currently at that position (if any) and any subsequent
     *  elements to the right (adds one to their indices).
     *
     * @param index index at which the specified element is to be inserted.
     * @param element element to be inserted.
     *
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this list.
     * @throws IllegalArgumentException if some aspect of the specified
     *         element prevents it from being added to this list.
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt; size()).
     */
    @Override
    public void add(int index, E element) {
        if(index == _size) {
            add(element);
        } else {
            if(index < 0 || index > _size) {
                throw new IndexOutOfBoundsException(String.valueOf(index) + " < 0 or " + String.valueOf(index) + " > " + _size);
            }
            Listable<E> succ = (isEmpty() ? null : getListableAt(index));
            Listable<E> pred = (null == succ ? null : succ.prev());
            insertListable(pred,succ,element);
        }
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this list, in the order that they are returned by the specified
     * {@link Collection}'s {@link Iterator}.  The behavior of this operation is
     * unspecified if the specified collection is modified while
     * the operation is in progress.  (Note that this will occur if the
     * specified collection is this list, and it's nonempty.)
     *
     * @param c collection whose elements are to be added to this list.
     * @return <code>true</code> if this list changed as a result of the call.
     *
     * @throws ClassCastException if the class of an element in the specified
     *       collection prevents it from being added to this list.
     * @throws IllegalArgumentException if some aspect of an element in the
     *         specified collection prevents it from being added to this
     *         list.
     */
    @Override
    public boolean addAll(Collection<? extends E> c) {
        if(c.isEmpty()) {
            return false;
        }
        Iterator<? extends E> it = c.iterator();
        while(it.hasNext()) {
            insertListable(_head.prev(),null,it.next());
        }
        return true;
    }

    /**
     * Inserts all of the elements in the specified collection into this
     * list at the specified position.  Shifts the element currently at
     * that position (if any) and any subsequent elements to the right
     * (increases their indices).  The new elements will appear in this
     * list in the order that they are returned by the specified
     * {@link Collection}'s {@link Iterator}.  The behavior of this operation is
     * unspecified if the specified collection is modified while the
     * operation is in progress.  (Note that this will occur if the specified
     * collection is this list, and it's nonempty.)
     *
     * @param index index at which to insert first element from the specified
     *              collection.
     * @param c elements to be inserted into this list.
     * @return <code>true</code> if this list changed as a result of the call.
     *
     * @throws ClassCastException if the class of one of elements of the
     *         specified collection prevents it from being added to this
     *         list.
     * @throws IllegalArgumentException if some aspect of one of elements of
     *         the specified collection prevents it from being added to
     *         this list.
     * @throws IndexOutOfBoundsException if the index is out of range (index
     *         &lt; 0 || index &gt; size()).
     */
    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        if(c.isEmpty()) {
            return false;
        } else if(_size == index || _size == 0) {
            return addAll(c);
        } else {
            Listable<E> succ = getListableAt(index);
            Listable<E> pred = (null == succ) ? null : succ.prev();
            Iterator<? extends E> it = c.iterator();
            while(it.hasNext()) {
                pred = insertListable(pred,succ,it.next());
            }
            return true;
        }
    }

    /**
     * Inserts the specified element at the beginning of this list.
     * (Equivalent to {@link #add(int,java.lang.Object) <code>add(0,o)</code>}).
     *
     * @param o element to be prepended to this list.
     * @return <code>true</code>
     */
    public boolean addFirst(E o) {
        insertListable(null,_head.next(),o);
        return true;
    }

    /**
     * Inserts the specified element at the end of this list.
     * (Equivalent to {@link #add(java.lang.Object)}).
     *
     * @param o element to be appended to this list.
     * @return <code>true</code>
     */
    public boolean addLast(E o) {
        insertListable(_head.prev(),null,o);
        return true;
    }

    /**
     * Removes all of the elements from this list.  This
     * list will be empty after this call returns (unless
     * it throws an exception).
     */
    @Override
    public void clear() {
        /*
        // this is the quick way, but would force us
        // to break all the cursors
        _modCount++;
        _head.setNext(null);
        _head.setPrev(null);
        _size = 0;
        */
        Iterator<E> it = iterator();
        while(it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    /**
     * Returns <code>true</code> if this list contains the specified element.
     * More formally, returns <code>true</code> if and only if this list contains
     * at least one element <code>e</code> such that
     * <code>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</code>.
     *
     * @param o element whose presence in this list is to be tested.
     * @return <code>true</code> if this list contains the specified element.
     */
    @Override
    public boolean contains(Object o) {
        for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
            if((null == o && null == elt.value()) ||
               (o != null && o.equals(elt.value()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns <code>true</code> if this list contains all of the elements of the
     * specified collection.
     *
     * @param c collection to be checked for containment in this list.
     * @return <code>true</code> if this list contains all of the elements of the
     *         specified collection.
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        Iterator<?> it = c.iterator();
        while(it.hasNext()) {
            if(!this.contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a {@link ListIterator} for iterating through the
     * elements of this list. Unlike {@link #iterator}, a cursor
     * is not bothered by concurrent modifications to the
     * underlying list.
     * <p>
     * Specifically, when elements are added to the list before or
     * after the cursor, the cursor simply picks them up automatically.
     * When the "current" (i.e., last returned by {@link ListIterator#next}
     * or {@link ListIterator#previous}) element of the list is removed,
     * the cursor automatically adjusts to the change (invalidating the
     * last returned value--i.e., it cannot be removed).
     * <p>
     * Note that the returned {@link ListIterator} does not support the
     * {@link ListIterator#nextIndex} and {@link ListIterator#previousIndex}
     * methods (they throw {@link UnsupportedOperationException} when invoked.
     * <p>
     * Historical Note: In previous versions of this class, the object
     * returned from this method was required to be explicitly closed. This
     * is no longer necessary.
     *
     * @see #cursor(int)
     * @see #listIterator()
     * @see CursorableLinkedList.Cursor
     */
    public CursorableLinkedList<E>.Cursor cursor() {
        return new Cursor(0);
    }

    /**
     * Returns a {@link ListIterator} for iterating through the
     * elements of this list, initialized such that
     * {@link ListIterator#next} will return the element at
     * the specified index (if any) and {@link ListIterator#previous}
     * will return the element immediately preceding it (if any).
     * Unlike {@link #iterator}, a cursor
     * is not bothered by concurrent modifications to the
     * underlying list.
     *
     * @see #cursor()
     * @see #listIterator(int)
     * @see CursorableLinkedList.Cursor
     * @throws IndexOutOfBoundsException if the index is out of range (index
     *          &lt; 0 || index &gt; size()).
     */
    public CursorableLinkedList<E>.Cursor cursor(int i) {
        return new Cursor(i);
    }

    /**
     * Compares the specified object with this list for equality.  Returns
     * <code>true</code> if and only if the specified object is also a list, both
     * lists have the same size, and all corresponding pairs of elements in
     * the two lists are <i>equal</i>.  (Two elements <code>e1</code> and
     * <code>e2</code> are <i>equal</i> if <code>(e1==null ? e2==null :
     * e1.equals(e2))</code>.)  In other words, two lists are defined to be
     * equal if they contain the same elements in the same order.  This
     * definition ensures that the equals method works properly across
     * different implementations of the <code>List</code> interface.
     *
     * @param o the object to be compared for equality with this list.
     * @return <code>true</code> if the specified object is equal to this list.
     */
    @Override
    public boolean equals(Object o) {
        if(o == this) {
            return true;
        } else if(!(o instanceof List)) {
            return false;
        }
        Iterator<?> it = ((List<?>)o).listIterator();
        for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
            if(!it.hasNext() || (null == elt.value() ? null != it.next() : !(elt.value().equals(it.next()))) ) {
                return false;
            }
        }
        return !it.hasNext();
    }

    /**
     * Returns the element at the specified position in this list.
     *
     * @param index index of element to return.
     * @return the element at the specified position in this list.
     *
     * @throws IndexOutOfBoundsException if the index is out of range (index
     *         &lt; 0 || index &gt;= size()).
     */
    @Override
    public E get(int index) {
        return getListableAt(index).value();
    }

    /**
     * Returns the element at the beginning of this list.
     */
    public E getFirst() {
        try {
            return _head.next().value();
        } catch(NullPointerException e) {
            throw new NoSuchElementException();
        }
    }

    /**
     * Returns the element at the end of this list.
     */
    public E getLast() {
        try {
            return _head.prev().value();
        } catch(NullPointerException e) {
            throw new NoSuchElementException();
        }
    }

    /**
     * Returns the hash code value for this list.  The hash code of a list
     * is defined to be the result of the following calculation:
     * <pre>
     *  hashCode = 1;
     *  Iterator i = list.iterator();
     *  while (i.hasNext()) {
     *      Object obj = i.next();
     *      hashCode = 31*hashCode + (obj==null ? 0 : obj.hashCode());
     *  }
     * </pre>
     * This ensures that <code>list1.equals(list2)</code> implies that
     * <code>list1.hashCode()==list2.hashCode()</code> for any two lists,
     * <code>list1</code> and <code>list2</code>, as required by the general
     * contract of <code>Object.hashCode</code>.
     *
     * @return the hash code value for this list.
     * @see Object#hashCode()
     * @see Object#equals(Object)
     * @see #equals(Object)
     */
    @Override
    public int hashCode() {
        int hash = 1;
        for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
            hash = 31*hash + (null == elt.value() ? 0 : elt.value().hashCode());
        }
        return hash;
    }

    /**
     * Returns the index in this list of the first occurrence of the specified
     * element, or -1 if this list does not contain this element.
     * More formally, returns the lowest index <code>i</code> such that
     * <code>(o==null ? get(i)==null : o.equals(get(i)))</code>,
     * or -1 if there is no such index.
     *
     * @param o element to search for.
     * @return the index in this list of the first occurrence of the specified
     *         element, or -1 if this list does not contain this element.
     */
    @Override
    public int indexOf(Object o) {
        int ndx = 0;

        // perform the null check outside of the loop to save checking every
        // single time through the loop.
        if (null == o) {
            for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
                if (null == elt.value()) {
                    return ndx;
                }
                ndx++;
            }
        } else {

            for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
                if (o.equals(elt.value())) {
                    return ndx;
                }
                ndx++;
            }
        }
        return -1;
    }

    /**
     * Returns <code>true</code> if this list contains no elements.
     * @return <code>true</code> if this list contains no elements.
     */
    @Override
    public boolean isEmpty() {
        return(0 == _size);
    }

    /**
     * Returns a fail-fast iterator.
     * @see List#iterator
     */
    @Override
    public Iterator<E> iterator() {
        return listIterator(0);
    }

    /**
     * Returns the index in this list of the last occurrence of the specified
     * element, or -1 if this list does not contain this element.
     * More formally, returns the highest index <code>i</code> such that
     * <code>(o==null ? get(i)==null : o.equals(get(i)))</code>,
     * or -1 if there is no such index.
     *
     * @param o element to search for.
     * @return the index in this list of the last occurrence of the specified
     *         element, or -1 if this list does not contain this element.
     */
    @Override
    public int lastIndexOf(Object o) {
        int ndx = _size-1;

        // perform the null check outside of the loop to save checking every
        // single time through the loop.
        if (null == o) {
            for(Listable<E> elt = _head.prev(), past = null; null != elt && past != _head.next(); elt = (past = elt).prev()) {
                if (null == elt.value()) {
                    return ndx;
                }
                ndx--;
            }
        } else {
            for(Listable<E> elt = _head.prev(), past = null; null != elt && past != _head.next(); elt = (past = elt).prev()) {
                if (o.equals(elt.value())) {
                    return ndx;
                }
                ndx--;
            }
        }
        return -1;
    }

    /**
     * Returns a fail-fast ListIterator.
     * @see List#listIterator()
     */
    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    /**
     * Returns a fail-fast ListIterator.
     * @see List#listIterator(int)
     */
    @Override
    public ListIterator<E> listIterator(int index) {
        if(index<0 || index > _size) {
            throw new IndexOutOfBoundsException(index + " < 0 or > " + _size);
        }
        return new ListIter(index);
    }

    /**
     * Removes the first occurrence in this list of the specified element.
     * If this list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index i
     * such that <code>(o==null ? get(i)==null : o.equals(get(i)))</code> (if
     * such an element exists).
     *
     * @param o element to be removed from this list, if present.
     * @return <code>true</code> if this list contained the specified element.
     */
    @Override
    public boolean remove(Object o) {
        for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
            if(null == o && null == elt.value()) {
                removeListable(elt);
                return true;
            } else if(o != null && o.equals(elt.value())) {
                removeListable(elt);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the element at the specified position in this list (optional
     * operation).  Shifts any subsequent elements to the left (subtracts one
     * from their indices).  Returns the element that was removed from the
     * list.
     *
     * @param index the index of the element to removed.
     * @return the element previously at the specified position.
     *
     * @throws IndexOutOfBoundsException if the index is out of range (index
     *            &lt; 0 || index &gt;= size()).
     */
    @Override
    public E remove(int index) {
        Listable<E> elt = getListableAt(index);
        E ret = elt.value();
        removeListable(elt);
        return ret;
    }

    /**
     * Removes from this list all the elements that are contained in the
     * specified collection.
     *
     * @param c collection that defines which elements will be removed from
     *          this list.
     * @return <code>true</code> if this list changed as a result of the call.
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        if(0 == c.size() || 0 == _size) {
            return false;
        } else {
            boolean changed = false;
            Iterator<?> it = iterator();
            while(it.hasNext()) {
                if(c.contains(it.next())) {
                    it.remove();
                    changed = true;
                }
            }
            return changed;
        }
    }

    /**
     * Removes the first element of this list, if any.
     */
    public E removeFirst() {
        if(_head.next() != null) {
            E val = _head.next().value();
            removeListable(_head.next());
            return val;
        } else {
            throw new NoSuchElementException();
        }
    }

    /**
     * Removes the last element of this list, if any.
     */
    public E removeLast() {
        if(_head.prev() != null) {
            E val = _head.prev().value();
            removeListable(_head.prev());
            return val;
        } else {
            throw new NoSuchElementException();
        }
    }

    /**
     * Retains only the elements in this list that are contained in the
     * specified collection.  In other words, removes
     * from this list all the elements that are not contained in the specified
     * collection.
     *
     * @param c collection that defines which elements this set will retain.
     *
     * @return <code>true</code> if this list changed as a result of the call.
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        Iterator<?> it = iterator();
        while(it.hasNext()) {
            if(!c.contains(it.next())) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     *
     * @param index index of element to replace.
     * @param element element to be stored at the specified position.
     * @return the element previously at the specified position.
     *
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this list.
     * @throws IllegalArgumentException if some aspect of the specified
     *         element prevents it from being added to this list.
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index &lt; 0 || index &gt;= size()).
     */
    @Override
    public E set(int index, E element) {
        Listable<E> elt = getListableAt(index);
        E val = elt.setValue(element);
        broadcastListableChanged(elt);
        return val;
    }

    /**
     * Returns the number of elements in this list.
     * @return the number of elements in this list.
     */
    @Override
    public int size() {
        return _size;
    }

    /**
     * Returns an array containing all of the elements in this list in proper
     * sequence.  Obeys the general contract of the {@link Collection#toArray()} method.
     *
     * @return an array containing all of the elements in this list in proper
     *         sequence.
     */
    @Override
    public Object[] toArray() {
        Object[] array = new Object[_size];
        int i = 0;
        for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
            array[i++] = elt.value();
        }
        return array;
    }

    /**
     * Returns an array containing all of the elements in this list in proper
     * sequence; the runtime type of the returned array is that of the
     * specified array. Obeys the general contract of the
     * {@link Collection#toArray()} method.
     *
     * @param a      the array into which the elements of this list are to
     *               be stored, if it is big enough; otherwise, a new array of the
     *               same runtime type is allocated for this purpose.
     * @return an array containing the elements of this list.
     * @exception ArrayStoreException
     *                   if the runtime type of the specified array
     *                   is not a supertype of the runtime type of every element in
     *                   this list.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T a[]) {
        if(a.length < _size) {
            a = (T[])Array.newInstance(a.getClass().getComponentType(), _size);
        }
        int i = 0;
        for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
            a[i++] = (T) elt.value();
        }
        if(a.length > _size) {
            a[_size] = null; // should we null out the rest of the array also? java.util.LinkedList doesn't
        }
        return a;
    }

    /**
     * Returns a {@link String} representation of this list, suitable for debugging.
     * @return a {@link String} representation of this list, suitable for debugging.
     */
    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("[");
        for(Listable<E> elt = _head.next(), past = null; null != elt && past != _head.prev(); elt = (past = elt).next()) {
            if(_head.next() != elt) {
                buf.append(", ");
            }
            buf.append(elt.value());
        }
        buf.append("]");
        return buf.toString();
    }

    /**
     * Returns a fail-fast sublist.
     * @see List#subList(int,int)
     */
    @Override
    public List<E> subList(int i, int j) {
        if(i < 0 || j > _size || i > j) {
            throw new IndexOutOfBoundsException();
        } else if(i == 0 && j == _size) {
            return this;
        } else {
            return new CursorableSubList<E>(this,i,j);
        }
    }

    //--- protected methods ------------------------------------------

    /**
     * Inserts a new <i>value</i> into my
     * list, after the specified <i>before</i> element, and before the
     * specified <i>after</i> element
     *
     * @return the newly created
     * {@link org.apache.tomcat.dbcp.pool.impl.CursorableLinkedList.Listable}
     */
    protected Listable<E> insertListable(Listable<E> before, Listable<E> after, E value) {
        _modCount++;
        _size++;
        Listable<E> elt = new Listable<E>(before,after,value);
        if(null != before) {
            before.setNext(elt);
        } else {
            _head.setNext(elt);
        }

        if(null != after) {
            after.setPrev(elt);
        } else {
            _head.setPrev(elt);
        }
        broadcastListableInserted(elt);
        return elt;
    }

    /**
     * Removes the given
     * {@link org.apache.tomcat.dbcp.pool.impl.CursorableLinkedList.Listable}
     * from my list.
     */
    protected void removeListable(Listable<E> elt) {
        _modCount++;
        _size--;
        if(_head.next() == elt) {
            _head.setNext(elt.next());
        }
        if(null != elt.next()) {
            elt.next().setPrev(elt.prev());
        }
        if(_head.prev() == elt) {
            _head.setPrev(elt.prev());
        }
        if(null != elt.prev()) {
            elt.prev().setNext(elt.next());
        }
        broadcastListableRemoved(elt);
    }

    /**
     * Returns the
     * {@link org.apache.tomcat.dbcp.pool.impl.CursorableLinkedList.Listable}
     * at the specified index.
     *
     * @throws IndexOutOfBoundsException if index is less than zero or
     *         greater than or equal to the size of this list.
     */
    protected Listable<E> getListableAt(int index) {
        if(index < 0 || index >= _size) {
            throw new IndexOutOfBoundsException(String.valueOf(index) + " < 0 or " + String.valueOf(index) + " >= " + _size);
        }
        if(index <=_size/2) {
            Listable<E> elt = _head.next();
            for(int i = 0; i < index; i++) {
                elt = elt.next();
            }
            return elt;
        } else {
            Listable<E> elt = _head.prev();
            for(int i = (_size-1); i > index; i--) {
                elt = elt.prev();
            }
            return elt;
        }
    }

    /**
     * Registers a {@link CursorableLinkedList.Cursor} to be notified
     * of changes to this list.
     */
    protected void registerCursor(Cursor cur) {
        // We take this opportunity to clean the _cursors list
        // of WeakReference objects to garbage-collected cursors.
        for (Iterator<WeakReference<Cursor>> it = _cursors.iterator(); it.hasNext(); ) {
            WeakReference<Cursor> ref = it.next();
            if (ref.get() == null) {
                it.remove();
            }
        }

        _cursors.add( new WeakReference<Cursor>(cur) );
    }

    /**
     * Removes a {@link CursorableLinkedList.Cursor} from
     * the set of cursors to be notified of changes to this list.
     */
    protected void unregisterCursor(Cursor cur) {
        for (Iterator<WeakReference<Cursor>> it = _cursors.iterator(); it.hasNext(); ) {
            WeakReference<Cursor> ref = it.next();
            Cursor cursor = ref.get();
            if (cursor == null) {
                // some other unrelated cursor object has been
                // garbage-collected; let's take the opportunity to
                // clean up the cursors list anyway..
                it.remove();

            } else if (cursor == cur) {
                ref.clear();
                it.remove();
                break;
            }
        }
    }

    /**
     * Informs all of my registered cursors that they are now
     * invalid.
     */
    protected void invalidateCursors() {
        Iterator<WeakReference<Cursor>> it = _cursors.iterator();
        while (it.hasNext()) {
            WeakReference<Cursor> ref = it.next();
            Cursor cursor = ref.get();
            if (cursor != null) {
                // cursor is null if object has been garbage-collected
                cursor.invalidate();
                ref.clear();
            }
            it.remove();
        }
    }

    /**
     * Informs all of my registered cursors that the specified
     * element was changed.
     * @see #set(int,java.lang.Object)
     */
    protected void broadcastListableChanged(Listable<E> elt) {
        Iterator<WeakReference<Cursor>> it = _cursors.iterator();
        while (it.hasNext()) {
            WeakReference<Cursor> ref = it.next();
            Cursor cursor = ref.get();
            if (cursor == null) {
                it.remove(); // clean up list
            } else {
                cursor.listableChanged(elt);
            }
        }
    }

    /**
     * Informs all of my registered cursors that the specified
     * element was just removed from my list.
     */
    protected void broadcastListableRemoved(Listable<E> elt) {
        Iterator<WeakReference<Cursor>> it = _cursors.iterator();
        while (it.hasNext()) {
            WeakReference<Cursor> ref = it.next();
            Cursor cursor = ref.get();
            if (cursor == null) {
                it.remove(); // clean up list
            } else {
                cursor.listableRemoved(elt);
            }
        }
    }

    /**
     * Informs all of my registered cursors that the specified
     * element was just added to my list.
     */
    protected void broadcastListableInserted(Listable<E> elt) {
        Iterator<WeakReference<Cursor>> it = _cursors.iterator();
        while (it.hasNext()) {
            WeakReference<Cursor> ref = it.next();
            Cursor cursor = ref.get();
            if (cursor == null) {
                it.remove();  // clean up list
            } else {
                cursor.listableInserted(elt);
            }
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(_size);
        Listable<E> cur = _head.next();
        while (cur != null) {
            out.writeObject(cur.value());
            cur = cur.next();
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        _size = 0;
        _modCount = 0;
        _cursors = new ArrayList<WeakReference<Cursor>>();
        _head = new Listable<E>(null,null,null);
        int size = in.readInt();
        for (int i=0;i<size;i++) {
            this.add((E)in.readObject());
        }
    }

    //--- protected attributes ---------------------------------------

    /** The number of elements in me. */
    protected transient int _size = 0;

    /**
     * A sentry node.
     * <p>
     * <code>_head.next()</code> points to the first element in the list,
     * <code>_head.prev()</code> to the last. Note that it is possible for
     * <code>_head.next().prev()</code> and <code>_head.prev().next()</code> to be
     * non-null, as when I am a sublist for some larger list.
     * Use <code>== _head.next()</code> and <code>== _head.prev()</code> to determine
     * if a given
     * {@link org.apache.tomcat.dbcp.pool.impl.CursorableLinkedList.Listable}
     * is the first or last element in the list.
     */
    protected transient Listable<E> _head = new Listable<E>(null,null,null);

    /** Tracks the number of structural modifications to me. */
    protected transient int _modCount = 0;

    /**
     * A list of the currently {@link CursorableLinkedList.Cursor}s currently
     * open in this list.
     */
    protected transient List<WeakReference<Cursor>> _cursors = new ArrayList<WeakReference<Cursor>>();

    //--- inner classes ----------------------------------------------

    static class Listable<E> implements Serializable {

        /**
         * Generated serial version ID.
         */
        private static final long serialVersionUID = -7657211971569732521L;
        private Listable<E> _prev = null;
        private Listable<E> _next = null;
        private E _val = null;

        Listable(Listable<E> prev, Listable<E> next, E val) {
            _prev = prev;
            _next = next;
            _val = val;
        }

        Listable<E> next() {
            return _next;
        }

        Listable<E> prev() {
            return _prev;
        }

        E value() {
            return _val;
        }

        void setNext(Listable<E> next) {
            _next = next;
        }

        void setPrev(Listable<E> prev) {
            _prev = prev;
        }

        E setValue(E val) {
            E temp = _val;
            _val = val;
            return temp;
        }
    }

    class ListIter implements ListIterator<E> {
        Listable<E> _cur = null;
        Listable<E> _lastReturned = null;
        int _expectedModCount = _modCount;
        int _nextIndex = 0;

        ListIter(int index) {
            if(index == 0) {
                _cur = new Listable<E>(null,_head.next(),null);
                _nextIndex = 0;
            } else if(index == _size) {
                _cur = new Listable<E>(_head.prev(),null,null);
                _nextIndex = _size;
            } else {
                Listable<E> temp = getListableAt(index);
                _cur = new Listable<E>(temp.prev(),temp,null);
                _nextIndex = index;
            }
        }

        @Override
        public E previous() {
            checkForComod();
            if(!hasPrevious()) {
                throw new NoSuchElementException();
            } else {
                E ret = _cur.prev().value();
                _lastReturned = _cur.prev();
                _cur.setNext(_cur.prev());
                _cur.setPrev(_cur.prev().prev());
                _nextIndex--;
                return ret;
            }
        }

        @Override
        public boolean hasNext() {
            checkForComod();
            return(null != _cur.next() && _cur.prev() != _head.prev());
        }

        @Override
        public E next() {
            checkForComod();
            if(!hasNext()) {
                throw new NoSuchElementException();
            } else {
                E ret = _cur.next().value();
                _lastReturned = _cur.next();
                _cur.setPrev(_cur.next());
                _cur.setNext(_cur.next().next());
                _nextIndex++;
                return ret;
            }
        }

        @Override
        public int previousIndex() {
            checkForComod();
            if(!hasPrevious()) {
                return -1;
            }
            return _nextIndex-1;
        }

        @Override
        public boolean hasPrevious() {
            checkForComod();
            return(null != _cur.prev() && _cur.next() != _head.next());
        }

        @Override
        public void set(E o) {
            checkForComod();
            try {
                _lastReturned.setValue(o);
            } catch(NullPointerException e) {
                throw new IllegalStateException();
            }
        }

        @Override
        public int nextIndex() {
            checkForComod();
            if(!hasNext()) {
                return size();
            }
            return _nextIndex;
        }

        @Override
        public void remove() {
            checkForComod();
            if(null == _lastReturned) {
                throw new IllegalStateException();
            } else {
                _cur.setNext(_lastReturned == _head.prev() ? null : _lastReturned.next());
                _cur.setPrev(_lastReturned == _head.next() ? null : _lastReturned.prev());
                removeListable(_lastReturned);
                _lastReturned = null;
                _nextIndex--;
                _expectedModCount++;
            }
        }

        @Override
        public void add(E o) {
            checkForComod();
            _cur.setPrev(insertListable(_cur.prev(),_cur.next(),o));
            _lastReturned = null;
            _nextIndex++;
            _expectedModCount++;
        }

        protected void checkForComod() {
            if(_expectedModCount != _modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    public class Cursor extends ListIter {
        boolean _valid = false;

        Cursor(int index) {
            super(index);
            _valid = true;
            registerCursor(this);
        }

        @Override
        public int previousIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(E o) {
            checkForComod();
            Listable<E> elt = insertListable(_cur.prev(),_cur.next(),o);
            _cur.setPrev(elt);
            _cur.setNext(elt.next());
            _lastReturned = null;
            _nextIndex++;
            _expectedModCount++;
        }

        protected void listableRemoved(Listable<E> elt) {
            if(null == _head.prev()) {
                _cur.setNext(null);
            } else if(_cur.next() == elt) {
                _cur.setNext(elt.next());
            }
            if(null == _head.next()) {
                _cur.setPrev(null);
            } else if(_cur.prev() == elt) {
                _cur.setPrev(elt.prev());
            }
            if(_lastReturned == elt) {
                _lastReturned = null;
            }
        }

        protected void listableInserted(Listable<E> elt) {
            if(null == _cur.next() && null == _cur.prev()) {
                _cur.setNext(elt);
            } else if(_cur.prev() == elt.prev()) {
                _cur.setNext(elt);
            }
            if(_cur.next() == elt.next()) {
                _cur.setPrev(elt);
            }
            if(_lastReturned == elt) {
                _lastReturned = null;
            }
        }

        protected void listableChanged(Listable<E> elt) {
            if(_lastReturned == elt) {
                _lastReturned = null;
            }
        }

        @Override
        protected void checkForComod() {
            if(!_valid) {
                throw new ConcurrentModificationException();
            }
        }

        protected void invalidate() {
            _valid = false;
        }

        /**
         * Mark this cursor as no longer being needed. Any resources
         * associated with this cursor are immediately released.
         * In previous versions of this class, it was mandatory to close
         * all cursor objects to avoid memory leaks. It is <i>no longer</i>
         * necessary to call this close method; an instance of this class
         * can now be treated exactly like a normal iterator.
         */
        public void close() {
            if(_valid) {
                _valid = false;
                unregisterCursor(this);
            }
        }
    }

}

class CursorableSubList<E> extends CursorableLinkedList<E> {

    //--- constructors -----------------------------------------------

    /**
     * Generated serial version ID.
     */
    private static final long serialVersionUID = 5253505752202921312L;

    CursorableSubList(CursorableLinkedList<E> list, int from, int to) {
        if(0 > from || list.size() < to) {
            throw new IndexOutOfBoundsException();
        } else if(from > to) {
            throw new IllegalArgumentException();
        }
        _list = list;
        if(from < list.size()) {
            _head.setNext(_list.getListableAt(from));
            _pre = (null == _head.next()) ? null : _head.next().prev();
        } else {
            _pre = _list.getListableAt(from-1);
        }
        if(from == to) {
            _head.setNext(null);
            _head.setPrev(null);
            if(to < list.size()) {
                _post = _list.getListableAt(to);
            } else {
                _post = null;
            }
        } else {
            _head.setPrev(_list.getListableAt(to-1));
            _post = _head.prev().next();
        }
        _size = to - from;
        _modCount = _list._modCount;
    }

    //--- public methods ------------------------------------------

    @Override
    public void clear() {
        checkForComod();
        Iterator<E> it = iterator();
        while(it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    @Override
    public Iterator<E> iterator() {
        checkForComod();
        return super.iterator();
    }

    @Override
    public int size() {
        checkForComod();
        return super.size();
    }

    @Override
    public boolean isEmpty() {
        checkForComod();
        return super.isEmpty();
    }

    @Override
    public Object[] toArray() {
        checkForComod();
        return super.toArray();
    }

    @Override
    public <T> T[] toArray(T a[]) {
        checkForComod();
        return super.toArray(a);
    }

    @Override
    public boolean contains(Object o) {
        checkForComod();
        return super.contains(o);
    }

    @Override
    public boolean remove(Object o) {
        checkForComod();
        return super.remove(o);
    }

    @Override
    public E removeFirst() {
        checkForComod();
        return super.removeFirst();
    }

    @Override
    public E removeLast() {
        checkForComod();
        return super.removeLast();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        checkForComod();
        return super.addAll(c);
    }

    @Override
    public boolean add(E o) {
        checkForComod();
        return super.add(o);
    }

    @Override
    public boolean addFirst(E o) {
        checkForComod();
        return super.addFirst(o);
    }

    @Override
    public boolean addLast(E o) {
        checkForComod();
        return super.addLast(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        checkForComod();
        return super.removeAll(c);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        checkForComod();
        return super.containsAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        checkForComod();
        return super.addAll(index,c);
    }

    @Override
    public int hashCode() {
        checkForComod();
        return super.hashCode();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        checkForComod();
        return super.retainAll(c);
    }

    @Override
    public E set(int index, E element) {
        checkForComod();
        return super.set(index,element);
    }

    @Override
    public boolean equals(Object o) {
        checkForComod();
        return super.equals(o);
    }

    @Override
    public E get(int index) {
        checkForComod();
        return super.get(index);
    }

    @Override
    public E getFirst() {
        checkForComod();
        return super.getFirst();
    }

    @Override
    public E getLast() {
        checkForComod();
        return super.getLast();
    }

    @Override
    public void add(int index, E element) {
        checkForComod();
        super.add(index,element);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        checkForComod();
        return super.listIterator(index);
    }

    @Override
    public E remove(int index) {
        checkForComod();
        return super.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        checkForComod();
        return super.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        checkForComod();
        return super.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        checkForComod();
        return super.listIterator();
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        checkForComod();
        return super.subList(fromIndex,toIndex);
    }

    //--- protected methods ------------------------------------------

    /**
     * Inserts a new <i>value</i> into my
     * list, after the specified <i>before</i> element, and before the
     * specified <i>after</i> element
     *
     * @return the newly created {@link CursorableLinkedList.Listable}
     */
    @Override
    protected Listable<E> insertListable(Listable<E> before, Listable<E> after, E value) {
        _modCount++;
        _size++;
        Listable<E> elt = _list.insertListable((null == before ? _pre : before), (null == after ? _post : after),value);
        if(null == _head.next()) {
            _head.setNext(elt);
            _head.setPrev(elt);
        }
        if(before == _head.prev()) {
            _head.setPrev(elt);
        }
        if(after == _head.next()) {
            _head.setNext(elt);
        }
        broadcastListableInserted(elt);
        return elt;
    }

    /**
     * Removes the given {@link CursorableLinkedList.Listable} from my list.
     */
    @Override
    protected void removeListable(Listable<E> elt) {
        _modCount++;
        _size--;
        if(_head.next() == elt && _head.prev() == elt) {
            _head.setNext(null);
            _head.setPrev(null);
        }
        if(_head.next() == elt) {
            _head.setNext(elt.next());
        }
        if(_head.prev() == elt) {
            _head.setPrev(elt.prev());
        }
        _list.removeListable(elt);
        broadcastListableRemoved(elt);
    }

    /**
     * Test to see if my underlying list has been modified
     * by some other process.  If it has, throws a
     * {@link ConcurrentModificationException}, otherwise
     * quietly returns.
     *
     * @throws ConcurrentModificationException
     */
    protected void checkForComod() throws ConcurrentModificationException {
        if(_modCount != _list._modCount) {
            throw new ConcurrentModificationException();
        }
    }

    //--- protected attributes ---------------------------------------

    /** My underlying list */
    protected CursorableLinkedList<E> _list = null;

    /** The element in my underlying list preceding the first element in my list. */
    protected Listable<E> _pre = null;

    /** The element in my underlying list following the last element in my list. */
    protected Listable<E> _post = null;

}
