package com.github.kostyasha.yad;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.github.kostyasha.yad.commons.DockerContainerRestartPolicy;
import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad.commons.DockerImagePullStrategy;
import com.github.kostyasha.yad.commons.DockerPullImage;
import com.github.kostyasha.yad.commons.DockerRemoveContainer;
import com.github.kostyasha.yad.commons.DockerStopContainer;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.model.Node;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.hamcrest.Matcher;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;

import static com.github.kostyasha.yad.commons.DockerContainerRestartPolicyName.NO_RESTART;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;

/**
 * Ensure that configuration is the same after configRoundTrip.
 * In one large test because in Cloud it one big page.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerCloudTest {
    public static JenkinsRule j = new JenkinsRule();

    public static PreparedCloud clouds = new PreparedCloud();

    public static class PreparedCloud extends ExternalResource {
        public DockerCloud before;
        public DockerCloud after;

        public DockerSlaveTemplate getTemplateAfter() {
            return after.getTemplates().get(0);
        }

        public DockerContainerLifecycle getLifecycleAfter() {
            return getTemplateAfter().getDockerContainerLifecycle();
        }

        public DockerSlaveTemplate getTemplateBefore() {
            return before.getTemplates().get(0);
        }

        public DockerContainerLifecycle getLifecycleBefore() {
            return getTemplateBefore().getDockerContainerLifecycle();
        }

        @Override
        public void before() throws Exception {
            final DockerServerCredentials dockerServerCredentials = new DockerServerCredentials(
                    CredentialsScope.GLOBAL, // scope
                    null, // id
                    "description", //desc
                    "keypem",
                    "certpem",
                    "capem"
            );
            SystemCredentialsProvider.getInstance().getCredentials().add(dockerServerCredentials);

            final EnvironmentVariablesNodeProperty.Entry entry = new EnvironmentVariablesNodeProperty.Entry("kee", "vasdfs");
            final EnvironmentVariablesNodeProperty variablesNodeProperty = new EnvironmentVariablesNodeProperty(singletonList(entry));

            final DockerConnector connector = new DockerConnector("http://sdfs.com:234");
            connector.setCredentialsId(dockerServerCredentials.getId());

            final DockerPullImage pullImage = new DockerPullImage();
            pullImage.setCredentialsId("");
            pullImage.setPullStrategy(DockerImagePullStrategy.PULL_ALWAYS);

            final DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher();
            launcher.setLaunchTimeout(100);
            launcher.setUser("jenkins");
            launcher.setJenkinsUrl("http://jenkins");
            launcher.setJvmOpts("-blah");
            launcher.setSlaveOpts("-more");
            launcher.setNoCertificateCheck(true);
            launcher.setReconnect(false);

            final DockerCreateContainer createContainer = new DockerCreateContainer();
            createContainer.setBindAllPorts(true);
            createContainer.setBindPorts("234");
            createContainer.setCommand("sdfff");
            createContainer.setCpuShares(3);
            createContainer.setDnsHosts(singletonList("dsf"));
            createContainer.setEnvironment(singletonList("sdf"));
            createContainer.setExtraHosts(singletonList("hoststs"));
            createContainer.setHostname("hostname.local");
            createContainer.setMacAddress("33:44:33:66:66:33");
            createContainer.setMemoryLimit(33333333L);
            createContainer.setPrivileged(false);
            createContainer.setTty(false);
            createContainer.setVolumes(singletonList("ssdf:/sdfsdf/sdf"));
            createContainer.setVolumesFrom(singletonList("sdfsd:/sdfsdf"));
            createContainer.setDevices(singletonList("/dev/sdc:/dev/sdc:rw"));
            createContainer.setCpusetCpus("1");
            createContainer.setCpusetMems("2");
            createContainer.setLinksString("some");
            createContainer.setRestartPolicy(new DockerContainerRestartPolicy(NO_RESTART, 0));

            final DockerStopContainer stopContainer = new DockerStopContainer();
            stopContainer.setTimeout(100);

            final DockerRemoveContainer removeContainer = new DockerRemoveContainer();
            removeContainer.setForce(true);
            removeContainer.setRemoveVolumes(true);


            final DockerContainerLifecycle containerLifecycle = new DockerContainerLifecycle();
            containerLifecycle.setImage("sdf/sdf:df");
            containerLifecycle.setPullImage(pullImage);
            containerLifecycle.setCreateContainer(createContainer);
            containerLifecycle.setStopContainer(stopContainer);
            containerLifecycle.setRemoveContainer(removeContainer);

            final DockerSlaveTemplate dockerSlaveTemplate = new DockerSlaveTemplate();
            dockerSlaveTemplate.setDockerContainerLifecycle(containerLifecycle);
            dockerSlaveTemplate.setLabelString("some-label");
            dockerSlaveTemplate.setLauncher(launcher);
            dockerSlaveTemplate.setMaxCapacity(233);
            dockerSlaveTemplate.setMode(Node.Mode.EXCLUSIVE);
            dockerSlaveTemplate.setNodeProperties(singletonList(variablesNodeProperty));
            dockerSlaveTemplate.setRemoteFs("/remotefs");
            dockerSlaveTemplate.setNumExecutors(1); // need to be verified with other retention strategy
            dockerSlaveTemplate.setRetentionStrategy(new DockerOnceRetentionStrategy(30));

            final ArrayList<DockerSlaveTemplate> dockerSlaveTemplates = new ArrayList<>();
            dockerSlaveTemplates.add(dockerSlaveTemplate);
            before = new DockerCloud("docker-cloud", dockerSlaveTemplates, 17, connector);

            j.getInstance().clouds.add(before);
            j.getInstance().save();

            j.configRoundtrip();

            after = (DockerCloud) j.getInstance().getCloud("docker-cloud");
        }
    }

    @ClassRule
    public static RuleChain chain = RuleChain.outerRule(j).around(clouds);

    @Test
    public void dockerCloud() {
        assertThatConfig(clouds.after,
                equalTo(clouds.before));

        assertThatConfig(clouds.after.getTemplates().size(),
                is(clouds.before.getTemplates().size()));
    }

    @Test
    public void dockerConnector() {
        assertThatConfig(clouds.after.getConnector(),
                equalTo(clouds.before.getConnector()));
    }

    @Test
    public void template() {
        assertThatConfig(clouds.getTemplateAfter(),
                equalTo(clouds.getTemplateBefore()));
    }

    @Test
    public void containerLifecycle() {
        final DockerContainerLifecycle lifecycleBefore = clouds.getTemplateBefore().getDockerContainerLifecycle();
        final DockerContainerLifecycle lifecycleAfter = clouds.getTemplateAfter().getDockerContainerLifecycle();

        assertThatConfig(lifecycleAfter,
                equalTo(lifecycleBefore));
    }

    @Test
    public void pullImage() {
        assertThatConfig(clouds.getLifecycleAfter().getPullImage(),
                equalTo(clouds.getLifecycleBefore().getPullImage()));
    }

    @Test
    public void createContainer() {
        assertThatConfig(clouds.getLifecycleAfter().getCreateContainer(),
                equalTo(clouds.getLifecycleBefore().getCreateContainer()));
    }

    @Test
    public void stopContainer() {
        assertThatConfig(clouds.getLifecycleAfter().getStopContainer(),
                equalTo(clouds.getLifecycleBefore().getStopContainer()));
    }

    @Test
    public void removeContainer() {
        assertThatConfig(clouds.getLifecycleAfter().getRemoveContainer(),
                equalTo(clouds.getLifecycleBefore().getRemoveContainer()));
    }

    public static <T> void assertThatConfig(T actual, Matcher<? super T> matcher) {
        assertThat("Page configuration must match before and after page save", actual, matcher);
    }
}
