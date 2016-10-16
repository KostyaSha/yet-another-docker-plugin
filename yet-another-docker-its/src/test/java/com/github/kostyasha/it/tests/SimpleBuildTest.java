package com.github.kostyasha.it.tests;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.github.kostyasha.it.other.BCallable;
import com.github.kostyasha.it.other.TestCause;
import com.github.kostyasha.it.rule.DockerResource;
import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerConnector.DescriptorImpl;
import com.github.kostyasha.yad.DockerContainerLifecycle;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerPullImage;
import com.github.kostyasha.yad.commons.DockerRemoveContainer;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.cli.DockerCLI;
import hudson.logging.LogRecorder;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import hudson.tasks.Shell;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.For;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import static com.github.kostyasha.it.utils.JenkinsRuleHelpers.caller;
import static com.github.kostyasha.it.utils.JenkinsRuleHelpers.waitUntilNoActivityUpTo;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_LATEST;
import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.jvnet.hudson.test.JenkinsRule.getLog;
import static org.mockito.Matchers.isNull;

/**
 * @author Kanstantsin Shautsou
 */
@RunWith(Parameterized.class)
public class SimpleBuildTest implements Serializable {
    public static final Logger LOG = LoggerFactory.getLogger(SimpleBuildTest.class);
    private static final long serialVersionUID = 1L;
    private static final String DOCKER_CLOUD_LABEL = "docker-label";
    private static final String DOCKER_CLOUD_NAME = "docker-cloud";
    private static final String TEST_VALUE = "2323re23e";

    @Parameterized.Parameters
    public static Iterable<String> data() {
        return Arrays.asList(
                DockerRule.SLAVE_IMAGE_JNLP,
                "java:8-jdk-alpine"
        );
    }

    @Parameterized.Parameter
    public static String slaveJnlpImage;

    //TODO redesign rule internals
    @ClassRule
    public static DockerRule d = new DockerRule(false);

    @Rule
    public MyResource dJenkins = new MyResource();

    public static class MyResource extends DockerResource {
        public String jenkinsId;
        public DockerCLI cli;

        public void call(BCallable callable) throws Throwable {
            caller(cli, callable);
        }

        @Override
        protected void before() throws Throwable {
            jenkinsId = d.runFreshJenkinsContainer(PULL_LATEST, false);
            cli = d.createCliForContainer(jenkinsId);
            LOG.trace("CLI prepared, preparing cloud");
            assertThat(cli, notNullValue());
            assertThat(cli.jenkins, notNullValue());
            assertThat(d, notNullValue());
            assertThat(d.clientConfig, notNullValue());
            assertThat(slaveJnlpImage, notNullValue());

            LOG.trace("Creating  PrepareCloudCallable object");
            try {
                final PrepareCloudCallable prepareCloudCallable = new PrepareCloudCallable(
                        cli.jenkins.getPort(),
                        d.getDockerServerCredentials(),
                        d.clientConfig.getDockerHost(),
                        slaveJnlpImage
                );
                LOG.trace("Calling caller.");
                caller(cli, prepareCloudCallable);
            } catch (NullPointerException ex) {
                LOG.error("HOW NPE HAPPENS HERE?!", ex);
                LOG.trace("cli {}", cli);
                LOG.trace("d.clientConfig {}", d.clientConfig);
                throw ex;
            }
        }

