package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Frame;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.LogContainerResultCallback;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.TaskListener;

public class ListenerLogContainerResultCallback extends LogContainerResultCallback {
    private final TaskListener listener;

    public ListenerLogContainerResultCallback(TaskListener listener) {
        this.listener = listener;
    }

    @SuppressFBWarnings(value = "DM_DEFAULT_ENCODING")
    @Override
    public void onNext(Frame item) {
        listener.getLogger().println(new String(item.getPayload()).trim());
        super.onNext(item);
    }
}
