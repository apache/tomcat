/*  Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.lite.io.NioChannel.NioChannelCallback;

/**
 * Abstract NIO/APR to avoid some of the complexity and allow more code
 * sharing and experiments.
 *
 * SelectorThread provides non-blocking methods for read/write and generates
 * callbacks using SelectorCallback. It has no buffers of its own.
 *
 * This is non-blocking, non-buffering and uses callbacks.
 *
 * @author Costin Manolache
 */
public class NioThread implements Runnable {

  // ----------- IO handling -----------
  protected long inactivityTimeout = 5000;
  protected Thread selectorThread;


  static Logger log = Logger.getLogger("NIO");

  Selector selector;

  // will be processed in the selector thread
  List<NioChannel> readInterest = new ArrayList<NioChannel>();
  List<NioChannel> writeInterest = new ArrayList<NioChannel>();
  List<NioChannel> connectAcceptInterest = new ArrayList<NioChannel>();
  List<NioChannel> updateCallback = new ArrayList<NioChannel>();
  List<NioChannel> closeInterest = new LinkedList<NioChannel>();
  List<Runnable> runnableInterest = new ArrayList<Runnable>();

  // Statistics
  AtomicInteger opened = new AtomicInteger();
  AtomicInteger closed = new AtomicInteger();
  AtomicInteger loops = new AtomicInteger();

  AtomicInteger callbackCount = new AtomicInteger();
  AtomicLong callbackTotalTime = new AtomicLong();
  long maxCallbackTime = 0;

  // actives are also stored in the Selector. This is only updated in the main
  // thread
  public ArrayList<NioChannel> active = new ArrayList<NioChannel>();

  public static boolean debug = false;
  boolean debugWakeup = false;
  boolean running = true;

  long lastWakeup = System.currentTimeMillis(); // last time we woke
  long nextWakeup; // next scheduled wakeup

  // Normally select will wait for the next time event - if it's
  // too far in future, maxSleep will override it.
  private long maxSleep = 600000;
  long sleepTime = maxSleep;

  // Never sleep less than minSleep. This defines the resulution for
  // time events.
  private long minSleep = 100;

  boolean daemon = false;

  // TODO: trace log - record all events with timestamps, replay

  public NioThread(String name, boolean daemon) {
      try {
          selectorThread = (name == null) ? new Thread(this) :
              new Thread(this, name);

          selector = Selector.open();
          // TODO: start it on-demand, close it when not in use
          selectorThread.setDaemon(daemon);
          this.daemon = daemon;

          selectorThread.start();

      } catch(IOException e) {
          throw new RuntimeException(e);
      }
  }

  /**
   * Opened sockets, waiting for something ( close at least )
   */
  public int getOpen() {
      return opened.get();
  }

  /**
   * Closed - we're done with them.
   */
  public int getClosed() {
      return closed.get();
  }

  public int getActive() {
      return active.size();
  }

  public int getCallbacks() {
      return callbackCount.get();
  }

  public long getMaxCallbackTime() {
      return maxCallbackTime;
  }

  public long getAvgCallbackTime() {
      int cnt = callbackCount.get();
      if (cnt == 0) {
          return 0;
      }
      return callbackTotalTime.get() / cnt;
  }

  /**
   * How many times we looped
   */
  public int getLoops() {
      return loops.get();
  }

  public long getLastWakeup() {
      return lastWakeup;
  }

  public long getTimeSinceLastWakeup() {
      return System.currentTimeMillis() - lastWakeup;
  }

  /**
   * Close all resources, stop accepting, stop the thread.
   * The actual stop will happen in background.
   */
  public void stop() {
      running = false;
      if (debug) {
          log.info("Selector thread stop " + this);
      }
      selector.wakeup();
  }

