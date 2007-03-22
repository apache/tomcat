package org.apache.catalina.core;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.catalina.Executor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.LifecycleSupport;
import java.util.concurrent.RejectedExecutionException;

public class StandardThreadExecutor implements Executor {
    
    // ---------------------------------------------- Properties
    protected int threadPriority = Thread.NORM_PRIORITY;

    protected boolean daemon = true;
    
    protected String namePrefix = "tomcat-exec-";
    
    protected int maxThreads = 200;
    
    protected int minSpareThreads = 25;
    
    protected int maxIdleTime = 60000;
    
    protected ThreadPoolExecutor executor = null;
    
    protected String name;
    
    private LifecycleSupport lifecycle = new LifecycleSupport(this);
    // ---------------------------------------------- Constructors
    public StandardThreadExecutor() {
        //empty constructor for the digester
    }
    

    
    // ---------------------------------------------- Public Methods
    public void start() throws LifecycleException {
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);
        TaskQueue taskqueue = new TaskQueue();
        TaskThreadFactory tf = new TaskThreadFactory(namePrefix);
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), maxIdleTime, TimeUnit.MILLISECONDS,taskqueue, tf);
        taskqueue.setParent( (ThreadPoolExecutor) executor);
        lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);
    }
    
    public void stop() throws LifecycleException{
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        if ( executor != null ) executor.shutdown();
        executor = null;
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);
    }
    
    public void execute(Runnable command) {
        if ( executor != null ) {
            try {
                executor.execute(command);
            } catch (RejectedExecutionException rx) {
                //there could have been contention around the queue
                if ( !( (TaskQueue) executor.getQueue()).force(command) ) throw new RejectedExecutionException();
            }
        } else throw new IllegalStateException("StandardThreadPool not started.");
    }

    public int getThreadPriority() {
        return threadPriority;
    }

    public boolean isDaemon() {

        return daemon;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }

    public String getName() {
        return name;
    }

    public void setThreadPriority(int threadPriority) {
        this.threadPriority = threadPriority;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public void setMaxIdleTime(int maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Add a LifecycleEvent listener to this component.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * Get the lifecycle listeners associated with this lifecycle. If this 
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * Remove a LifecycleEvent listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    // Statistics from the thread pool
    public int getActiveCount() {
        return (executor != null) ? executor.getActiveCount() : 0;
    }

    public long getCompletedTaskCount() {
        return (executor != null) ? executor.getCompletedTaskCount() : 0;
    }

    public int getCorePoolSize() {
        return (executor != null) ? executor.getCorePoolSize() : 0;
    }

    public int getLargestPoolSize() {
        return (executor != null) ? executor.getLargestPoolSize() : 0;
    }

    public int getPoolSize() {
        return (executor != null) ? executor.getPoolSize() : 0;
    }

    // ---------------------------------------------- TaskQueue Inner Class
    class TaskQueue extends LinkedBlockingQueue<Runnable> {
        ThreadPoolExecutor parent = null;

        public TaskQueue() {
            super();
        }

        public TaskQueue(int initialCapacity) {
            super(initialCapacity);
        }

        public TaskQueue(Collection<? extends Runnable> c) {
            super(c);
        }

        public void setParent(ThreadPoolExecutor tp) {
            parent = tp;
        }
        
        public boolean force(Runnable o) {
            if ( parent.isShutdown() ) throw new RejectedExecutionException("Executor not running, can't force a command into the queue");
            return super.offer(o); //forces the item onto the queue, to be used if the task is rejected
        }

        public boolean offer(Runnable o) {
            if (parent != null && parent.getPoolSize() < parent.getMaximumPoolSize())
                return false; //force creation of new threads by rejecting the task
            else
                return super.offer(o);
        }
    }

    // ---------------------------------------------- ThreadFactory Inner Class
    class TaskThreadFactory implements ThreadFactory {
        final ThreadGroup group;
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;

        TaskThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(daemon);
            t.setPriority(getThreadPriority());
            return t;
        }
    }


}