package com.github.kostyasha.yad.steps;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DockerImageComboStepResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    @Nonnull
    private List<String> images = new ArrayList<>();
    private boolean success = false;
    private Set<String> containers;
    private String errorTrace;
    private String errorMessage;

    @Nonnull
    public List<String> getImages() {
        return images;
    }

    public void setImages(@Nonnull List<String> images) {
        this.images = images;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setContainers(Set<String> containers) {
        this.containers = containers;
    }

    public Set<String> getContainers() {
        return containers;
    }

    @CheckForNull
    public String getErrorTrace() {
        return errorTrace;
    }

    public void setErrorTrace(String errorTrace) {
        this.errorTrace = errorTrace;
    }

    @CheckForNull
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
