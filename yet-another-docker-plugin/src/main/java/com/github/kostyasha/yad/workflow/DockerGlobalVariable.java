package com.github.kostyasha.yad.workflow;

import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

import javax.annotation.Nonnull;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerGlobalVariable extends GlobalVariable {
    @Nonnull
    @Override
    public String getName() {
        return "yad";
    }

    @Nonnull
    @Override
    public Object getValue(@Nonnull CpsScript script) throws Exception {
        return null;
    }
}
