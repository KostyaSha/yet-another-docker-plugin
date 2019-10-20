package com.github.kostyasha.yad.commons;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.KnownHostsFileKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.tools.JDKInstaller;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;

import static hudson.Util.fixEmpty;
import static hudson.plugins.sshslaves.SSHLauncher.SSH_SCHEME;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Restore class like it was before 1.30.0 ssh-slaves-plugin.
 */
public class DockerSSHConnector extends ComputerConnector {
    /**
     * Field port
     */
    private final int port;

    /**
     * The id of the credentials to use.
     */
    private String credentialsId;

    /**
     * Transient stash of the credentials to use, mostly just for providing floating user object.
     */
    private transient StandardUsernameCredentials credentials;

    /**
     * Field jvmOptions.
     */
    private final String jvmOptions;

    /**
     * Field javaPath.
     */
    private final String javaPath;

    /**
     * Field jdk
     */
    private final JDKInstaller jdkInstaller;

    /**
     * Field prefixStartSlaveCmd.
     */
    private final String prefixStartSlaveCmd;

    /**
     * Field suffixStartSlaveCmd.
     */
    private final String suffixStartSlaveCmd;

    /**
     * Field launchTimeoutSeconds.
     */
    private final Integer launchTimeoutSeconds;

    /**
     * Field maxNumRetries.
     */
    private final Integer maxNumRetries;

    /**
     * Field retryWaitTime.
     */
    private final Integer retryWaitTime;

    private SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy;

    @SuppressWarnings("checkstyle:ParameterNumber")
    @DataBoundConstructor
    public DockerSSHConnector(int port,
                              StandardUsernameCredentials credentials,
                              String credentialsId,
                              String jvmOptions,
                              String javaPath,
                              JDKInstaller jdkInstaller,
                              String prefixStartSlaveCmd,
                              String suffixStartSlaveCmd,
                              Integer launchTimeoutSeconds,
                              Integer maxNumRetries,
                              Integer retryWaitTime,
                              SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
        this.jvmOptions = jvmOptions;
        this.port = port == 0 ? 22 : port;
        this.credentials = credentials;
        this.credentialsId = credentialsId;
        this.javaPath = javaPath;
        this.jdkInstaller = jdkInstaller;
        this.prefixStartSlaveCmd = fixEmpty(prefixStartSlaveCmd);
        this.suffixStartSlaveCmd = fixEmpty(suffixStartSlaveCmd);
        this.launchTimeoutSeconds = launchTimeoutSeconds == null || launchTimeoutSeconds <= 0 ? null : launchTimeoutSeconds;
        this.maxNumRetries = maxNumRetries != null && maxNumRetries > 0 ? maxNumRetries : 0;
        this.retryWaitTime = retryWaitTime != null && retryWaitTime > 0 ? retryWaitTime : 0;
        this.sshHostKeyVerificationStrategy = sshHostKeyVerificationStrategy;
    }

    public int getPort() {
        return port;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public void setCredentials(StandardUsernameCredentials credentials) {
        this.credentials = credentials;
    }

    public String getJvmOptions() {
        return jvmOptions;
    }

    public String getJavaPath() {
        return javaPath;
    }

    public JDKInstaller getJdkInstaller() {
        return jdkInstaller;
    }

    public String getPrefixStartSlaveCmd() {
        return prefixStartSlaveCmd;
    }

    public String getSuffixStartSlaveCmd() {
        return suffixStartSlaveCmd;
    }

    public Integer getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds;
    }

    public Integer getMaxNumRetries() {
        return maxNumRetries;
    }

    public Integer getRetryWaitTime() {
        return retryWaitTime;
    }

    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
        return sshHostKeyVerificationStrategy;
    }

    @CheckForNull
    public StandardUsernameCredentials getCredentials() {
        String credentialsIdf;

        if (isNull(credentialsId)) {
            credentialsIdf = (isNull(credentials)) ? null : credentials.getId();
        } else {
            credentialsIdf = credentialsId;
        }

        try {
            StandardUsernameCredentials credentialss;
            if (credentialsIdf == null) {
                credentialss = null;
            } else {
                credentialss = SSHLauncher.lookupSystemCredentials(credentialsIdf);
            }
            if (nonNull(credentialss)) {
                credentials = credentialss;
            }
        } catch (Throwable t) {
            // ignore
        }

        return credentials;
    }

    @Override
    public SSHLauncher launch(String host, TaskListener listener) throws IOException, InterruptedException {
        return new SSHLauncher(host, port, credentialsId, jvmOptions, javaPath, prefixStartSlaveCmd,
                suffixStartSlaveCmd, launchTimeoutSeconds, maxNumRetries, retryWaitTime,
                sshHostKeyVerificationStrategy);
    }

    protected Object readResolve() {
        if (isNull(sshHostKeyVerificationStrategy)) {
            this.sshHostKeyVerificationStrategy = new KnownHostsFileKeyVerificationStrategy();
        }

        return this;
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker SSH Connector Settings";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            if (context instanceof AccessControlled) {
                if (!((AccessControlled) context).hasPermission(Computer.CONFIGURE)) {
                    return new ListBoxModel();
                }
            } else {
                if (!Jenkins.getInstance().hasPermission(Computer.CONFIGURE)) {
                    return new ListBoxModel();
                }
            }
            return new StandardUsernameListBoxModel().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                            ACL.SYSTEM, SSH_SCHEME));
        }

        public FormValidation doCheckLaunchTimeoutSeconds(String value) {
            if (StringUtils.isBlank(value)) return FormValidation.ok();
            try {
                if (Integer.parseInt(value.trim()) < 0) {
                    return FormValidation.error("Launch timeout must be positive!");
                }
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("Launch timeout must be a number!");
            }
        }

    }
}