  public void run() {
      int sloops = 0;
      if (debug) {
          log.info("Start NIO thread, daemon=" + daemon);
      }
      while (running) {
          // if we want timeouts - set here.
          try {
              loops.incrementAndGet();

              // Check if new requests were added
              processPending();

              // Timers
              long now = System.currentTimeMillis();
              if (nextWakeup < now) {
                  // We don't want to iterate on every I/O
                  updateSleepTimeAndProcessTimeouts(now);
              }

              int selected = selector.select(sleepTime);

              lastWakeup = System.currentTimeMillis();
              long slept = lastWakeup - now;

              if (debugWakeup && selected == 0) {
                  if (sleepTime < maxSleep - 1000) { // short wakeup
                      log.info("Wakeup " + selected + " " + slept
                              + " " + sleepTime);
                  }
              }
              if (slept < 10 && selected == 0) {
                  if (sloops > 50) {
                      sloops = 0;
                      log.severe("Looping !");
                      resetSelector();
                  }
                  sloops++;
              }

              // handle events for existing req first.
              if (selected != 0) {
                  sloops = 0;
                  int callbackCnt = 0;
                  Set<SelectionKey> sel = selector.selectedKeys();
                  Iterator<SelectionKey> i = sel.iterator();

                  while (i.hasNext()) {
                      callbackCnt++;
                      long beforeCallback = System.currentTimeMillis();
                      SelectionKey sk = i.next();
                      i.remove();

                      boolean valid = sk.isValid();
                      int readyOps = (valid) ? sk.readyOps() : 0;

                      NioChannel ch = (NioChannel) sk.attachment();
                      if (debugWakeup) {
                          log.info("Wakeup selCnt=" + selected + " slept=" + (lastWakeup - now) +
                                  " ready: " + readyOps + " v=" +
                                  sk.isValid() + " ch=" + ch);
                      }
                      if (ch == null) {
                          log.severe("Missing channel");
                          sk.cancel();
                          continue;
                      }
                      if (ch.selKey != sk) {
                          // if (ch.selKey != null) { // null if closed
                          log.severe("Invalid state, selKey doesn't match ");
                          ch.selKey = sk;
                      }
                      if (ch.channel != sk.channel()) {
                          ch.channel = sk.channel();
                          log.severe("Invalid state, channel doesn't match ");
                      }

                      if (!sk.isValid()) {
                          if (debug) {
                              log.info("!isValid, closed socket " + ch);
                          }
                          ch.close();
                          continue;
                      }

                      try {
                          int ready = sk.readyOps();
                          // callbacks
                          if (sk.isValid() && sk.isAcceptable()) {
                              handleAccept(ch, sk);
                          }

                          if (sk.isValid() && sk.isConnectable()) {
                              sk.interestOps(sk.interestOps() & ~SelectionKey.OP_CONNECT);
                              SocketChannel sc = (SocketChannel) sk.channel();
                              handleConnect(ch, sc);
                          }

                          if (sk.isValid() && sk.isWritable()) {
                              // Needs to be explicitely re-enabled by callback
                              // if more data.
                              sk.interestOps(sk.interestOps() & ~SelectionKey.OP_WRITE);
                              ch.writeInterest = false;
                              handleDataWriteable(ch);
                          }

                          if (sk.isValid() && sk.isReadable()) {
                              // Leave readable interest !
                              handleReadable(ch);
                          }

                          long callbackTime =
                              System.currentTimeMillis() - beforeCallback;

                          if (callbackTime > 250) {
                              log.warning("Callback too long ! ops=" + ready +
                                      " time=" + callbackTime + " ch=" + ch +
                                      " " + callbackCnt);
                          }
                          if (callbackTime > maxCallbackTime) {
                              maxCallbackTime = callbackTime;
                          }
                          callbackCount.incrementAndGet();
                          this.callbackTotalTime.addAndGet(callbackTime);

                      } catch (Throwable t) {
                          log.log(Level.SEVERE, "SelectorThread: Channel error, closing", t);
                          ch.lastException = t;
                          ch.close();
                      }

                  }
                  // All at once
                  sel.clear();
              }

          } catch (Throwable e) {
              log.log(Level.SEVERE, "SelectorThread: Error in select", e);
          }
      } // while(running)
      log.info("SelectorThread done");
  }

