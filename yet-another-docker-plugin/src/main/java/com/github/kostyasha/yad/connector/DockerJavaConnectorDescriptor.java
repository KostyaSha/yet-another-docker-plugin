package com.github.kostyasha.yad.connector;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.exception.DockerException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Version;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad.utils.CredentialsListBoxModel;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.github.kostyasha.yad.client.ClientBuilderForConnector.newClientBuilderForConnector;
import static org.apache.commons.lang.builder.ToStringStyle.MULTI_LINE_STYLE;

/**
 * @author Kanstantsin Shautsou
 */
public abstract class DockerJavaConnectorDescriptor extends Descriptor<DockerJavaConnector> {
    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
        List<StandardCredentials> credentials =
                CredentialsProvider.lookupCredentials(StandardCredentials.class, context, ACL.SYSTEM,
                        Collections.emptyList());

        return new CredentialsListBoxModel()
                .withEmptySelection()
                .withMatching(CredentialsMatchers.always(), credentials);
    }

    public static DescriptorExtensionList<DockerJavaConnector, DockerJavaConnectorDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(DockerJavaConnector.class);
    }
}
