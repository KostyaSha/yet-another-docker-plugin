package com.github.kostyasha.yad.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.google.common.base.Throwables;
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
        return credentials.getClientKey();
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
            String caPem = FileUtils.readFileToString(new File(credDir, "ca.pem"));
            String keyPem = FileUtils.readFileToString(new File(credDir, "key.pem"));
            String certPem = FileUtils.readFileToString(new File(credDir, "cert.pem"));
            this.credentials = new DockerServerCredentials(null, "remote-docker", null, caPem, keyPem, certPem);
        } catch (IOException ex) {
            LOG.error("", ex);
            Throwables.propagate(ex);
        }
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        public DescriptorImpl() {
        }

        public String getDisplayName() {
            return "Docker Host Certificate Authentication (remote)";
        }
    }
}
