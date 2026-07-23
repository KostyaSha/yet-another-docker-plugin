package org.jvnet.hudson.test;

import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerSimpleBuildWrapper;
import com.github.kostyasha.yad.DockerSlaveConfig;
import com.github.kostyasha.yad.launcher.DockerComputerSingleJNLPLauncher;
import com.github.kostyasha.yad.launcher.NoOpDelegatingComputerLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Shell;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerSimpleBuildWrapperTest {
    private static final Logger LOG = LoggerFactory.getLogger(DockerSimpleBuildWrapperTest.class);

    // switch to Inet4Address?
    private static final String ADDRESS = "192.168.1.3";

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Ignore("For local experiments")
    @Test
    public void testWrapper() throws Exception {
        final FreeStyleProject project = jRule.createProject(FreeStyleProject.class, "freestyle");

        final DockerConnector connector = new DockerConnector("tcp://" + ADDRESS + ":2376/");
        connector.setConnectorType(JERSEY);

        final DockerSlaveConfig config = new DockerSlaveConfig();
        config.getDockerContainerLifecycle().setImage("java:8-jdk-alpine");
        config.setLauncher(new NoOpDelegatingComputerLauncher(new DockerComputerSingleJNLPLauncher()));
        config.setRetentionStrategy(new DockerOnceRetentionStrategy(10));

        final DockerSimpleBuildWrapper dockerSimpleBuildWrapper = new DockerSimpleBuildWrapper(connector, config);
        project.getBuildWrappersList().add(dockerSimpleBuildWrapper);
        project.getBuildersList().add(new Shell("sleep 30"));

        final QueueTaskFuture<FreeStyleBuild> taskFuture = project.scheduleBuild2(0);

        jRule.waitUntilNoActivity();
        jRule.pause();
    }

}
