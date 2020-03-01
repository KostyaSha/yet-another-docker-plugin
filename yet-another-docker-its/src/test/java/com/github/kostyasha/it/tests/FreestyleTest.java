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
import com.github.kostyasha.yad.DockerNodeProperty;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerPullImage;
import com.github.kostyasha.yad.commons.DockerRemoveContainer;
import com.github.kostyasha.yad.connector.CloudNameDockerConnector;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad.steps.DockerShellStep;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.PullImageResultCallback;
import hudson.cli.DockerCLI;
import hudson.logging.LogRecorder;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import static com.github.kostyasha.it.utils.JenkinsRuleHelpers.caller;
import static com.github.kostyasha.it.utils.JenkinsRuleHelpers.waitUntilNoActivityUpTo;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_LATEST;
import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;
import static com.github.kostyasha.yad.other.ConnectorType.NETTY;
import static com.github.kostyasha.yad.other.ConnectorType.OKHTTP;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.jvnet.hudson.test.JenkinsRule.getLog;

/**
 * @author Kanstantsin Shautsou
 */
@RunWith(Parameterized.class)
public class FreestyleTest implements Serializable {
    public static final Logger LOG = LoggerFactory.getLogger(FreestyleTest.class);
    private static final long serialVersionUID = 1L;
    private static final String DOCKER_CLOUD_LABEL = "docker-label";
    private static final String DOCKER_CLOUD_NAME = "docker-cloud";
    private static final String TEST_VALUE = "2323re23e";
    private static final String CONTAINER_ID = "CONTAINER_ID";
    private static final String CLOUD_ID = "CLOUD_ID";
    private static final String DOCKER_HOST = "SOME_HOST";

    @Parameterized.Parameters(name = "{0} {1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                        {OKHTTP, DockerRule.SLAVE_IMAGE_JNLP},
                        {JERSEY, DockerRule.SLAVE_IMAGE_JNLP},
                        {NETTY, DockerRule.SLAVE_IMAGE_JNLP},
                        {OKHTTP, "java:8-jdk-alpine"},
                        {JERSEY, "java:8-jdk-alpine"},
                        {NETTY, "java:8-jdk-alpine"},
                }
        );
    }

    @Parameterized.Parameter(0)
    public static String slaveJnlpImage;
    @Parameterized.Parameter(1)
    public ConnectorType connectorType;

    //TODO redesign rule internals
    @ClassRule
    public static DockerRule d = new DockerRule(false);

    @Rule
    public MyResource dJenkins = new MyResource(slaveJnlpImage);

    public static class MyResource extends DockerResource {
        public String jenkinsId;
        public DockerCLI cli;
        private String slaveJnlpImage;

        public MyResource(String slaveJnlpImage) {
            this.slaveJnlpImage = slaveJnlpImage;
        }

        public Boolean call(BCallable callable) throws Throwable {
            return caller(cli, callable);
        }

        @Override
        protected void before() throws Throwable {
            d.getDockerCli().pullImageCmd(slaveJnlpImage).exec(new PullImageResultCallback()).awaitSuccess();

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
            final Jenkins jenkins = Jenkins.getInstance();

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
                    dockerServerCredentials.getId(), ConnectorType.NETTY, 10 * 1000, 11 * 1000));
            checkFormValidation(descriptor.doTestConnection(dockerUri.toString(), "",
                    dockerServerCredentials.getId(), JERSEY, 10 * 1000, 11 * 1000));

            // prepare Docker Cloud
            final DockerConnector dockerConnector = new DockerConnector(dockerUri.toString());
            dockerConnector.setCredentialsId(dockerServerCredentials.getId());
            dockerConnector.setConnectTimeout(10 * 1000);
            dockerConnector.setReadTimeout(0);
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
            nodeProperties.add(new DockerNodeProperty(CONTAINER_ID, CLOUD_ID, DOCKER_HOST));


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
        try {
            dJenkins.call(new FreestyleProjectBuildCallable());
        } catch (Exception ex) {
            throw ex;
        }
    }

    private static class FreestyleProjectBuildCallable extends BCallable {
        @Override
        public Boolean call() throws Throwable {
            final Jenkins jenkins = Jenkins.getInstance();

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
            assertThat(lastBuild, not(nullValue()));
            assertThat(lastBuild.getResult(), is(Result.SUCCESS));

            assertThat(getLog(lastBuild), Matchers.containsString(TEST_VALUE));
            assertThat(getLog(lastBuild), Matchers.containsString(CLOUD_ID + "=" + DOCKER_CLOUD_NAME));

            return true;
        }
    }

    @Test
    public void dockerShell() throws Throwable {
        Boolean result = dJenkins.call(new DockerShellCallable(slaveJnlpImage));
        assertThat(result, is(true));
    }

    private static class DockerShellCallable extends BCallable {
        private String image;

        public DockerShellCallable(String image) {
            this.image = image;
        }

        @Override
        public Boolean call() throws Throwable {
            final Jenkins jenkins = Jenkins.getInstance();
            assertThat(image, notNullValue());

            // prepare job
            final FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "freestyle-dockerShell");
            final Shell env = new Shell("env");
            project.getBuildersList().add(env);

            DockerShellStep dockerShellStep = new DockerShellStep();
            dockerShellStep.setShellScript("env && pwd");
            dockerShellStep.getContainerLifecycle().setImage(image);
            dockerShellStep.setConnector(new CloudNameDockerConnector(DOCKER_CLOUD_NAME));
            project.getBuildersList().add(dockerShellStep);

//            project.setAssignedLabel(new LabelAtom(DOCKER_CLOUD_LABEL));
            project.save();

            LOG.trace("trace test.");
            project.scheduleBuild(new TestCause());

            // image pull may take time
            waitUntilNoActivityUpTo(jenkins, 10 * 60 * 1000);

            final FreeStyleBuild lastBuild = project.getLastBuild();
            assertThat(lastBuild, not(nullValue()));
            assertThat(lastBuild.getResult(), is(Result.SUCCESS));

            assertThat(getLog(lastBuild), Matchers.containsString("exit code: 0"));

            return true;
        }
    }
}