  private void log(String msg, int selected, long slept, SelectionKey sk, int readyOps) {
      log.info(msg + " " + selected
              + " " + slept
              + " ready: " + readyOps + " "
              + sk.readyOps() + " " + sk);
  }

  private void resetSelector() throws IOException, ClosedChannelException {
      // Let's close all sockets - one is bad, but we can't do much.
      Set<SelectionKey> keys = selector.keys();
      //Set<SelectionKey> keys = selector.keys();
      ArrayList<NioChannel> oldCh = new ArrayList<NioChannel>();
      ArrayList<Integer> interests = new ArrayList<Integer>();
      for (SelectionKey k : keys) {
          NioChannel cd = (NioChannel) k.attachment();
          interests.add(k.interestOps());
          oldCh.add(cd);
          k.cancel();
      }

      selector.close();
      selector = Selector.open();
      for (int i = 0; i < oldCh.size(); i++) {
          NioChannel selectorData = oldCh.get(i);
          if (selectorData == null) {
              continue;
          }
          int interest = interests.get(i);
          if (selectorData.channel instanceof ServerSocketChannel) {
              ServerSocketChannel socketChannel =
                  (ServerSocketChannel) selectorData.channel;
              selectorData.selKey = socketChannel.register(selector, SelectionKey.OP_ACCEPT);
          } else {
              SocketChannel socketChannel =
                  (SocketChannel) selectorData.channel;
              if (interest != 0) {
                  selectorData.selKey = socketChannel.register(selector,
                      interest);
              }

          }
      }
  }

  private void handleReadable(NioChannel ch) throws IOException {
      ch.lastReadResult = 0;
      if (ch.callback != null) {
          ch.callback.handleReadable(ch);
      }
      if (ch.lastReadResult != 0 && ch.readInterest && !ch.inClosed) {
          log.warning("LOOP: read interest" +
                      " after incomplete read");
          ch.close();
      }
  }

  private void handleDataWriteable(NioChannel ch) throws IOException {
      ch.lastWriteResult = 0;
      if (ch.callback != null) {
          ch.callback.handleWriteable(ch);
      }
      if (ch.lastWriteResult > 0 && ch.writeInterest) {
          log.warning("SelectorThread: write interest" +
                      " after incomplete write, LOOP");
      }
  }

  private void handleConnect(NioChannel ch, SocketChannel sc)
          throws IOException, SocketException {
      try {
          if (!sc.finishConnect()) {
              log.warning("handleConnected - finishConnect returns false");
          }
          ch.sel = this;
          //sc.socket().setSoLinger(true, 0);
          if (debug) {
              log.info("connected() " + ch + " isConnected()=" + sc.isConnected() + " " +
                      sc.isConnectionPending());
          }

          readInterest(ch, true);
      } catch (Throwable t) {
          close(ch, t);
      }
      try {
          if (ch.callback != null) {
              ch.callback.handleConnected(ch);
          }
      } catch(Throwable t1) {
          log.log(Level.WARNING, "Error in connect callback", t1);
      }

  }

  private void handleAccept(NioChannel ch, SelectionKey sk)
          throws IOException, ClosedChannelException {
      SelectableChannel selc = sk.channel();
      ServerSocketChannel ssc=(ServerSocketChannel)selc;
      SocketChannel sockC = ssc.accept();
      sockC.configureBlocking(false);

      NioChannel acceptedChannel = new NioChannel(this);
      acceptedChannel.selKey = sockC.register(selector,
              SelectionKey.OP_READ,
              acceptedChannel);
      acceptedChannel.channel = sockC;

      synchronized (active) {
          active.add(acceptedChannel);
      }

      // Find the callback for the new socket
      if (ch.callback != null) {
          // TODO: use future !
          try {
              ch.callback.handleConnected(acceptedChannel);
          } catch (Throwable t) {
              log.log(Level.SEVERE, "SelectorThread: Channel error, closing ", t);
              acceptedChannel.lastException = t;
              acceptedChannel.close();
          }
     }

      //sk.interestOps(sk.interestOps() | SelectionKey.OP_ACCEPT);
      if (debug) {
          log.info("handleAccept " + ch);
      }
  }


