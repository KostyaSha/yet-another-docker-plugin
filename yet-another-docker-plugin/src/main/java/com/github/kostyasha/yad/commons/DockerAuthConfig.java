package com.github.kostyasha.yad.commons;

import com.cloudbees.plugins.credentials.Credentials;
import com.github.kostyasha.yad.credentials.DockerRegistryAuthCredentials;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;

import static com.github.kostyasha.yad.client.ClientBuilderForConnector.lookupSystemCredentials;

/**
 * Registry + credentialsId storage that returns docker-java AuthConfig object.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerAuthConfig {
    private String registryAddress;
    private String credentialsId;

    @DataBoundConstructor
    public DockerAuthConfig(String target, String credentialsId) {
        this.registryAddress = target;
        this.credentialsId = credentialsId;
    }

    @CheckForNull
    public String getTarget() {
        return registryAddress;
    }

    public void setTarget(String registryAddress) {
        this.registryAddress = registryAddress;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @CheckForNull
    public AuthConfig getAuthConfig() {
        if (StringUtils.isEmpty(credentialsId)) {
            return null;
        }

        if (StringUtils.isNotBlank(credentialsId)) {
            // hostname requirements?
            Credentials credentials = lookupSystemCredentials(credentialsId);
            if (credentials instanceof DockerRegistryAuthCredentials) {
                final DockerRegistryAuthCredentials authCredentials = (DockerRegistryAuthCredentials) credentials;
                return authCredentials.getAuthConfig();
            }
        }
        return null;
    }
}
