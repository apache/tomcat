package org.apache.tomcat.util.http;

import org.junit.Test;

import org.junit.Assert;

public class TestSameSiteCookies {

    @Test
    public void testNone() {
        SameSiteCookies attribute = SameSiteCookies.NONE;

        Assert.assertEquals("None", attribute.getValue());
        Assert.assertEquals(SameSiteCookies.NONE, attribute);

        Assert.assertNotEquals(SameSiteCookies.LAX, attribute);
        Assert.assertNotEquals(SameSiteCookies.STRICT, attribute);
    }

    @Test
    public void testLax() {
        SameSiteCookies attribute = SameSiteCookies.LAX;

        Assert.assertEquals("Lax", attribute.getValue());
        Assert.assertEquals(SameSiteCookies.LAX, attribute);

        Assert.assertNotEquals(SameSiteCookies.NONE, attribute);
        Assert.assertNotEquals(SameSiteCookies.STRICT, attribute);
    }

    @Test
    public void testStrict() {
        SameSiteCookies attribute = SameSiteCookies.STRICT;

        Assert.assertEquals("Strict", attribute.getValue());
        Assert.assertEquals(SameSiteCookies.STRICT, attribute);

        Assert.assertNotEquals(SameSiteCookies.NONE, attribute);
        Assert.assertNotEquals(SameSiteCookies.LAX, attribute);
    }

    @Test
    public void testToValidAttribute() {
        Assert.assertEquals(SameSiteCookies.fromString("none"), SameSiteCookies.NONE);
        Assert.assertEquals(SameSiteCookies.fromString("None"), SameSiteCookies.NONE);
        Assert.assertEquals(SameSiteCookies.fromString("NONE"), SameSiteCookies.NONE);

        Assert.assertEquals(SameSiteCookies.fromString("lax"), SameSiteCookies.LAX);
        Assert.assertEquals(SameSiteCookies.fromString("Lax"), SameSiteCookies.LAX);
        Assert.assertEquals(SameSiteCookies.fromString("LAX"), SameSiteCookies.LAX);

        Assert.assertEquals(SameSiteCookies.fromString("strict"), SameSiteCookies.STRICT);
        Assert.assertEquals(SameSiteCookies.fromString("Strict"), SameSiteCookies.STRICT);
        Assert.assertEquals(SameSiteCookies.fromString("STRICT"), SameSiteCookies.STRICT);
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute01() {
        SameSiteCookies.fromString("");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute02() {
        SameSiteCookies.fromString(" ");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute03() {
        SameSiteCookies.fromString("Strict1");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute04() {
        SameSiteCookies.fromString("foo");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute05() {
        SameSiteCookies.fromString("Lax ");
    }
}