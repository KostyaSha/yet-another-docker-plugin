package com.github.kostyasha.it.other;

import hudson.model.Cause;

/**
 * @author Kanstantsin Shautsou
 */
public class TestCause extends Cause {
    @Override
    public String getShortDescription() {
        return "Triggered by test code";
    }
}
