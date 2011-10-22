/*
 */
package org.apache.tomcat.lite.load;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadRunner {
    int tCount = 10;
    int rCount = 100;
    Thread[] threads;
    int[] ok;

    int sleepTime = 0;

    long time;
    protected AtomicInteger errors = new AtomicInteger();

    public ThreadRunner(int threads, int count) {
        tCount = threads;
        rCount = count;
        this.threads = new Thread[tCount];
        ok = new int[tCount];
    }

    public void run() {
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < tCount; i++) {
          final int j = i;
          threads[i] = new Thread(new Runnable() {
            public void run() {
              makeRequests(j);
            }
          });
          threads[i].start();
        }

        int res = 0;
        for (int i = 0; i < tCount; i++) {
          try {
            threads[i].join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
          res += ok[i];
        }
        long t1 = System.currentTimeMillis();
        time = t1 - t0;
    }

    public void makeRequests(int cnt) {
        for (int i = 0; i < rCount ; i++) {
            try {
              //System.err.println("MakeReq " + t + " " + i);
              makeRequest(cnt);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
    }

    public void makeRequest(int i) throws Exception {

    }
}