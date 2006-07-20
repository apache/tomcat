/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.collections;

import java.util.Hashtable;

/**
 * This class implements a Generic LRU Cache
 *
 *
 * @author Ignacio J. Ortega
 *
 */

public class LRUCache
{
    class CacheNode
    {

        CacheNode prev;
        CacheNode next;
        Object value;
        Object key;

        CacheNode()
        {
        }
    }


    public LRUCache(int i)
    {
        currentSize = 0;
        cacheSize = i;
        nodes = new Hashtable(i);
    }

    public Object get(Object key)
    {
        CacheNode node = (CacheNode)nodes.get(key);
        if(node != null)
        {
            moveToHead(node);
            return node.value;
        }
        else
        {
            return null;
        }
    }

    public void put(Object key, Object value)
    {
        CacheNode node = (CacheNode)nodes.get(key);
        if(node == null)
        {
            if(currentSize >= cacheSize)
            {
                if(last != null)
                    nodes.remove(last.key);
                removeLast();
            }
            else
            {
                currentSize++;
            }
            node = new CacheNode();
        }
        node.value = value;
        node.key = key;
        moveToHead(node);
        nodes.put(key, node);
    }

    public Object remove(Object key) {
        CacheNode node = (CacheNode)nodes.get(key);
        if (node != null) {
            if (node.prev != null) {
                node.prev.next = node.next;
            }
            if (node.next != null) {
                node.next.prev = node.prev;
            }
            if (last == node)
                last = node.prev;
            if (first == node)
                first = node.next;
        }
        return node;
    }

    public void clear()
    {
        first = null;
        last = null;
    }

    private void removeLast()
    {
        if(last != null)
        {
            if(last.prev != null)
                last.prev.next = null;
            else
                first = null;
            last = last.prev;
        }
    }

    private void moveToHead(CacheNode node)
    {
        if(node == first)
            return;
        if(node.prev != null)
            node.prev.next = node.next;
        if(node.next != null)
            node.next.prev = node.prev;
        if(last == node)
            last = node.prev;
        if(first != null)
        {
            node.next = first;
            first.prev = node;
        }
        first = node;
        node.prev = null;
        if(last == null)
            last = first;
    }

    private int cacheSize;
    private Hashtable nodes;
    private int currentSize;
    private CacheNode first;
    private CacheNode last;
}
