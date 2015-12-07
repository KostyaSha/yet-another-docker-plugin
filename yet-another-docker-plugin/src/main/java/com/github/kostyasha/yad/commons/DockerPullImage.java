package com.github.kostyasha.yad.commons;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Image;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.NameParser;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.kostyasha.yad.docker_java.com.google.common.collect.Iterables;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Contains docker pull image related settings:
 *
 * @author Kanstantsin Shautsou
 */
public class DockerPullImage extends AbstractDescribableImpl<DockerPullImage> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerPullImage.class);

    @CheckForNull
    private DockerImagePullStrategy pullStrategy = DockerImagePullStrategy.PULL_LATEST;

    @CheckForNull
    private String credentialsId;

    @DataBoundConstructor
    public DockerPullImage() {
    }

    public DockerImagePullStrategy getPullStrategy() {
        return pullStrategy;
    }

    @DataBoundSetter
    public void setPullStrategy(DockerImagePullStrategy pullStrategy) {
        this.pullStrategy = pullStrategy;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    /**
     * Action around image with defined configuration
     */
    public void exec(@Nonnull final DockerClient client, @Nonnull final String imageName)
            throws IOException {
        List<Image> images = client.listImagesCmd().exec();

        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(imageName);
        // if image was specified without tag, then treat as latest
        final String fullImageName = repostag.repos + ":" + (repostag.tag.isEmpty() ? "latest" : repostag.tag);

        boolean hasImage = Iterables.any(images, image -> Arrays.asList(image.getRepoTags()).contains(fullImageName));

        boolean pull = hasImage ?
                getPullStrategy().pullIfExists(imageName) :
                getPullStrategy().pullIfNotExists(imageName);

        if (pull) {
            LOG.info("Pulling image '{}' {}. This may take awhile...", imageName,
                    hasImage ? "again" : "since one was not found");

            long startTime = System.currentTimeMillis();
            //Identifier amiId = Identifier.fromCompoundString(ami);
            client.pullImageCmd(imageName).exec(new PullImageResultCallback()).awaitSuccess();
            long pullTime = System.currentTimeMillis() - startTime;
            LOG.info("Finished pulling image '{}', took {} ms", imageName, pullTime);
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerPullImage> {
        @Override
        public String getDisplayName() {
            return "Docker Pull Image";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            List<StandardCredentials> credentials =
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, context, ACL.SYSTEM,
                            Collections.<DomainRequirement>emptyList());

            return new StandardListBoxModel().withEmptySelection()
                    .withMatching(CredentialsMatchers.always(), credentials);
        }
    }
}
