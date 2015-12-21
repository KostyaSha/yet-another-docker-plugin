package com.github.kostyasha.it;

import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.cli.CLI;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.github.kostyasha.it.utils.DockerHPIContainerUtil.getResource;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_ALWAYS;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Kanstantsin Shautsou
 */
public class SomeTest implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(SomeTest.class);

    Map<String, String> labels = new HashMap<String, String>() {{
        put(SomeTest.class.getPackage().getName(), SomeTest.class.getName());
    }};

    public static final String IMAGE_NAME = "jenkins:1.609.3";

    @ClassRule
    public static DockerRule d = new DockerRule(false);

    /**
     * classloading issues example.
     */
    @Test
    public void dockerCloudDescriptorMissing() throws Exception {
        CreateContainerCmd createCmd = d.cli.createContainerCmd(IMAGE_NAME)
                .withPublishAllPorts(true)
                .withLabels(labels); // mark test method

        String jenkinsId = d.runFreshJenkinsContainer(createCmd, PULL_ALWAYS, true);
        // make standard CLI to remote jenkins
        final CLI cli = d.createCliForContainer(jenkinsId);

        try (Channel channel = cli.getChannel()) {
            LOG.info("Remoteclassloading allowed {}", channel.isRemoteClassLoadingAllowed());

            LOG.info("Descriptor for Shell {}",
                    channel.call(new CloudToStringCallable() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public String call() throws Exception {
                            final Shell shell = new Shell("");
                            // works, descriptor exists
                            return shell.getDescriptor().toString();
                        }
                    })
            );

            LOG.info("DockerCloud Descriptor: {}",
                    channel.call(new CloudToStringCallable() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public String call() throws Exception {
                            final DockerCloud dockerCloud = new DockerCloud(null, null, 0, null);
                            // fails because this descriptor doesn't exist in jenkins for this class.
                            // It exists for other class with the same package+name. Different classloaders??
                            return dockerCloud.getDescriptor().toString();
                        }
                    }));
        } catch (Exception ex) {
            LOG.error("", ex);
            fail();
        } finally {
            cli.close();
        }
    }


    @Test
    public void configureWithGroovyHack() throws Exception {
        CreateContainerCmd createCmd = d.cli.createContainerCmd(IMAGE_NAME)
                .withPublishAllPorts(true)
                .withLabels(labels);

        String jenkinsId = d.runFreshJenkinsContainer(createCmd, PULL_ALWAYS, true);
        final CLI cli = d.createCliForContainer(jenkinsId);

        try (Channel channel = cli.getChannel()) {
            final String resource = getResource(getClass(), "AddDocker.groovy");

            assertTrue(channel.call(
                    new Callable<Boolean, Exception>() {
                        @Override
                        public void checkRoles(RoleChecker checker) throws SecurityException {
                        }

                        @Override
                        public Boolean call() throws Exception {
                            final Binding binding = new Binding();
                            binding.setVariable("testVar", "testValue");

                            final ClassLoader cl = Jenkins.getInstance().pluginManager.uberClassLoader;
                            GroovyShell shell = new GroovyShell(cl, binding);
                            return (Boolean) shell.evaluate(resource);
                        }

                        private static final long serialVersionUID = 1L;
                    })
            );

        } finally {
            cli.close();
        }
    }


    @Test
    public void someTest() throws Exception {
        // mark test method
        labels.put(SomeTest.class.getName(), "someTest");

        CreateContainerCmd createCmd = d.cli.createContainerCmd(IMAGE_NAME)
                .withPublishAllPorts(true)
                .withLabels(labels);

        String jenkinsId = d.runFreshJenkinsContainer(createCmd, PULL_ALWAYS, true);
        final CLI cli = d.createCliForContainer(jenkinsId);

        try (Channel channel = cli.getChannel()) {
            LOG.info("Remoteclassloading allowed {}", channel.isRemoteClassLoadingAllowed());
            LOG.info("Server jobs: {}",
                    channel.call(new CloudToStringCallable() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void checkRoles(RoleChecker checker) throws SecurityException {
                        }

                        public String call() throws Exception {
                            final Jenkins instance = Jenkins.getInstance();
                            final FreeStyleProject project = instance.createProject(FreeStyleProject.class, "freestyle-project");
                            final Shell env = new Shell("env");
                            project.getBuildersList().add(env);
                            project.save();
                            // this portion executes inside the Jenkins server JVM.
                            return Jenkins.getInstance().getItems().toString();
                        }
                    }));

            String res = channel.call(new Callable<String, RuntimeException>() {
                @Override
                public void checkRoles(RoleChecker checker) throws SecurityException {
                }

                public String call() {
                    // [com.github.kostyasha.yad.DockerCloud$DescriptorImpl@8f5678a] -> exist Descriptor
                    return Cloud.all().toString();
                }
            });
            LOG.info("Cloud.all() : {}", res);

            //adding new cloud that will have "missing descriptor"
            Boolean ress = channel.call(
                    new Callable<Boolean, Exception>() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void checkRoles(RoleChecker checker) throws SecurityException {
                        }

                        @Override
                        public Boolean call() throws Exception {
                            final DockerConnector dockerConnector = new DockerConnector("https://192.168.99.100:2376");
                            final DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher(new JNLPLauncher());
                            final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate()
                                    .setLabelString("docker-label")
                                    .setLauncher(launcher)
                                    .setMode(Node.Mode.EXCLUSIVE)
                                    .setRetentionStrategy(new DockerOnceRetentionStrategy(10));
                            final ArrayList<DockerSlaveTemplate> templates = new ArrayList<>();
                            templates.add(slaveTemplate);

                            final DockerCloud dockerCloud = new DockerCloud(
                                    "docker",
                                    templates,
                                    3,
                                    dockerConnector
                            );
                            // dockerCloud has no descriptor at this point
                            //
                            return Jenkins.getActiveInstance().clouds.add(dockerCloud);
                        }
                    }
            );
            LOG.info("The server has: {}", ress);

            // contains DockerCloud -> we added
            LOG.info("Docker.clouds after: " +
                    channel.call(
                            new CloudToStringCallable()
                    ));
        } finally {
            cli.close();
        }
    }

    static class CloudToStringCallable implements Callable<String, Exception> {
        private static final long serialVersionUID = 1L;

        @Override
        public String call() throws Exception {
            return Jenkins.getInstance().clouds.toString();
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
        }
    }
}
