package com.github.kostyasha.yad.commons;

/**
 * @author Kanstantsin Shautsou
 */

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.github.kostyasha.yad.credentials.DockerRegistryAuthCredentials;
import com.github.kostyasha.yad.utils.CredentialsListBoxModel;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Map docke registry host - credentialsId for UI configuration.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerRegistryCredential extends AbstractDescribableImpl<DockerRegistryCredential> {

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

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerRegistryCredential> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            List<DockerRegistryAuthCredentials> credentials =
                    CredentialsProvider.lookupCredentials(DockerRegistryAuthCredentials.class, context, ACL.SYSTEM,
                            Collections.emptyList());

            return new CredentialsListBoxModel()
                    .includeEmptyValue()
                    .withMatching(CredentialsMatchers.always(), credentials);
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Registry - Credential mapping";
        }
    }
}
