package com.github.kostyasha.it.other;

import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;

/**
 * Test callable, no perms.
 *
 * @author Kanstantsin Shautsou
 */
public abstract class TCallable<V, T extends Throwable> implements Callable<V, T> {
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
    }
}
