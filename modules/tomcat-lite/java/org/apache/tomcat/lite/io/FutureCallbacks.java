/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;



/**
 * Support for blocking calls and callbacks.
 *
 * Unlike FutureTask, it is possible to reuse this and hopefully
 * easier to extends. Also has callbacks.
 *
 * @author Costin Manolache
 */
public class FutureCallbacks<V> implements Future<V> {

    // Other options: ReentrantLock uses AbstractQueueSynchronizer,
    // more complex. Same for CountDownLatch
    // FutureTask - uses Sync as well, ugly interface with
    // Callable, can't be recycled.
    // Mina: simple object lock, doesn't extend java.util.concurent.Future

    private Sync sync = new Sync();

    private V value;

    public static interface Callback<V> {
        public void run(V param);
    }

    private List<Callback<V>> callbacks = new ArrayList();

    public FutureCallbacks() {
    }

    /**
     * Unlocks the object if it was locked. Should be called
     * when the object is reused.
     *
     * Callbacks will not be invoked.
     */
    public void reset() {
        sync.releaseShared(0);
        sync.reset();
    }

    public void recycle() {
        callbacks.clear();
        sync.releaseShared(0);
        sync.reset();
    }

    /**
     * Unlocks object and calls the callbacks.
     * @param v
     *
     * @throws IOException
     */
    public void signal(V v) throws IOException {
        sync.releaseShared(0);
        onSignal(v);
    }

    protected boolean isSignaled() {
        return true;
    }

    /**
     * Override to call specific callbacks
     */
    protected void onSignal(V v) {
        for (Callback<V> cb: callbacks) {
            if (cb != null) {
                cb.run(v);
            }
        }
    }

    /**
     * Set the response. Will cause the callback to be called and lock to be
     * released.
     *
     * @param value
     * @throws IOException
     */
    public void setValue(V value) throws IOException {
        synchronized (this) {
            this.value = value;
            signal(value);
        }
    }

    public void waitSignal(long to) throws IOException {
        try {
            get(to, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e1) {
            throw new WrappedException(e1);
        } catch (TimeoutException e1) {
            throw new WrappedException(e1);
        } catch (ExecutionException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        sync.acquireSharedInterruptibly(0);
        return value;
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        if (!sync.tryAcquireSharedNanos(0, unit.toNanos(timeout))) {
            throw new TimeoutException("Waiting " + timeout);
        }
        return value;
    }


    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return sync.isSignaled();
    }

    private class Sync extends AbstractQueuedSynchronizer {

        static final int DONE = 1;
        static final int BLOCKED = 0;
        Object result;
        Throwable t;

        @Override
        protected int tryAcquireShared(int ignore) {
            return getState() == DONE ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int ignore) {
            setState(DONE);
            return true;
        }

        public void reset() {
            setState(BLOCKED);
        }

        boolean isSignaled() {
            return getState() == DONE;
        }
    }
}