  public void shutdownOutput(NioChannel ch) throws IOException {
      Channel channel = ch.channel;
      if (channel instanceof SocketChannel) {
          SocketChannel sc = (SocketChannel) channel;
          if (sc.isOpen() && sc.isConnected()) {
              if (debug) {
                  log.info("Half shutdown " + ch);
              }
              sc.socket().shutdownOutput(); // TCP end to the other side
          }
      }
  }

  /**
   * Called from the IO thread
   */
  private void closeIOThread(NioChannel ch, boolean remove) {
      SelectionKey sk = (SelectionKey) ch.selKey;
      Channel channel = ch.channel;
      try {
          synchronized(closeInterest) {
              if (ch.closeCalled) {
                  if (debug) {
                      log.severe("Close called 2x ");
                  }
                  return;
              }
              ch.closeCalled = true;
              int o = opened.decrementAndGet();
              if (debug) {
                  log.info("-------------> close: " + ch + " t=" + ch.lastException);
              }
              if (sk != null) {
                  if (sk.isValid()) {
                      sk.interestOps(0);
                  }
                  sk.cancel();
                  ch.selKey = null;
              }

              if (channel instanceof SocketChannel) {
                  SocketChannel sc = (SocketChannel) channel;

                  if (sc.isConnected()) {
                      if (debug) {
                          log.info("Close socket, opened=" + o);
                      }
                      try {
                          sc.socket().shutdownInput();
                      } catch(IOException io1) {
                      }
                      try {
                          sc.socket().shutdownOutput(); // TCP end to the other side
                      } catch(IOException io1) {
                      }
                      sc.socket().close();
                  }
              }
              channel.close();

              closed.incrementAndGet();

              if (ch.callback != null) {
                  ch.callback.handleClosed(ch);
              }
              // remove from active - false only if already removed
              if (remove) {
                  synchronized (active) {
                      boolean removed = active.remove(ch);
                  }
              }
      }
      } catch (IOException ex2) {
          log.log(Level.SEVERE, "SelectorThread: Error closing socket ", ex2);
      }
  }

  // --------------- Socket op abstractions ------------

  public int readNonBlocking(NioChannel selectorData, ByteBuffer bb)
  throws IOException {
      try {
          int off = bb.position();

          int done = 0;

          done = ((SocketChannel) selectorData.channel).read(bb);

          if (debug) {
              log.info("-------------readNB rd=" + done + " bb.limit=" +
                      bb.limit() + " pos=" + bb.position() + " " + selectorData);
          }
          if (done > 0) {
              if (debug) {
                  if (!bb.isDirect()) {
                      String s = new String(bb.array(), off,
                          bb.position() - off);
                      log.info("Data:\n" + s);
                  } else {
                      log.info("Data: " + bb.toString());
                  }
              }
              selectorData.zeroReads = 0;
          } else if (done < 0) {
              if (debug) {
                  log.info("SelectorThread: EOF while reading " + selectorData);
              }
          } else {
              // need more...
              if (selectorData.lastReadResult == 0) {
                  selectorData.zeroReads++;
                  if (selectorData.zeroReads > 6) {
                      log.severe("LOOP 0 reading ");
                      selectorData.lastException = new IOException("Polling read");
                      selectorData.close();
                      return -1;
                  }
              }
          }
          selectorData.lastReadResult = done;
          return done;
      } catch(IOException ex) {
          if (debug) {
              log.info("readNB error rd=" + -1 + " bblen=" +
                      (bb.limit() - bb.position()) + " " + selectorData + " " + ex);
          }
          // common case: other side closed the connection. No need for trace
          if (ex.toString().indexOf("Connection reset by peer") < 0) {
              ex.printStackTrace();
          }
          selectorData.lastException = ex;
          selectorData.close();
          return -1;
      }
  }

