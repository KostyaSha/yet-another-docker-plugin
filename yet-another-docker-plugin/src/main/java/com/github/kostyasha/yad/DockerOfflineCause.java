package com.github.kostyasha.yad;

import hudson.slaves.OfflineCause;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerOfflineCause extends OfflineCause {
    private String message;

    public DockerOfflineCause(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }
}
