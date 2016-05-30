package com.github.kostyasha.yad.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientImpl;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.KeystoreSSLConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.SSLConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.netty.DockerCmdExecFactoryImpl;
import com.github.kostyasha.yad.other.VariableSSLConfig;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.apache.commons.lang.Validate;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Collections;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static com.github.kostyasha.yad.client.ClientConfigBuilderForPlugin.newClientConfigBuilder;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Builds ClientConfig with helper methods that extracts info for plugin routines.
 */
@SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "https://github.com/docker-java/docker-java/issues/588")
public class ClientBuilderForConnector {

    private DockerClientConfig dockerClientConfig = null;
    private DockerCmdExecFactory dockerCmdExecFactory = null;
    private SSLConfig sslConfig = null;

    private ClientBuilderForConnector() {
    }

    public ClientBuilderForConnector withDockerCmdExecFactory(DockerCmdExecFactory dockerCmdExecFactory) {
        this.dockerCmdExecFactory = dockerCmdExecFactory;
        return this;
    }

    public ClientBuilderForConnector withSslConfig(SSLConfig sslConfig) {
        this.sslConfig = sslConfig;
        return this;
    }

    /**
     * Sets SSLConfig from defined credentials id.
     *
     * @param credentialsId credentials to find in jenkins
     * @return docker-java client
     */
    public ClientBuilderForConnector withCredentials(String credentialsId)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (isNotBlank(credentialsId)) {
            Credentials credentials = lookupSystemCredentials(credentialsId);

            if (credentials instanceof CertificateCredentials) {
                CertificateCredentials certificateCredentials = (CertificateCredentials) credentials;
                withSslConfig(new KeystoreSSLConfig(
                        certificateCredentials.getKeyStore(),
                        certificateCredentials.getPassword().getPlainText()
                ));
//            } else if (credentials instanceof StandardUsernamePasswordCredentials) {
//                StandardUsernamePasswordCredentials usernamePasswordCredentials =
//                        ((StandardUsernamePasswordCredentials) credentials);
//
//                dockerClientConfigBuilder.withRegistryUsername(usernamePasswordCredentials.getUsername());
//                dockerClientConfigBuilder.withRegistryPassword(usernamePasswordCredentials.getPassword().getPlainText());
//
            } else if (credentials instanceof DockerServerCredentials) {
                final DockerServerCredentials dockerCreds = (DockerServerCredentials) credentials;

                withSslConfig(new VariableSSLConfig(
                        dockerCreds.getClientKey(),
                        dockerCreds.getClientCertificate(),
                        dockerCreds.getServerCaCertificate()
                ));
            }
        }

        return this;
    }

    public ClientBuilderForConnector withDockerClientConfig(DockerClientConfig clientConfig) {
        this.dockerClientConfig = clientConfig;
        return this;
    }

    public ClientBuilderForConnector withDockerConnector(DockerConnector connector)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        withDockerClientConfig(
                newClientConfigBuilder()
                        .forConnector(connector)
                        .build()
        );

        withCredentials(connector.getCredentialsId());

        return this;
    }

    public DockerClient build() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
            KeyManagementException {
        Validate.notNull(dockerClientConfig, "ClientConfig must be set");
//        Validate.notNull(dockerCmdExecFactory, "DockerCmdExecFactory must be set");

        if (isNull(dockerCmdExecFactory)) {
            dockerCmdExecFactory = new DockerCmdExecFactoryImpl();
        }

        if (nonNull(sslConfig)) {
            dockerCmdExecFactory.withSSLContext(sslConfig.getSSLContext());
        }

        return DockerClientImpl.getInstance(dockerClientConfig)
                .withDockerCmdExecFactory(dockerCmdExecFactory);
    }


    /**
     * Util method to find credential by id in jenkins
     *
     * @param credentialsId credentials to find in jenkins
     * @return {@link CertificateCredentials} or {@link StandardUsernamePasswordCredentials} expected
     */
    public static Credentials lookupSystemCredentials(String credentialsId) {
        return firstOrNull(
                lookupCredentials(
                        Credentials.class,
                        Jenkins.getActiveInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()
                ),
                withId(credentialsId)
        );
    }

    /**
     * helper class
     */
    public static ClientBuilderForConnector newClientBuilderForConnector() {
        return new ClientBuilderForConnector();
    }
}
