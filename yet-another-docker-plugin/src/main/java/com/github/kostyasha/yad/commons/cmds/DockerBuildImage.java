package com.github.kostyasha.yad.commons.cmds;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.BuildImageCmd;
import com.github.kostyasha.yad_docker_java.com.google.common.annotations.Beta;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.EqualsBuilder;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.HashCodeBuilder;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.ToStringBuilder;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.Serializable;
import java.util.List;

import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.ToStringStyle.SHORT_PREFIX_STYLE;

/**
 * @author Kanstantsin Shautsou
 */
@Beta
public class DockerBuildImage extends AbstractDescribableImpl<DockerBuildImage> implements Serializable {
    private static final Long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImage.class);

    private List<String> tags;
    private Boolean noCache;
    private Boolean remove = true;
    private Boolean quiet;
    private Boolean pull;
    private String baseDirectory;
    private String dockerFile;

    @DataBoundConstructor
    public DockerBuildImage() {
    }

    /**
     * @see #tags
     */
    @CheckForNull
    public List<String> getTags() {
        return tags;
    }

    @DataBoundSetter
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * @see #noCache
     */
    @CheckForNull
    public Boolean getNoCache() {
        return noCache;
    }

    @DataBoundSetter
    public void setNoCache(Boolean noCache) {
        this.noCache = noCache;
    }

    /**
     * @see #remove
     */
    @CheckForNull
    public Boolean getRemove() {
        return remove;
    }

    @DataBoundSetter
    public void setRemove(Boolean remove) {
        this.remove = remove;
    }

    /**
     * @see #quiet
     */
    @CheckForNull
    public Boolean getQuiet() {
        return quiet;
    }

    @DataBoundSetter
    public void setQuiet(Boolean quiet) {
        this.quiet = quiet;
    }

    /**
     * @see #pull
     */
    @CheckForNull
    public Boolean getPull() {
        return pull;
    }

    @DataBoundSetter
    public void setPull(Boolean pull) {
        this.pull = pull;
    }

    /**
     * @see #baseDirectory
     */
    @CheckForNull
    public String getBaseDirectory() {
        return baseDirectory;
    }

    @DataBoundSetter
    public void setBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Nonnull
    public BuildImageCmd fillSettings(@Nonnull BuildImageCmd cmd) {
        cmd.withNoCache(noCache);
        cmd.withRemove(remove);
        cmd.withQuiet(quiet);
        cmd.withPull(pull);
        cmd.withBaseDirectory(new File(baseDirectory));
        if (StringUtils.isNotBlank(dockerFile)) {
            cmd.withDockerfile(new File(baseDirectory, dockerFile));
        } else {
            cmd.withDockerfile(new File(baseDirectory, "Dockerfile"));
        }
        return cmd;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, SHORT_PREFIX_STYLE);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerBuildImage> {
        @Override
        public String getDisplayName() {
            return "Docker Build Image";
        }
    }
}