  /**
   *  May be called from any thread
   */
  public int writeNonBlocking(NioChannel selectorData, ByteBuffer bb)
          throws IOException {
      try {
          if (debug) {
              log.info("writeNB pos=" + bb.position() + " len=" +
                      (bb.limit() - bb.position()) + " " + selectorData);
             if (!bb.isDirect()) {
                  String s = new String(bb.array(), bb.position(),

                      bb.limit() - bb.position());
                  log.info("Data:\n" + s);
              }
          }
          if (selectorData.writeInterest) {
              // writeInterest will be false after a callback, if it is
              // set it means we want to wait for the callback.
              if (debug) {
                  log.info("Prevent writeNB when writeInterest is set");
              }
              return 0;
          }

          int done = 0;
          done = ((SocketChannel) selectorData.channel).write(bb);
          selectorData.lastWriteResult = done;
          return done;
      } catch(IOException ex) {
          if (debug) {
              log.info("writeNB error pos=" + bb.position() + " len=" +
                      (bb.limit() - bb.position()) + " " + selectorData + " " +
                      ex);
          }
          //ex.printStackTrace();
          selectorData.lastException = ex;
          selectorData.close();
          throw ex;
          // return -1;
      }
  }

  public int getPort(NioChannel sd, boolean remote) {
      SocketChannel socketChannel = (SocketChannel) sd.channel;

      if (remote) {
          return socketChannel.socket().getPort();
      } else {
          return socketChannel.socket().getLocalPort();
      }
  }

  public InetAddress getAddress(NioChannel sd, boolean remote) {
      SocketChannel socketChannel = (SocketChannel) sd.channel;

      if (remote) {
          return socketChannel.socket().getInetAddress();
      } else {
          return socketChannel.socket().getLocalAddress();
      }
  }

  /**
   */
  public void connect(String host, int port, NioChannelCallback cstate)
          throws IOException {
      connect(new InetSocketAddress(host, port), cstate);
  }


  public void connect(SocketAddress sa, NioChannelCallback cstate)
          throws IOException {
      connect(sa, cstate, null);
  }

  public void connect(SocketAddress sa, NioChannelCallback cstate,
                      NioChannel filter)
          throws IOException {

      SocketChannel socketChannel = SocketChannel.open();
      socketChannel.configureBlocking(false);
      NioChannel selectorData = new NioChannel(this);
      selectorData.sel = this;
      selectorData.callback = cstate;
      selectorData.channel = socketChannel;
      selectorData.channel = socketChannel; // no key

      socketChannel.connect(sa);
      opened.incrementAndGet();

      synchronized (connectAcceptInterest) {
          connectAcceptInterest.add(selectorData);
      }
      selector.wakeup();
  }

  // TODO
  public void configureSocket(ByteChannel ch,
                              boolean noDelay) throws IOException {
      SocketChannel sockC = (SocketChannel) ch;
      sockC.socket().setTcpNoDelay(noDelay);
  }

  // TODO
  public void setSocketOptions(NioChannel selectorData,
                               int linger,
                               boolean tcpNoDelay,
                               int socketTimeout)
  throws IOException {

      SocketChannel socketChannel =
          (SocketChannel) selectorData.channel;
      Socket socket = socketChannel.socket();

      if(linger >= 0 )
          socket.setSoLinger( true, linger);
      if( tcpNoDelay )
          socket.setTcpNoDelay(tcpNoDelay);
      if( socketTimeout > 0 )
          socket.setSoTimeout( socketTimeout );
  }

  /**
   * Can be called from multiple threads or multiple times.
   */
  public int close(NioChannel selectorData, Throwable exception) throws IOException {
      synchronized (closeInterest) {
          if (exception != null) {
              selectorData.lastException = exception;
          }
          selectorData.readInterest = false;
          if (isSelectorThread()) {
              closeIOThread(selectorData, true);
              return 0;
          }
          if (!selectorData.inClosed) {
              closeInterest.add(selectorData);
          }
      }
      selector.wakeup();
      return 0;
  }


