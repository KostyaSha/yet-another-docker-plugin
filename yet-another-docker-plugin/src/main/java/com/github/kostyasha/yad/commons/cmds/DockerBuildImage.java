package com.github.kostyasha.yad.commons.cmds;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.BuildImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.NameParser;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.ToStringStyle.SHORT_PREFIX_STYLE;
import static java.util.Objects.isNull;
import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * @author Kanstantsin Shautsou
 */
@Beta
public class DockerBuildImage extends AbstractDescribableImpl<DockerBuildImage> implements Serializable {
  private static final Long serialVersionUID = 1L;
  private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImage.class);

  private List<String> tags;
  private Boolean noCache;
  private Boolean rm;
  private Boolean forceRm;
  private Boolean quiet;
  private Boolean pull;
  /**
   * Full path for dockerfile build context dir.
   */
  private String baseDirectory;
  private String dockerfile;

  private AuthConfig authConfig;

  @DataBoundConstructor
  public DockerBuildImage() {
  }

  /**
   * clone
   */
  public DockerBuildImage(DockerBuildImage that) {
    this.tags = that.getTags();
    this.noCache = that.getNoCache();
    this.rm = that.getRm();
    this.forceRm = that.getForceRm();
    this.quiet = that.getQuiet();
    this.pull = that.getPull();
    this.baseDirectory = that.getBaseDirectory();
    this.dockerfile = that.getDockerfile();
  }

  /**
   * @see #tags
   */
  @Nonnull
  public List<String> getTags() {
    return isNull(tags) ? Collections.emptyList() : tags;
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
  public Boolean getRm() {
    return rm;
  }

  @DataBoundSetter
  public void setRm(Boolean rm) {
    this.rm = rm;
  }

  /**
   * @see #remove
   */
  @CheckForNull
  public Boolean getForceRm() {
    return forceRm;
  }

  @DataBoundSetter
  public void setForceRm(Boolean forceRm) {
    this.forceRm = forceRm;
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

  /**
   * @see #baseDirectory
   */
  @DataBoundSetter
  public void setBaseDirectory(String baseDirectory) {
    this.baseDirectory = baseDirectory;
  }

  @CheckForNull
  public String getDockerfile() {
    return dockerfile;
  }

  @DataBoundSetter
  public void setDockerfile(String dockerfile) {
    this.dockerfile = dockerfile;
  }

  public List<String> getTagsNormalised() {
    ArrayList<String> normalizedTags = new ArrayList<>();
    for (String tag : getTags()) {
      NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(tag);
      normalizedTags.add(reposTag.repos + ":" + getRealImageTag(reposTag));
    }
    return normalizedTags;
  }

  private static String getRealImageTag(NameParser.ReposTag reposTag) {
    return isEmpty(reposTag.tag) ? "latest" : reposTag.tag;
  }

  @Nonnull
  public BuildImageCmd fillSettings(@Nonnull BuildImageCmd cmd) {
    cmd.withNoCache(noCache)
      .withRemove(rm)
      .withForcerm(forceRm)
      .withQuiet(quiet)
      .withPull(pull)
      .withBaseDirectory(new File(baseDirectory));

    if (StringUtils.isNotBlank(dockerfile)) {
      cmd.withDockerfile(new File(baseDirectory, dockerfile));
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

  public AuthConfig getAuthConfig() {
    return authConfig;
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<DockerBuildImage> {
    @Override
    public String getDisplayName() {
      return "Docker Build Image";
    }
  }
}
