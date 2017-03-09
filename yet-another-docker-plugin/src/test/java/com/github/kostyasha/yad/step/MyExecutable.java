package com.github.kostyasha.yad.step;

import hudson.model.Queue;
import hudson.model.queue.SubTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author Kanstantsin Shautsou
 */
public class MyExecutable implements Queue.Executable {
    private static final Logger LOG = LoggerFactory.getLogger(MyExecutable.class);

    private DockerTask dockerTask;

    public MyExecutable(DockerTask dockerTask) {
        this.dockerTask = dockerTask;
    }

    @Nonnull
    @Override
    public SubTask getParent() {
        return dockerTask;
    }

    @Override
    public void run() {
        LOG.info(" In run!");
        try {
            Thread.sleep(MINUTES.toMillis(5));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getEstimatedDuration() {
        return -1;
    }

    @Override
    public String toString() {
        return "This is my super executable";
    }
}
