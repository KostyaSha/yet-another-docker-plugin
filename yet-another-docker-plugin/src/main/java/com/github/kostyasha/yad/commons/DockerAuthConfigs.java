package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfigurations;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerAuthConfigs implements Describable<DockerAuthConfigs> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImage.class);

    private List<DockerAuthConfig> dockerAuthConfigs;

    @DataBoundConstructor
    public DockerAuthConfigs(List<DockerAuthConfig> dockerAuthConfigs) {
        this.dockerAuthConfigs = dockerAuthConfigs;
    }

    /**
     * @see #dockerAuthConfigs
     */
    @CheckForNull
    public List<DockerAuthConfig> getDockerAuthConfigs() {
        return dockerAuthConfigs;
    }

    public void setDockerAuthConfigs(List<DockerAuthConfig> dockerAuthConfigs) {
        this.dockerAuthConfigs = dockerAuthConfigs;
    }

    @Nonnull
    public AuthConfigurations getAuthConfigurations() {
        final AuthConfigurations authConfigurations = new AuthConfigurations();
        for (DockerAuthConfig authConfig : dockerAuthConfigs) {
            authConfigurations.addConfig(authConfig.getAuthConfig());
        }
        return authConfigurations;
    }

    @Override
    public Descriptor<DockerAuthConfigs> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

}
