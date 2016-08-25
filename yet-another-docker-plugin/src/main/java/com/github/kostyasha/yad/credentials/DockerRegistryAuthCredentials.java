package com.github.kostyasha.yad.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
import hudson.Extension;
import hudson.Util;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Docker registry auth credentials.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerRegistryAuthCredentials extends UsernamePasswordCredentialsImpl {
    private static final long serialVersionUID = 1L;

    private final String email;

    /**
     * @param scope       the credentials scope
     * @param id          the ID or {@code null} to generate a new one.
     * @param description the description.
     * @param username    the username.
     * @param password    the password.
     * @param email       the email.
     */
    @DataBoundConstructor
    public DockerRegistryAuthCredentials(@CheckForNull CredentialsScope scope,
                                         @CheckForNull String id,
                                         @CheckForNull String description,
                                         @CheckForNull String username,
                                         @CheckForNull String password,
                                         @CheckForNull String email) {
        super(scope, id, description, username, password);
        this.email = Util.fixNull(email);
    }

    @Nonnull
    public String getEmail() {
        return email;
    }

    public AuthConfig getAuthConfig() {
        final AuthConfig authConfig = new AuthConfig();

        authConfig.withEmail(getEmail());
        authConfig.withUsername(getUsername());
        authConfig.withPassword(getPassword().getPlainText());

        return authConfig;
    }

    @Extension
    public static class DescriptorImpl extends UsernamePasswordCredentialsImpl.DescriptorImpl {
        @Override
        public String getDisplayName() {
            return "Docker Registry Auth";
        }
    }
}