  public void acceptor(NioChannelCallback cstate,
                       int port,
                       InetAddress inet,
                       int backlog,
                       int serverTimeout)
  throws IOException
  {
      ServerSocketChannel ssc=ServerSocketChannel.open();
      ServerSocket serverSocket = ssc.socket();

      SocketAddress sa = null;

      if (inet == null) {
          sa = new InetSocketAddress( port );
      } else {
          sa = new InetSocketAddress(inet, port);
      }
      if (backlog > 0) {
          serverSocket.bind( sa , backlog);
      } else {
          serverSocket.bind(sa);
      }
      if( serverTimeout >= 0 ) {
          serverSocket.setSoTimeout( serverTimeout );
      }


      ssc.configureBlocking(false);

      NioChannel selectorData = new NioChannel(this);
      selectorData.channel = ssc; // no key yet
      selectorData.callback = cstate;
      // key will be set in pending

      // TODO: add SSL here

      synchronized (connectAcceptInterest) {
          connectAcceptInterest.add(selectorData);
      }
      selector.wakeup();
  }

  public void runInSelectorThread(Runnable cb) throws IOException {
      if (isSelectorThread()) {
          cb.run();
      } else {
          synchronized (runnableInterest) {
              runnableInterest.add(cb);
          }
          selector.wakeup();
      }
  }

  /**
   * Example config:
   *
   * www stream tcp wait USER  PATH_TO_tomcatInetd.sh
   *
   * For a different port, you need to add it to /etc/services.
   *
   * 'wait' is critical - the common use of inetd is 'nowait' for
   * tcp services, which doesn't make sense for java ( too slow startup
   * time ). It may make sense in future with something like android VM.
   *
   * In 'wait' mode, inetd will pass the acceptor socket to java - so
   * you can listen on port 80 and run as regular user with no special
   * code and magic.
   * If tomcat dies, inetd will get back the acceptor and on next connection
   * restart tomcat.
   *
   * This also works with xinetd. It might work with Apple launchd.
   *
   * TODO: detect inactivity for N minutes, exist - to free resources.
   */
  public void inetdAcceptor(NioChannelCallback cstate) throws IOException {
      SelectorProvider sp=SelectorProvider.provider();

      Channel ch=sp.inheritedChannel();
      if(ch!=null ) {
          log.info("Inherited: " + ch.getClass().getName());
          // blocking mode
          ServerSocketChannel ssc=(ServerSocketChannel)ch;
          ssc.configureBlocking(false);

          NioChannel selectorData = new NioChannel(this);
          selectorData.channel = ssc;
          selectorData.callback = cstate;

          synchronized (connectAcceptInterest) {
              connectAcceptInterest.add(selectorData);
          }
          selector.wakeup();
      } else {
          log.severe("No inet socket ");
          throw new IOException("Invalid inheritedChannel");
      }
  }

  // -------------- Housekeeping -------------
  /**
   *  Same as APR connector - iterate over tasks, get
   *  smallest timeout
   * @throws IOException
   */
  void updateSleepTimeAndProcessTimeouts(long now)
          throws IOException {
      long min = Long.MAX_VALUE;
      // TODO: test with large sets, maybe sort
      synchronized (active) {
          Iterator<NioChannel> activeIt = active.iterator();

          while(activeIt.hasNext()) {
              NioChannel selectorData = activeIt.next();
              if (! selectorData.channel.isOpen()) {
                  if (debug) {
                      log.info("Found closed socket, removing " +
                              selectorData.channel);
                  }
//                  activeIt.remove();
//                  selectorData.close();
              }

              long t = selectorData.nextTimeEvent;
              if (t == 0) {
                  continue;
              }
              if (t < now) {
                  // Timeout
                  if (debug) {
                      log.info("Time event " + selectorData);
                  }
                  if (selectorData.timeEvent != null) {
                      selectorData.timeEvent.run();
                  }
                  // TODO: make sure this is updated if it was selected
                  continue;
              }
              if (t < min) {
                  min = t;
              }
          }
      }
      long nextSleep = min - now;
      if (nextSleep > maxSleep) {
          sleepTime = maxSleep;
      } else if (nextSleep < minSleep) {
          sleepTime = minSleep;
      } else {
          sleepTime = nextSleep;
      }
      nextWakeup = now + sleepTime;
  }

