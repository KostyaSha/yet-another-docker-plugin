package com.github.kostyasha.it.utils;

import hudson.Functions;
import hudson.cli.DockerCLI;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import jenkins.model.Jenkins;

import java.io.Serializable;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author Kanstantsin Shautsou
 */
public final class JenkinsRuleHelpers implements Serializable {
    private static final long serialVersionUID = 1L;

    private JenkinsRuleHelpers() {
    }

    /**
     * Returns true if Hudson is building something or going to build something.
     */
    public static boolean isSomethingHappening(Jenkins jenkins) {
        if (!jenkins.getQueue().isEmpty())
            return true;
        for (Computer n : jenkins.getComputers())
            if (!n.isIdle())
                return true;
        return false;
    }

    /**
     * Waits until Hudson finishes building everything, including those in the queue, or fail the test
     * if the specified timeout milliseconds is
     */
    public static void waitUntilNoActivityUpTo(Jenkins jenkins, int timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        int streak = 0;

        while (true) {
            Thread.sleep(10);
            if (isSomethingHappening(jenkins)) {
                streak = 0;
            } else {
                streak++;
            }

            if (streak > 5) {  // the system is quiet for a while
                return;
            }

            if (System.currentTimeMillis() - startTime > timeout) {
                List<Queue.Executable> building = new ArrayList<Queue.Executable>();
                for (Computer c : jenkins.getComputers()) {
                    for (Executor e : c.getExecutors()) {
                        if (e.isBusy())
                            building.add(e.getCurrentExecutable());
                    }
                    for (Executor e : c.getOneOffExecutors()) {
                        if (e.isBusy())
                            building.add(e.getCurrentExecutable());
                    }
                }
                dumpThreads();
                throw new AssertionError(String.format("Jenkins is still doing something after %dms: queue=%s building=%s",
                        timeout, Arrays.asList(jenkins.getQueue().getItems()), building));
            }
        }
    }

    private static void dumpThreads() {
        ThreadInfo[] threadInfos = Functions.getThreadInfos();
        Functions.ThreadGroupMap m = Functions.sortThreadsAndGetGroupMap(threadInfos);
        for (ThreadInfo ti : threadInfos) {
            System.err.println(Functions.dumpThreadInfo(ti, m));
        }
    }

    public static void caller(DockerCLI cli, Callable<Boolean, Throwable> callable) throws Throwable {
        assertThat(
                cli.getChannel().call(callable),
                equalTo(true)
        );
    }
}
