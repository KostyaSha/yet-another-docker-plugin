package com.github.kostyasha.it.tests;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.kostyasha.it.other.BCallable;
import com.github.kostyasha.it.rule.DockerResource;
import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerContainerLifecycle;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerPullImage;
import com.github.kostyasha.yad.commons.DockerRemoveContainer;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.HostConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.kostyasha.yad_docker_java.org.apache.commons.io.FileUtils;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils;
import hudson.cli.DockerCLI;
import hudson.model.Node;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.github.kostyasha.it.rule.DockerRule.getDockerItDir;
import static com.github.kostyasha.it.utils.JenkinsRuleHelpers.caller;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_ALWAYS;
import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;
import static java.util.Objects.nonNull;

/**
 * @author Kanstantsin Shautsou
 */
@Ignore(value = "docker-java doesn't support docker daemon UsernamePassword auth")
public class UserNameNginxProxyAuthTest {
    private static final Logger LOG = LoggerFactory.getLogger(UserNameNginxProxyAuthTest.class);
    private static final String DATA_IMAGE_TAG = UserNameNginxProxyAuthTest.class.getSimpleName().toLowerCase();
    private static final String HOST_CONTAINER_NAME = UserNameNginxProxyAuthTest.class.getCanonicalName() + "_host";
    private static final int CONTAINER_PORT = 44445;

    @ClassRule
    public static DockerRule d = new DockerRule(true);

    @ClassRule
    transient public static TemporaryFolder folder = new TemporaryFolder(new File(getDockerItDir()));

    @Rule
    public NginxResource nginxContainer = new NginxResource();

    public class NginxResource extends DockerResource {
        public String hostContainerId;

        @Override
        public void before() throws IOException, InterruptedException {
            // remove host container
            try {
                d.getDockerCli().removeContainerCmd(HOST_CONTAINER_NAME)
                        .withForce(true)
                        .withRemoveVolumes(true)
                        .exec();
                LOG.info("Removed container {}", HOST_CONTAINER_NAME);
            } catch (NotFoundException ignore) {
            }

            // remove data image
            try {
                d.getDockerCli().removeImageCmd(DATA_IMAGE_TAG)
                        .withForce(true)
                        .exec();
                LOG.info("Removed image {}", DATA_IMAGE_TAG);
            } catch (NotFoundException ignore) {
            }


            final File buildDir = folder.newFolder(UserNameNginxProxyAuthTest.class.getName());

            File resources = new File(String.format("src/test/resources/%s/docker",
                    UserNameNginxProxyAuthTest.class.getName().replace(".", "/"))
            );
            FileUtils.copyDirectory(resources, buildDir);

            final String imageId = d.getDockerCli().buildImageCmd(buildDir)
                    .withForcerm(true)
                    .withTag(DATA_IMAGE_TAG)
                    .exec(new BuildImageResultCallback() {
                        public void onNext(BuildResponseItem item) {
                            String text = item.getStream();
                            if (nonNull(text)) {
                                LOG.debug(StringUtils.removeEnd(text, DockerRule.NL));
                            }
                            super.onNext(item);
                        }
                    })
                    .awaitImageId();

            hostContainerId = d.getDockerCli().createContainerCmd(imageId)
                    .withName(HOST_CONTAINER_NAME)
                    .withHostConfig(HostConfig.newHostConfig()
                            .withPortBindings(PortBinding.parse("0.0.0.0:" + CONTAINER_PORT + ":" + CONTAINER_PORT))
                    )
//                .withExposedPorts(new ExposedPort(4243))
//                .withPortSpecs(String.format("%d/tcp", CONTAINER_PORT))
                    .exec()
                    .getId();

            d.getDockerCli().startContainerCmd(hostContainerId).exec();

            d.waitDindStarted(hostContainerId);
        }
    }

    @Test
    public void testAuth() throws Throwable {
        String jenkinsId = d.runFreshJenkinsContainer(PULL_ALWAYS, false);
        final DockerCLI cli = d.createCliForContainer(jenkinsId);
        caller(cli, new TestCallable(
                        cli.jenkins.getPort(),
                        d.clientConfig.getDockerHost(),
                        DockerRule.SLAVE_IMAGE_JNLP
                )
        );
    }

    private static class TestCallable extends BCallable {
        private final int jenkinsPort;
        private final URI dockerUri;
        private final String slaveImage;

        public TestCallable(int jenkinsPort, URI dockerUri, String slaveImage) {
            this.jenkinsPort = jenkinsPort;
            this.dockerUri = dockerUri;
            this.slaveImage = slaveImage;
        }

        @Override
        public Boolean call() throws Throwable {
            final String dockerLabel = "docker-label";
            final Jenkins jenkins = Jenkins.getActiveInstance();

            // prepare jenkins global (url, cred)
            JenkinsLocationConfiguration.get().setUrl(String.format("http://%s:%d", dockerUri.getHost(), jenkinsPort));

            final UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(
                    CredentialsScope.GLOBAL,
                    null,
                    "description",
                    "docker",
                    "docker"
            );

            SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

            // prepare Docker Cloud
            final DockerConnector dockerConnector = new DockerConnector(
                    String.format("tcp://%s:%d", dockerUri.getHost(), CONTAINER_PORT)
            );
            dockerConnector.setConnectTimeout(50 * 1000);
            dockerConnector.setConnectorType(JERSEY);
            dockerConnector.setCredentialsId(credentials.getId());
            dockerConnector.testConnection();
//            final Version version = dockerConnector.getClient().versionCmd().exec();
//            LOG.info("Version {}", version);

            final DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher();
            final DockerPullImage pullImage = new DockerPullImage();
            pullImage.setPullStrategy(PULL_ALWAYS);

            final DockerRemoveContainer removeContainer = new DockerRemoveContainer();
            removeContainer.setRemoveVolumes(true);
            removeContainer.setForce(true);

            final DockerContainerLifecycle containerLifecycle = new DockerContainerLifecycle();
            containerLifecycle.setImage(slaveImage);
            containerLifecycle.setPullImage(pullImage);
            containerLifecycle.setRemoveContainer(removeContainer);

            final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate();
            slaveTemplate.setLabelString(dockerLabel);
            slaveTemplate.setLauncher(launcher);
            slaveTemplate.setMode(Node.Mode.EXCLUSIVE);
            slaveTemplate.setRetentionStrategy(new DockerOnceRetentionStrategy(10));
            slaveTemplate.setDockerContainerLifecycle(containerLifecycle);

            final List<DockerSlaveTemplate> templates = new ArrayList<>();
            templates.add(slaveTemplate);

            final DockerCloud dockerCloud = new DockerCloud(
                    "docker",
                    templates,
                    3,
                    dockerConnector
            );

            jenkins.clouds.add(dockerCloud);
            jenkins.save(); // either xmls a half broken

            return true;
        }
    }
}