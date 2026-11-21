package com.github.kostyasha.yad.commons;

/**
 * @author Kanstantsin Shautsou
 */

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.github.kostyasha.yad.credentials.DockerRegistryAuthCredentials;
import com.github.kostyasha.yad.utils.CredentialsListBoxModel;
import hudson.Extension;
import hudson.model.Describable;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryToken;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Collections;

/**
 * Map docke registry host - credentialsId for UI configuration.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerRegistryCredential implements Describable<DockerRegistryCredential> {

    private final String registryAddr;
    private final String credentialsId;

    @DataBoundConstructor
    public DockerRegistryCredential(@Nonnull String registryAddr, @Nonnull String credentialsId) {
        this.registryAddr = registryAddr;
        this.credentialsId = credentialsId;
    }

    public String getRegistryAddr() {
        return registryAddr;
    }

    public String getCredentialsId() {
        return credentialsId;
    }


    @Override
    public Descriptor<DockerRegistryCredential> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerRegistryCredential> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return new CredentialsListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM2, context, StandardCredentials.class,
                            Collections.emptyList(), CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(DockerRegistryAuthCredentials.class),
                                    AuthenticationTokens.matcher(DockerRegistryToken.class)));
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Registry - Credential mapping";
        }
    }
}