        @Override
        protected void after() {
            try {
                LOG.trace("Closing CLI.");
                cli.close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class PrepareCloudCallable extends BCallable {
        private final int jenkinsPort;
        private final DockerServerCredentials dockerServerCredentials;
        private final URI dockerUri;
        private final String slaveImage;

        public PrepareCloudCallable(int jenkinsPort, DockerServerCredentials credentials,
                                    URI dockerUri, String slaveImage) {
            assertThat("jenkinsPort", jenkinsPort, notNullValue());
            assertThat("credentials", credentials, notNullValue());
            assertThat("dockerUri", dockerUri, notNullValue());
            assertThat("slaveImage", slaveImage, notNullValue());

            this.jenkinsPort = jenkinsPort;
            this.dockerServerCredentials = credentials;
            this.dockerUri = dockerUri;
            this.slaveImage = slaveImage;
        }

        @Override
        public Boolean call() throws Throwable {
            final Jenkins jenkins = Jenkins.getActiveInstance();

            String logName = "com.github.kostyasha.yad";
            final LogRecorder logRecorder = new LogRecorder(logName);
            logRecorder.targets.add(new LogRecorder.Target("com.github.kostyasha.yad", Level.ALL));
            jenkins.getLog().logRecorders.put("logName", logRecorder);
            logRecorder.save();

            // prepare jenkins global (url, cred)
            JenkinsLocationConfiguration.get().setUrl(String.format("http://%s:%d", dockerUri.getHost(), jenkinsPort));

            SystemCredentialsProvider.getInstance().getCredentials().add(dockerServerCredentials);

            //verify doTestConnection
            final DescriptorImpl descriptor = (DescriptorImpl) jenkins.getDescriptor(DockerConnector.class);
            checkFormValidation(descriptor.doTestConnection(dockerUri.toString(), "",
                    dockerServerCredentials.getId(), ConnectorType.NETTY, 10 * 1000));
            checkFormValidation(descriptor.doTestConnection(dockerUri.toString(), "",
                    dockerServerCredentials.getId(), JERSEY, 10 * 1000));

            // prepare Docker Cloud
            final DockerConnector dockerConnector = new DockerConnector(dockerUri.toString());
            dockerConnector.setCredentialsId(dockerServerCredentials.getId());
            dockerConnector.setConnectTimeout(10 * 1000);
            dockerConnector.setConnectorType(JERSEY);
            dockerConnector.testConnection();

            //launcher
            final DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher();
            launcher.setNoCertificateCheck(true);
            launcher.setJvmOpts("-XX:-PrintClassHistogram");
            final DockerPullImage pullImage = new DockerPullImage();
            pullImage.setPullStrategy(PULL_LATEST);

            //remove
            final DockerRemoveContainer removeContainer = new DockerRemoveContainer();
            removeContainer.setRemoveVolumes(true);
            removeContainer.setForce(true);

            //lifecycle
            final DockerContainerLifecycle containerLifecycle = new DockerContainerLifecycle();
            containerLifecycle.setImage(slaveImage);
            containerLifecycle.setPullImage(pullImage);
            containerLifecycle.setRemoveContainer(removeContainer);

            //template
            final Entry entry = new Entry("super-key", TEST_VALUE);
            final EnvironmentVariablesNodeProperty nodeProperty = new EnvironmentVariablesNodeProperty(entry);
            final ArrayList<NodeProperty<?>> nodeProperties = new ArrayList<>();
            nodeProperties.add(nodeProperty);

            final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate();
            slaveTemplate.setMaxCapacity(4);
            slaveTemplate.setLabelString(DOCKER_CLOUD_LABEL);
            slaveTemplate.setLauncher(launcher);
            slaveTemplate.setMode(Node.Mode.EXCLUSIVE);
            slaveTemplate.setRetentionStrategy(new DockerOnceRetentionStrategy(10));
            slaveTemplate.setDockerContainerLifecycle(containerLifecycle);
            slaveTemplate.setNodeProperties(nodeProperties);

            final List<DockerSlaveTemplate> templates = new ArrayList<>();
            templates.add(slaveTemplate);

            final DockerCloud dockerCloud = new DockerCloud(
                    DOCKER_CLOUD_NAME,
                    templates,
                    3,
                    dockerConnector
            );
            jenkins.clouds.add(dockerCloud);
            jenkins.save(); // either xmls a half broken

            return true;
        }

        private static void checkFormValidation(FormValidation formValidation) throws FormValidation {
            if (formValidation.kind != FormValidation.Kind.OK) {
                throw formValidation;
            }
        }
    }

    @Test
    public void freestyleProjectBuilds() throws Throwable {
        dJenkins.call(new FreestyleProjectBuildCallable());
    }

    private static class FreestyleProjectBuildCallable extends BCallable {
        @Override
        public Boolean call() throws Throwable {
            final Jenkins jenkins = Jenkins.getActiveInstance();

            // prepare job
            final FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "freestyle-project");
            final Shell env = new Shell("env");
            project.getBuildersList().add(env);
            project.setAssignedLabel(new LabelAtom(DOCKER_CLOUD_LABEL));
            project.save();

            LOG.trace("trace test.");
            project.scheduleBuild(new TestCause());

            // image pull may take time
            waitUntilNoActivityUpTo(jenkins, 10 * 60 * 1000);

            final FreeStyleBuild lastBuild = project.getLastBuild();
            assertThat(lastBuild, not(isNull()));
            assertThat(lastBuild.getResult(), is(Result.SUCCESS));

            assertThat(getLog(lastBuild), Matchers.containsString(TEST_VALUE));

            return true;
        }
    }
}
