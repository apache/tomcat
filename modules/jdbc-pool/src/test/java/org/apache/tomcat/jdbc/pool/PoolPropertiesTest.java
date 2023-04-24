package org.apache.tomcat.jdbc.pool;


import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PoolPropertiesTest {
    private static final String DEFAULT_USER = "username_def";
    private static final String DEFAULT_PASSWD = "password_def";
    @Test
    public void toStringOutputShouldHaveBalancedBrackets() {
        PoolProperties properties = new PoolProperties();
        properties.setUsername(DEFAULT_USER);
        properties.setPassword(DEFAULT_PASSWD);
        properties.setAlternateUsernameAllowed(true);
        properties.setInitialSize(0);
        properties.setRemoveAbandoned(false);
        properties.setTimeBetweenEvictionRunsMillis(-1);

        String asString = properties.toString();

        List<Character> stack = new ArrayList<>();
        for (char c : asString.toCharArray()) {
            switch (c) {
                case '{':
                case '(':
                case '[': stack.add(c); break;
                case '}': Assert.assertEquals('{', stack.remove(stack.size() - 1).charValue()); break;
                case ')': Assert.assertEquals('(', stack.remove(stack.size() - 1).charValue()); break;
                case ']': Assert.assertEquals('[', stack.remove(stack.size() - 1).charValue()); break;
                default: break;
            }
        }
        Assert.assertEquals("All brackets should have been closed", 0, stack.size());
    }
}
