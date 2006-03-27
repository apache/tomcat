package org.apache.el.util;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ConcurrentCache<K,V> {

	private final int size;
	
	private final Map<K,V> eden;
	
	private final Map<K,V> longterm;
	
	public ConcurrentCache(int size) {
		this.size = size;
		this.eden = new ConcurrentHashMap<K,V>(size);
		this.longterm = new WeakHashMap<K,V>(size);
	}
	
	public V get(K k) {
		V v = this.eden.get(k);
		if (v == null) {
			v = this.longterm.get(k);
			if (v != null) {
				this.eden.put(k, v);
			}
		}
		return v;
	}
	
	public void put(K k, V v) {
		if (this.eden.size() >= size) {
			this.longterm.putAll(this.eden);
			this.eden.clear();
		}
		this.eden.put(k, v);
	}
}