  /**
   * Request a callback whenever data can be written.
   * When the callback is invoked, the write interest is removed ( to avoid
   * looping ). If the write() operation doesn't complete, you must call
   * writeInterest - AND stop writing, some implementations will throw
   * exception. write() will actually attempt to detect this and avoid the
   * error.
   *
   * @param sc
   */
  public void writeInterest(NioChannel selectorData) {
      // TODO: suspended ?

      SelectionKey sk = (SelectionKey) selectorData.selKey;
      if (!sk.isValid()) {
          return;
      }
      selectorData.writeInterest = true;
      int interest = sk.interestOps();
      if ((interest & SelectionKey.OP_WRITE) != 0) {
          return;
      }
      if (Thread.currentThread() == selectorThread) {
          interest =
              interest | SelectionKey.OP_WRITE;
          sk.interestOps(interest);
          if (debug) {
              log.info("Write interest " + selectorData + " i=" + interest);
          }
          return;
      }
      if (debug) {
          log.info("Pending write interest " + selectorData);
      }
      synchronized (writeInterest) {
          writeInterest.add(selectorData);
      }
      selector.wakeup();
  }


  public void readInterest(NioChannel selectorData, boolean b) throws IOException {
      if (Thread.currentThread() == selectorThread) {
          selectorData.readInterest = b;
          selThreadReadInterest(selectorData);
          return;
      }
      SelectionKey sk = (SelectionKey) selectorData.selKey;
      if (sk == null) {
          close(selectorData, null);
          return;
      }
      int interest = sk.interestOps();
      selectorData.readInterest = b;
      if (b && (interest & SelectionKey.OP_READ) != 0) {
          return;
      }
      if (!b && (interest & SelectionKey.OP_READ) == 0) {
          return;
      }
      // Schedule the interest update.
      synchronized (readInterest) {
          readInterest.add(selectorData);
      }
      if (debug) {
          log.info("Registering pending read interest");
      }
      selector.wakeup();
  }


  private void selThreadReadInterest(NioChannel selectorData) throws IOException {
      SelectionKey sk = (SelectionKey) selectorData.selKey;
      if (sk == null) {
          if (selectorData.readInterest) {
              if (debug) {
                  log.info("Register again for read interest");
              }
              SocketChannel socketChannel =
                  (SocketChannel) selectorData.channel;
              if (socketChannel.isOpen()) {
                  selectorData.sel = this;
                  selectorData.selKey =
                      socketChannel.register(selector,
                              SelectionKey.OP_READ, selectorData);
                  selectorData.channel = socketChannel;
              }
          }
          return;
      }
      if (!sk.isValid()) {
          return;
      }
      int interest = sk.interestOps();
      if (sk != null && sk.isValid()) {
          if (selectorData.readInterest) {
//              if ((interest | SelectionKey.OP_READ) != 0) {
//                  return;
//              }
              interest =
                  interest | SelectionKey.OP_READ;
          } else {
//              if ((interest | SelectionKey.OP_READ) == 0) {
//                  return;
//              }
              interest =
                  interest & ~SelectionKey.OP_READ;
          }
          if (interest == 0) {
              if (!selectorData.inClosed) {
                  new Throwable().printStackTrace();
                  log.warning("No interest(rd removed) " + selectorData);
              }
              // TODO: should we remove it ? It needs to be re-activated
              // later.
              sk.cancel(); //??
              selectorData.selKey = null;
          } else {
              sk.interestOps(interest);
          }
          if (debug) {
              log.info(((selectorData.readInterest)
                      ? "RESUME read " : "SUSPEND read ")
                      + selectorData);
          }
      }
  }


