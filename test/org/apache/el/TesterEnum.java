package org.apache.el;

public enum TesterEnum {
    APPLE, ORANGE;
    
    @Override
    public String toString() {
        return "This is a " + this.name();
    }
}
