package com.github.kostyasha.yad.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

import static java.util.Objects.nonNull;

/**
 * {@link DockerServerCredentials} has final fields that blocks extensibility (as usual in jenkins).
 *
 * @author Kanstantsin Shautsou
 */
public class DockerDaemonFileCredentials extends BaseStandardCredentials implements DockerDaemonCerts {
    private static final long serialVersionUID = 1L;

    public static final Logger LOG = LoggerFactory.getLogger(DockerDaemonFileCredentials.class);

    private String dockerCertPath;

    private transient DockerServerCredentials credentials = null;

    @DataBoundConstructor
    public DockerDaemonFileCredentials(@CheckForNull CredentialsScope scope, @CheckForNull String id,
                                       @CheckForNull String description,
                                       @Nonnull String dockerCertPath) {
        super(scope, id, description);
        this.dockerCertPath = dockerCertPath;
    }

    public String getClientKey() {
        resolveCredentialsOnSlave();
        return credentials.getClientKeySecret().getPlainText();
    }

    public String getClientCertificate() {
        resolveCredentialsOnSlave();
        return credentials.getClientCertificate();
    }

    public String getServerCaCertificate() {
        resolveCredentialsOnSlave();
        return credentials.getServerCaCertificate();
    }


    private void resolveCredentialsOnSlave() {
        if (nonNull(credentials)) {
            return;
        }
        File credDir = new File(dockerCertPath);
        if (!credDir.isDirectory()) {
            throw new IllegalStateException(dockerCertPath + " isn't directory!");
        }
        try {
            String caPem = FileUtils.readFileToString(new File(credDir, "ca.pem"), java.nio.charset.StandardCharsets.UTF_8);
            String keyPem = FileUtils.readFileToString(new File(credDir, "key.pem"), java.nio.charset.StandardCharsets.UTF_8);
            String certPem = FileUtils.readFileToString(new File(credDir, "cert.pem"), java.nio.charset.StandardCharsets.UTF_8);
            this.credentials = new DockerServerCredentials(null, "remote-docker", null,
                    hudson.util.Secret.fromString(caPem), keyPem, certPem);
        } catch (IOException ex) {
            LOG.error("", ex);
            throw new RuntimeException(ex);
        }
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @Nonnull
        public String getDisplayName() {
            return "Docker Host Certificate Authentication (remote)";
        }
    }
}
