package com.github.kostyasha.yad.utils;

import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.function.Function;

/**
 * @author Kanstantsin Shautsou
 */
public class ResolveVarFunction implements Function<String, String> {
    private Run run;
    private TaskListener listener;

    public ResolveVarFunction(Run run, TaskListener listener) {
        this.run = run;
        this.listener = listener;
    }

    @Override
    public String apply(String s) {
        return VariableUtils.resolveVar(s, run, listener);
    }
}
