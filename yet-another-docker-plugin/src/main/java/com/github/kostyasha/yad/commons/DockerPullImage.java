package com.github.kostyasha.yad.commons;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.kostyasha.yad.credentials.DockerRegistryAuthCredentials;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectImageResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.PullImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.DockerClientException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Image;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.NameParser;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.github.kostyasha.yad.client.ClientBuilderForConnector.lookupSystemCredentials;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Contains docker pull image related settings:
 *
 * @author Kanstantsin Shautsou
 */
public class DockerPullImage extends AbstractDescribableImpl<DockerPullImage> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerPullImage.class);

    @CheckForNull
    private DockerImagePullStrategy pullStrategy = DockerImagePullStrategy.PULL_LATEST;

    @Deprecated
    @CheckForNull
    private String credentialsId;

    @CheckForNull
    private List<DockerRegistryCredential> registriesCreds;

    @DataBoundConstructor
    public DockerPullImage() {
    }

    @Nonnull
    public DockerImagePullStrategy getPullStrategy() {
        return isNull(pullStrategy) ? DockerImagePullStrategy.PULL_LATEST : pullStrategy;
    }

    @DataBoundSetter
    public void setPullStrategy(DockerImagePullStrategy pullStrategy) {
        this.pullStrategy = pullStrategy;
    }

    @Deprecated
    public String getCredentialsId() {
        return credentialsId;
    }

    @Deprecated
    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Nonnull
    public List<DockerRegistryCredential> getRegistriesCreds() {
        return nonNull(registriesCreds) ? registriesCreds : emptyList();
    }

    @DataBoundSetter
    public void setRegistriesCreds(List<DockerRegistryCredential> registriesCreds) {
        this.registriesCreds = registriesCreds;
    }

    /**
     * Action around image with defined configuration
     */
    public void exec(@Nonnull final DockerClient client, @Nonnull final String imageName, TaskListener listener)
            throws IOException {
        PrintStream llog = listener.getLogger();

        if (shouldPullImage(client, imageName)) {
            LOG.info("Pulling image '{}'. This may take awhile...", imageName);
            llog.println(String.format("Pulling image '%s'. This may take awhile...", imageName));

            long startTime = System.currentTimeMillis();
            final PullImageCmd pullImageCmd = client.pullImageCmd(imageName);

//            final AuthConfig authConfig = pullImageCmd.getAuthConfig();
            for (DockerRegistryCredential cred : getRegistriesCreds()) {
                // hostname requirements?
                Credentials credentials = lookupSystemCredentials(cred.getCredentialsId());
//                final String registryAddr = cred.getRegistryAddr();
                if (credentials instanceof DockerRegistryAuthCredentials) {
                    final DockerRegistryAuthCredentials authCredentials = (DockerRegistryAuthCredentials) credentials;
                    // TODO update docker-java for multiple entries
                    pullImageCmd.withAuthConfig(authCredentials.getAuthConfig());
                }
            }
            // Deprecated
            if (StringUtils.isNotBlank(credentialsId)) {
                // hostname requirements?
                Credentials credentials = lookupSystemCredentials(credentialsId);
                if (credentials instanceof DockerRegistryAuthCredentials) {
                    final DockerRegistryAuthCredentials authCredentials = (DockerRegistryAuthCredentials) credentials;
                    pullImageCmd.withAuthConfig(authCredentials.getAuthConfig());
                }
            }

            try {
                pullImageCmd
                        .exec(new DockerPullImageListenerLogger(listener))
                        .awaitSuccess();
            } catch (DockerClientException exception) {
                String exMsg = exception.getMessage();
                if (exMsg.contains("Could not pull image: Digest:") ||
                        exMsg.contains(": downloaded")) {
                    try {
                        client.inspectImageCmd(imageName).exec();
                    } catch (NotFoundException notFoundEx) {
                        throw exception;
                    }
                } else {
                    throw exception;
                }
            }

            long pullTime = System.currentTimeMillis() - startTime;
            LOG.info("Finished pulling image '{}', took {} ms", imageName, pullTime);
            llog.println(String.format("Finished pulling image '%s', took %d ms", imageName, pullTime));
        }
    }

    protected boolean shouldPullImage(DockerClient client, String imageName) {
        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(imageName);

        // if image was specified without tag, then treat as latest
        final String fullImageName = repostag.repos + ":" + (repostag.tag.isEmpty() ? "latest" : repostag.tag);

        // simply check without asking docker
        if (getPullStrategy().pullIfExists(fullImageName) && getPullStrategy().pullIfNotExists(fullImageName)) {
            return true;
        }

        if (!getPullStrategy().pullIfExists(fullImageName) && !getPullStrategy().pullIfNotExists(fullImageName)) {
            return false;
        }

        List<Image> images = client.listImagesCmd().exec();

        boolean hasImage = images.stream().anyMatch(image ->
                nonNull(image.getRepoTags()) &&
                        Arrays.stream(image.getRepoTags())
                                .anyMatch(repoTag ->
                                        repoTag.contains(fullImageName) || repoTag.contains("docker.io/" + fullImageName)
                                )
        );

        return hasImage ?
                getPullStrategy().pullIfExists(imageName) :
                getPullStrategy().pullIfNotExists(imageName);
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
    public static class DescriptorImpl extends Descriptor<DockerPullImage> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Pull Image";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            List<DockerRegistryAuthCredentials> credentials =
                    CredentialsProvider.lookupCredentials(DockerRegistryAuthCredentials.class, context, ACL.SYSTEM,
                            Collections.<DomainRequirement>emptyList());

            return new StandardListBoxModel().withEmptySelection()
                    .withMatching(CredentialsMatchers.instanceOf(DockerRegistryAuthCredentials.class), credentials);
        }
    }
}