  private void processPendingConnectAccept() throws IOException {
      synchronized (connectAcceptInterest) {
          Iterator<NioChannel> ci = connectAcceptInterest.iterator();

          while (ci.hasNext()) {
              NioChannel selectorData = ci.next();

              // Find host, port - initiate connection
              try {
                  // Accept interest ?
                  if (selectorData.channel instanceof ServerSocketChannel) {
                      ServerSocketChannel socketChannel =
                          (ServerSocketChannel) selectorData.channel;
                      selectorData.sel = this;
                      selectorData.selKey =
                        socketChannel.register(selector,
                            SelectionKey.OP_ACCEPT, selectorData);

                      selectorData.channel = socketChannel;
                      synchronized (active) {
                          active.add(selectorData);
                      }
                      if (debug) {
                          log.info("Pending acceptor added: " + selectorData);
                      }
                  } else {
                      SocketChannel socketChannel =
                          (SocketChannel) selectorData.channel;
                      selectorData.sel = this;
                      selectorData.selKey =
                        socketChannel.register(selector,
                            SelectionKey.OP_CONNECT, selectorData);
                      synchronized (active) {
                          active.add(selectorData);
                      }
                      if (debug) {
                          log.info("Pending connect added: " + selectorData);
                      }
                  }
              } catch (Throwable e) {
                  log.log(Level.SEVERE, "error registering connect/accept",
                          e);
              }
          }
          connectAcceptInterest.clear();
      }
  }

  private void processPending() throws IOException {
      if (closeInterest.size() > 0) {
          synchronized (closeInterest) {
              List<NioChannel> closeList = new ArrayList(closeInterest);
              closeInterest.clear();

              Iterator<NioChannel> ci = closeList.iterator();

              while (ci.hasNext()) {
                  try {
                      NioChannel selectorData = ci.next();
                      closeIOThread(selectorData, true);
                  } catch (Throwable t) {
                      t.printStackTrace();
                  }
              }
          }
      }
      processPendingConnectAccept();
      processPendingReadWrite();

      if (runnableInterest.size() > 0) {
          synchronized (runnableInterest) {
              Iterator<Runnable> ci = runnableInterest.iterator();
              while (ci.hasNext()) {
                  Runnable cstate = ci.next();
                  try {
                      cstate.run();
                  } catch (Throwable t) {
                      t.printStackTrace();
                  }
                  if (debug) {
                      log.info("Run in selthread: " + cstate);
                  }
              }
              runnableInterest.clear();
          }
      }
      //processPendingUpdateCallback();
  }

  private void processPendingReadWrite() throws IOException {
      // Update interest
      if (readInterest.size() > 0) {
          synchronized (readInterest) {
              Iterator<NioChannel> ci = readInterest.iterator();
              while (ci.hasNext()) {
                  NioChannel cstate = ci.next();
                  selThreadReadInterest(cstate);
                  if (debug) {
                      log.info("Read interest added: " + cstate);
                  }
              }
              readInterest.clear();
          }
      }
      if (writeInterest.size() > 0) {
          synchronized (writeInterest) {
              Iterator<NioChannel> ci = writeInterest.iterator();
              while (ci.hasNext()) {
                  NioChannel cstate = ci.next();
                  // Fake callback - will update as side effect
                  handleDataWriteable(cstate);
                  if (debug) {
                      log.info("Write interest, calling dataWritable: " + cstate);
                  }
              }
              writeInterest.clear();
          }
      }
  }


  protected boolean isSelectorThread() {
      return Thread.currentThread() == selectorThread;
  }

  public static boolean isSelectorThread(IOChannel ch) {
      SocketIOChannel sc = (SocketIOChannel) ch.getFirst();
      return Thread.currentThread() == sc.ch.sel.selectorThread;
  }

}