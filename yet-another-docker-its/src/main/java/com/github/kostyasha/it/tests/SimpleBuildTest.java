package com.github.kostyasha.it.tests;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.github.kostyasha.it.other.JenkinsDockerImage;
import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerContainerLifecycle;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerPullImage;
import com.github.kostyasha.yad.commons.DockerRemoveContainer;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.cli.DockerCLI;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.slaves.JNLPLauncher;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.kostyasha.it.utils.JenkinsRuleHelpers.waitUntilNoActivityUpTo;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_ALWAYS;

/**
 * @author Kanstantsin Shautsou
 */
public class SimpleBuildTest implements Serializable {
    public static final Logger LOG = LoggerFactory.getLogger(SimpleBuildTest.class);
    private static final long serialVersionUID = 1L;

    Map<String, String> labels = new HashMap<String, String>() {{
        put(getClass().getPackage().getName(), getClass().getName());
    }};

    public static final JenkinsDockerImage JENKINS_DOCKER_IMAGE = JenkinsDockerImage.JENKINS_1_609_3;
    private static final String SLAVE_IMAGE = "kostyasha/jenkins-slave:jdk-wget";


    @ClassRule
    public static DockerRule d = new DockerRule(false);

    @Test
    public void addDockerCloudFromTest() throws Throwable {
        // mark test method
        labels.put(getClass().getName(), "addDockerCloudFromTest");

        CreateContainerCmd createCmd = d.cli.createContainerCmd(JENKINS_DOCKER_IMAGE.getDockerImageName())
                .withPublishAllPorts(true)
                .withLabels(labels);

        String jenkinsId = d.runFreshJenkinsContainer(createCmd, PULL_ALWAYS, true);
        final DockerCLI cli = d.createCliForContainer(jenkinsId);

        try (Channel channel = cli.getChannel()) {

            channel.call(new MyCallable(
                    cli.jenkins.getPort(),
                    d.getDockerServerCredentials(),
                    d.clientConfig.getUri().getHost()
            ));

        } finally {
            cli.close();
        }
    }

    private static class MyCallable implements Callable<Boolean, Throwable> {
        private static final long serialVersionUID = 1L;
        private final int jenkinsPort;
        private final DockerServerCredentials dockerServerCredentials;
        private final String serverAddr;

        public MyCallable(int jenkinsPort, DockerServerCredentials credentials, String serverAddr) {
            this.jenkinsPort = jenkinsPort;
            this.dockerServerCredentials = credentials;
            this.serverAddr = serverAddr;
        }

        @Override
        public Boolean call() throws Throwable {
            final String dockerLabel = "docker-label";

            final Jenkins jenkins = Jenkins.getActiveInstance();
            JenkinsLocationConfiguration.get().setUrl("http://" + serverAddr + ":" + jenkinsPort);

            SystemCredentialsProvider.getInstance().getCredentials().add(dockerServerCredentials);

            final DockerConnector dockerConnector = new DockerConnector("https://" + serverAddr + ":2376")
                    .setCredentialsId(dockerServerCredentials.getId())
                    .setConnectTimeout(10);
            dockerConnector.testConnection();

            final DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher(new JNLPLauncher());
            final DockerContainerLifecycle containerLifecycle = new DockerContainerLifecycle()
                    .setImage(SLAVE_IMAGE)
                    .setPullImage(new DockerPullImage().setPullStrategy(PULL_ALWAYS))
                    .setRemoveContainer(new DockerRemoveContainer().setRemoveVolumes(true).setForce(true));

            final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate()
                    .setLabelString(dockerLabel)
                    .setLauncher(launcher)
                    .setMode(Node.Mode.EXCLUSIVE)
                    .setRetentionStrategy(new DockerOnceRetentionStrategy(10))
                    .setDockerContainerLifecycle(containerLifecycle);

            final List<DockerSlaveTemplate> templates = new ArrayList<>();
            templates.add(slaveTemplate);

            final DockerCloud dockerCloud = new DockerCloud(
                    "docker",
                    templates,
                    3,
                    dockerConnector
            );

            jenkins.clouds.add(dockerCloud);

            final FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "freestyle-project");
            final Shell env = new Shell("env");
            project.getBuildersList().add(env);
            project.setAssignedLabel(new LabelAtom(dockerLabel));
            project.save();

            project.scheduleBuild();

            waitUntilNoActivityUpTo(jenkins, 60 * 1000);

            if (project.getLastBuild() == null) {
                throw new AssertionError("Build not found");
            }

            return true;
        }


        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
        }
    }
}
