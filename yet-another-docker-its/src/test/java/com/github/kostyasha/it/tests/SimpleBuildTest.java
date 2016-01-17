package com.github.kostyasha.it.tests;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.github.kostyasha.it.other.TCallable;
import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerContainerLifecycle;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerPullImage;
import com.github.kostyasha.yad.commons.DockerRemoveContainer;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.cli.DockerCLI;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.remoting.Channel;
import hudson.slaves.JNLPLauncher;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.github.kostyasha.it.utils.JenkinsRuleHelpers.waitUntilNoActivityUpTo;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_ALWAYS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.isNull;

/**
 * @author Kanstantsin Shautsou
 */
public class SimpleBuildTest implements Serializable {
    public static final Logger LOG = LoggerFactory.getLogger(SimpleBuildTest.class);
    private static final long serialVersionUID = 1L;

    @ClassRule
    public static DockerRule d = new DockerRule(false);

    @Test
    public void addDockerCloudFromTest() throws Throwable {
        String jenkinsId = d.runFreshJenkinsContainer(PULL_ALWAYS, false);
        final DockerCLI cli = d.createCliForContainer(jenkinsId);
        try (Channel channel = cli.getChannel()) {
            assertThat(
                    channel.call(
                            new TestCallable(
                                    cli.jenkins.getPort(),
                                    d.getDockerServerCredentials(),
                                    d.clientConfig.getUri(),
                                    DockerRule.SLAVE_IMAGE_JNLP
                            )),
                    equalTo(true)
            );
        } finally {
            cli.close();
        }
    }

    private static class TestCallable extends TCallable<Boolean, Throwable> {
        private final int jenkinsPort;
        private final DockerServerCredentials dockerServerCredentials;
        private final URI dockerUri;
        private final String slaveImage;

        public TestCallable(int jenkinsPort, DockerServerCredentials credentials, URI dockerUri, String slaveImage) {
            this.jenkinsPort = jenkinsPort;
            this.dockerServerCredentials = credentials;
            this.dockerUri = dockerUri;
            this.slaveImage = slaveImage;
        }

        @Override
        public Boolean call() throws Throwable {
            final String dockerLabel = "docker-label";
            final Jenkins jenkins = Jenkins.getActiveInstance();

            // prepare jenkins global (url, cred)
            JenkinsLocationConfiguration.get().setUrl(String.format("http://%s:%d", dockerUri.getHost(), jenkinsPort));

            SystemCredentialsProvider.getInstance().getCredentials().add(dockerServerCredentials);

            // prepare Docker Cloud
            final DockerConnector dockerConnector = new DockerConnector(
                    String.format("https://%s:%d", dockerUri.getHost(), dockerUri.getPort()));
            dockerConnector.setCredentialsId(dockerServerCredentials.getId());
            dockerConnector.setConnectTimeout(10);
            dockerConnector.testConnection();

            final DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher(new JNLPLauncher());
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

            // prepare job
            final FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "freestyle-project");
            final Shell env = new Shell("env");
            project.getBuildersList().add(env);
            project.setAssignedLabel(new LabelAtom(dockerLabel));
            project.save();

            // test
            project.scheduleBuild();

            waitUntilNoActivityUpTo(jenkins, 60 * 1000);

            final FreeStyleBuild lastBuild = project.getLastBuild();
            assertThat(lastBuild, not(isNull()));
            assertThat(lastBuild.getResult(), is(Result.SUCCESS));

            return true;
        }
    }
}
