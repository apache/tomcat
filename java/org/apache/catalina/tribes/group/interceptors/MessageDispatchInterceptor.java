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
package org.apache.catalina.tribes.group.interceptors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.ErrorHandler;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.util.ExecutorFactory;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.catalina.tribes.util.TcclThreadFactory;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * The message dispatcher is a way to enable asynchronous communication
 * through a channel. The dispatcher will look for the
 * <code>Channel.SEND_OPTIONS_ASYNCHRONOUS</code> flag to be set, if it is, it
 * will queue the message for delivery and immediately return to the sender.
 */
public class MessageDispatchInterceptor extends ChannelInterceptorBase
        implements MessageDispatchInterceptorMBean {

    private static final Log log = LogFactory.getLog(MessageDispatchInterceptor.class);
    protected static final StringManager sm =
            StringManager.getManager(MessageDispatchInterceptor.class);

    protected long maxQueueSize = 1024*1024*64; //64MB
    protected volatile boolean run = false;
    protected boolean useDeepClone = true;
    protected boolean alwaysSend = true;

    protected final AtomicLong currentSize = new AtomicLong(0);
    protected ExecutorService executor = null;
    protected int maxThreads = 10;
    protected int maxSpareThreads = 2;
    protected long keepAliveTime = 5000;


    public MessageDispatchInterceptor() {
        setOptionFlag(Channel.SEND_OPTIONS_ASYNCHRONOUS);
    }


    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        boolean async = (msg.getOptions() &
                Channel.SEND_OPTIONS_ASYNCHRONOUS) == Channel.SEND_OPTIONS_ASYNCHRONOUS;
        if (async && run) {
            if ((getCurrentSize()+msg.getMessage().getLength()) > maxQueueSize) {
                if (alwaysSend) {
                    super.sendMessage(destination,msg,payload);
                    return;
                } else {
                    throw new ChannelException(sm.getString("messageDispatchInterceptor.queue.full",
                            Long.toString(maxQueueSize), Long.toString(getCurrentSize())));
                }
            }
            //add to queue
            if (useDeepClone) {
                msg = (ChannelMessage)msg.deepclone();
            }
            if (!addToQueue(msg, destination, payload)) {
                throw new ChannelException(
                        sm.getString("messageDispatchInterceptor.unableAdd.queue"));
            }
            addAndGetCurrentSize(msg.getMessage().getLength());
        } else {
            super.sendMessage(destination, msg, payload);
        }
    }


    public boolean addToQueue(final ChannelMessage msg, final Member[] destination,
            final InterceptorPayload payload) {
        executor.execute(() -> sendAsyncData(msg, destination, payload));
        return true;
    }


    public void startQueue() {
        if (run) {
            return;
        }
        String channelName = "";
        if (getChannel().getName() != null) {
            channelName = "[" + getChannel().getName() + "]";
        }
        executor = ExecutorFactory.newThreadPool(maxSpareThreads, maxThreads, keepAliveTime,
                TimeUnit.MILLISECONDS,
                new TcclThreadFactory("MessageDispatchInterceptor.MessageDispatchThread" + channelName));
        run = true;
    }


    public void stopQueue() {
        run = false;
        executor.shutdownNow();
        setAndGetCurrentSize(0);
    }


    @Override
    public void setOptionFlag(int flag) {
        if ( flag != Channel.SEND_OPTIONS_ASYNCHRONOUS ) {
            log.warn(sm.getString("messageDispatchInterceptor.warning.optionflag"));
        }
        super.setOptionFlag(flag);
    }


    public void setMaxQueueSize(long maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }


    public void setUseDeepClone(boolean useDeepClone) {
        this.useDeepClone = useDeepClone;
    }

    @Override
    public long getMaxQueueSize() {
        return maxQueueSize;
    }


    public boolean getUseDeepClone() {
        return useDeepClone;
    }

    @Override
    public long getCurrentSize() {
        return currentSize.get();
    }


    public long addAndGetCurrentSize(long inc) {
        return currentSize.addAndGet(inc);
    }


    public long setAndGetCurrentSize(long value) {
        currentSize.set(value);
        return value;
    }

    @Override
    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    @Override
    public int getMaxSpareThreads() {
        return maxSpareThreads;
    }

    @Override
    public int getMaxThreads() {
        return maxThreads;
    }


    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }


    public void setMaxSpareThreads(int maxSpareThreads) {
        this.maxSpareThreads = maxSpareThreads;
    }


    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    @Override
    public boolean isAlwaysSend() {
        return alwaysSend;
    }


    @Override
    public void setAlwaysSend(boolean alwaysSend) {
        this.alwaysSend = alwaysSend;
    }


    @Override
    public void start(int svc) throws ChannelException {
        //start the thread
        if (!run ) {
            synchronized (this) {
                // only start with the sender
                if ( !run && ((svc & Channel.SND_TX_SEQ)==Channel.SND_TX_SEQ) ) {
                    startQueue();
                }
            }
        }
        super.start(svc);
    }


    @Override
    public void stop(int svc) throws ChannelException {
        //stop the thread
        if (run) {
            synchronized (this) {
                if ( run && ((svc & Channel.SND_TX_SEQ)==Channel.SND_TX_SEQ)) {
                    stopQueue();
                }
            }
        }

        super.stop(svc);
    }


    protected void sendAsyncData(ChannelMessage msg, Member[] destination,
            InterceptorPayload payload) {
        ErrorHandler handler = null;
        if (payload != null) {
            handler = payload.getErrorHandler();
        }
        try {
            super.sendMessage(destination, msg, null);
            try {
                if (handler != null) {
                    handler.handleCompletion(new UniqueId(msg.getUniqueId()));
                }
            } catch ( Exception ex ) {
                log.error(sm.getString("messageDispatchInterceptor.completeMessage.failed"),ex);
            }
        } catch ( Exception x ) {
            ChannelException cx = null;
            if (x instanceof ChannelException) {
                cx = (ChannelException) x;
            } else {
                cx = new ChannelException(x);
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("messageDispatchInterceptor.AsyncMessage.failed"),x);
            }
            try {
                if (handler != null) {
                    handler.handleError(cx, new UniqueId(msg.getUniqueId()));
                }
            } catch ( Exception ex ) {
                log.error(sm.getString("messageDispatchInterceptor.errorMessage.failed"),ex);
            }
        } finally {
            addAndGetCurrentSize(-msg.getMessage().getLength());
        }
    }

    // ---------------------------------------------- stats of the thread pool
    /**
     * Return the current number of threads that are managed by the pool.
     * @return the current number of threads that are managed by the pool
     */
    @Override
    public int getPoolSize() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getPoolSize();
        } else {
            return -1;
        }
    }

    /**
     * Return the current number of threads that are in use.
     * @return the current number of threads that are in use
     */
    @Override
    public int getActiveCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getActiveCount();
        } else {
            return -1;
        }
    }

    /**
     * Return the total number of tasks that have ever been scheduled for execution by the pool.
     * @return the total number of tasks that have ever been scheduled for execution by the pool
     */
    @Override
    public long getTaskCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getTaskCount();
        } else {
            return -1;
        }
    }

    /**
     * Return the total number of tasks that have completed execution by the pool.
     * @return the total number of tasks that have completed execution by the pool
     */
    @Override
    public long getCompletedTaskCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getCompletedTaskCount();
        } else {
            return -1;
        }
    }

}
