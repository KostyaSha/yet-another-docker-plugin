package com.github.kostyasha.yad.other.cloudorder;

import com.github.kostyasha.yad.DockerCloud;
import hudson.Extension;
import hudson.model.Label;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static jenkins.model.Jenkins.getInstance;

public class AsIsDockerCloudOrder extends DockerCloudOrder {
    @Nonnull
    @Override
    public List<DockerCloud> getDockerClouds(Label label) {
        return getInstance().clouds.stream()
                .filter(Objects::nonNull)
                .filter(DockerCloud.class::isInstance)
                .map(cloud -> (DockerCloud) cloud)
                .collect(Collectors.toList());
    }

    @Extension
    public static class DescriptorImpl extends DockerCloudOrderDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "As is";
        }
    }
}
