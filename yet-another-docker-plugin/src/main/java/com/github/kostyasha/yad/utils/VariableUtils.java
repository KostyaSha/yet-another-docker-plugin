package com.github.kostyasha.yad.utils;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Kanstantsin Shautsou
 */
public class VariableUtils {
    private static final Logger LOG = LoggerFactory.getLogger(VariableUtils.class);

    private VariableUtils() {
    }

    /**
     * Resolve on remoting side during execution.
     * Because node may have some specific node vars.
     */
    public static String resolveVar(String var, Run run, TaskListener taskListener) {
        String resolvedVar = var;
        try {
            final EnvVars envVars = run.getEnvironment(taskListener);
            resolvedVar = envVars.expand(var);
        } catch (IOException | InterruptedException e) {
            LOG.warn("Can't resolve variable " + var + " for {}", run, e);
        }
        return resolvedVar;
    }

}
