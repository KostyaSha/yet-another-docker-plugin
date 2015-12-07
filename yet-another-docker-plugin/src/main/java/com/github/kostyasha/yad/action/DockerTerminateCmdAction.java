package com.github.kostyasha.yad.action;

import hudson.model.Action;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;

/**
 * @author Kanstantsin Shautsou
 */
public abstract class DockerTerminateCmdAction implements Action {
    public abstract void exec(DockerClient client, String containerId);
}
