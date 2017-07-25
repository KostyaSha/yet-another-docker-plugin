package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PullResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.PullImageResultCallback;
import hudson.model.TaskListener;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerPullImageListenerLogger extends PullImageResultCallback {
    private TaskListener listener;

    public DockerPullImageListenerLogger(TaskListener listener) {
        this.listener = listener;
    }

    @Override
    public void onNext(PullResponseItem item) {
        super.onNext(item);
        listener.getLogger().println(item.toString());
    }
}
