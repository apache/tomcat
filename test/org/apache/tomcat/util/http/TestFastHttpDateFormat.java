package org.apache.tomcat.util.http;

import org.junit.Assert;
import org.junit.Test;

public class TestFastHttpDateFormat {
    @Test
    public void testGetCurrentDateInSameSecond() {
        long now = System.currentTimeMillis();
        try {
            Thread.sleep(1000L - now % 1000);
        } catch (InterruptedException e) {
        }
        now = System.currentTimeMillis();
        String s1 = FastHttpDateFormat.getCurrentDate();
        long lastMillisInSameSecond = now - now % 1000 + 900L;
        try {
            Thread.sleep(lastMillisInSameSecond - now);
        } catch (InterruptedException e) {
        }
        String s2 = FastHttpDateFormat.getCurrentDate();
        Assert.assertEquals("Two same RFC5322 format dates are expected.", s1, s2);
    }

    @Test
    public void testGetCurrentDateNextToAnotherSecond() {
        long now = System.currentTimeMillis();

        try {
            Thread.sleep(2000L - now % 1000 + 500L);
        } catch (InterruptedException e) {
        }
        now = System.currentTimeMillis();
        String s1 = FastHttpDateFormat.getCurrentDate();
        long firstMillisOfNextSecond = now - now % 1000 + 1100L;
        try {
            Thread.sleep(firstMillisOfNextSecond - now);
        } catch (InterruptedException e) {
        }

        String s2 = FastHttpDateFormat.getCurrentDate();
        Assert.assertFalse("Two different RFC5322 format dates are expected.", s1.equals(s2));
    }
}
