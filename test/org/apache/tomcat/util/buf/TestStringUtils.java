package org.apache.tomcat.util.buf;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

/*
 *
 */
public class TestStringUtils {

    @Test
    public void testNullArray() {
        Assert.assertEquals("", StringUtils.join((String[]) null));
    }

    @Test
    public void testNullArrayCharStringBuilder() {
        StringBuilder sb = new StringBuilder();
        StringUtils.join((String[]) null, ',', sb);
        Assert.assertEquals("", sb.toString());
    }

    @Test
    public void testNullCollection() {
        Assert.assertEquals("", StringUtils.join((Collection<String>) null));
    }

    @Test
    public void testNullCollectionChar() {
        Assert.assertEquals("", StringUtils.join(null, ','));
    }

    @Test
    public void testNullIterableCharStringBuilder() {
        StringBuilder sb = new StringBuilder();
        StringUtils.join((Iterable<String>) null, ',', sb);
        Assert.assertEquals("", sb.toString());
    }

    @Test
    public void testNullArrayCharFunctionStringBuilder() {
        StringBuilder sb = new StringBuilder();
        StringUtils.join((String[]) null, ',', null, sb);
        Assert.assertEquals("", sb.toString());
    }

    @Test
    public void testNullIterableCharFunctionStringBuilder() {
        StringBuilder sb = new StringBuilder();
        StringUtils.join((Iterable<String>) null, ',', null, sb);
        Assert.assertEquals("", sb.toString());
    }

    // ✅ Added tests by Y Charan

    @Test
    public void testSplitCommaSeparatedOnlyCommas() {
        String[] result = StringUtils.splitCommaSeparated(",,,");
        Assert.assertArrayEquals(new String[]{}, result);
    }

    @Test
    public void testSplitCommaSeparatedMultipleCommas() {
        String[] result = StringUtils.splitCommaSeparated("a,,b");
        Assert.assertArrayEquals(new String[]{"a", "", "b"}, result);
    }

    // 🔥

    @Test
    public void testJoinCollectionWithSeparator() {
        Collection<String> input = java.util.Arrays.asList("a", "b", "c");
        String result = StringUtils.join(input, ',');

        Assert.assertEquals("a,b,c", result);
    }
}