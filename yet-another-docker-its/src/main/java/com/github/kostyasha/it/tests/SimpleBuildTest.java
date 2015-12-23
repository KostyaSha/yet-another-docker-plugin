package com.github.kostyasha.it.tests;

import com.cloudbees.plugins.credentials.CredentialsScope;
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

            channel.call(new MyCallable(cli.jenkins.getPort()));

        } finally {
            cli.close();
        }
    }

    private static class MyCallable implements Callable<Boolean, Throwable> {
        private static final long serialVersionUID = 1L;
        private final int jenkinsPort;

        public MyCallable(int jenkinsPort) {
            this.jenkinsPort = jenkinsPort;
        }

        @Override
        public Boolean call() throws Throwable {
            final String dockerLabel = "docker-label";

            final Jenkins jenkins = Jenkins.getActiveInstance();
            JenkinsLocationConfiguration.get().setUrl("http://192.168.99.100:"+ jenkinsPort);

            final DockerServerCredentials dockerServerCredentials = new DockerServerCredentials(
                    CredentialsScope.GLOBAL, // scope
                    null, // name
                    null, //desc
                    "-----BEGIN RSA PRIVATE KEY-----\n" +
                            "MIIEpAIBAAKCAQEAuufPBrSaxy/hFmYCb2dNRXSeNZ6x8M7RL69qrP/H3pBvXZyr\n" +
                            "oEdJ4eIPfC6DQMe/kBExSyaNnKYibMNuS0jBK3RnCiJ+wh2Nc83qeqPC3X3GHkkR\n" +
                            "ATCDjDBVbNzN6RUaZQrKR6M7n75aeCkh3qxi+7CZq2gy91RokePG4A6hAfuoF9H9\n" +
                            "xP+CO8rqc4d/WhOyOlMSAyn1wnpGqAzDDB3B2l0orkNTydQrZcPF273fbLHv9Sa3\n" +
                            "xfjXzXIf9xwYyJKoNKGWuuQZK+Rk1dMlsuJo33HW+U36/a6teJxpFvThaS1Incl3\n" +
                            "OIcQWAbwQiO+2aWXkSkUlzAKPtIk+zZjcuJ5/QIDAQABAoIBAQCRBbqyRkJuWW06\n" +
                            "Nu6eyDXBtanoivkgkyjm6iJIl7Las5FlvmHA3G+sT/6Z6XE4O4Uc4OoxmHl62cGO\n" +
                            "SNl0msAf2pL03y0hq1KNT3IntJdHywaFi0YheSYpCXvPG0i+GPzA9+1aRoLGASor\n" +
                            "YCcCoxmulym1QQWCuUDmKimuwksVwH4IunwyrRG9BTwuMDNi3PSywBOwq6aSOjn8\n" +
                            "xiS7Dft/GSUyja8JevkAIQf4od9Xn8qYDzUKft+G3fzPbK/g6X7ZDhrxA69je0G0\n" +
                            "kYQT9FE1JF8HOezfN7jBANOy9nyv43QV2dKsZwDtnhBLFlZEFYmFQnBZS057IwIl\n" +
                            "kacMofW1AoGBANvVB6TfxdlrwbA0FlYzUVzHzhxjcysB/iCDaSY1YekZ3M15UlLz\n" +
                            "A7/0m4rOZ5o60Zk4yykNGlv6IrAH8WSD0n3+sk3vdaT7srMc6TTVDeOfQLrjN37M\n" +
                            "ZTDM75hk24zWJkqn284PWynxK/oFGLHc9e0Reb+nHaWw9FpNkw/6U9ePAoGBANmn\n" +
                            "9mNHx9tL2u0IzXy5y27Y0Vun172JlOv6BJdXIBjnYhqEcaWv3RQdw3fkvGv2Ky2H\n" +
                            "2H+n60Fxm1N+Sjer12tq2w0sMqtZMgP2uqUwNa/hB8B/vkX6La6/kqIb9F7EVSuI\n" +
                            "59qEIMrqlAdf31dNL5cINzrm0gziZSXfO2eTmK+zAoGASmKrU37k081CnP0DEegL\n" +
                            "f/mcJL8CGWtzMk1FJ0io+Ndnf5+t26OfgTSj7TQqmmWMxuwQ0rM8WCMr2aTWacyx\n" +
                            "TTEB5J1CkbEZpsIBp37wVDVvEc4Q2TcQhpLSAB8gq2dLTbe/CNrpXifdWZyf3o+G\n" +
                            "J9HiAJfr0EUwad4WBTIPbMECgYA+hftHJbWJj07CTIcKzyxbGTl6xMo6ji0TZGyx\n" +
                            "NLvpq34I9AbZe51cS/h8ll5x/PMGT0Gn2grAb+wYneyf9WMaXkWykQG1Kxgs/1E6\n" +
                            "ZpDlhxT8/TUhUO6ShkGPA8y89FUq/lbr9Iye/aesPqQfpfKHxjpnVyr7vIUlzex4\n" +
                            "onN39QKBgQC4diHTd/QtDgfC9hUPg1yiWELEbxjKBx/F6idvb5AAp2rBfDpGoBvw\n" +
                            "vlL6WGv5CIKf6ayaqcrqiV8gbAJvKd03J07laOmq5x3m/XYs4bVk9vjL+D4ql75T\n" +
                            "7aqeo3AJy/J0rFlnY+vtG6H9AHjo0boZGeCjonpI35lc03/CLRqddg==\n" +
                            "-----END RSA PRIVATE KEY-----",
                    "-----BEGIN CERTIFICATE-----\n" +
                            "MIIC4DCCAcqgAwIBAgIRAKDwrMGMPwpySzUxkYl8XOMwCwYJKoZIhvcNAQELMBIx\n" +
                            "EDAOBgNVBAoTB2ludGVnZXIwHhcNMTUwNzA3MTczMjAwWhcNMTgwNjIxMTczMjAw\n" +
                            "WjASMRAwDgYDVQQKEwdpbnRlZ2VyMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n" +
                            "CgKCAQEAuufPBrSaxy/hFmYCb2dNRXSeNZ6x8M7RL69qrP/H3pBvXZyroEdJ4eIP\n" +
                            "fC6DQMe/kBExSyaNnKYibMNuS0jBK3RnCiJ+wh2Nc83qeqPC3X3GHkkRATCDjDBV\n" +
                            "bNzN6RUaZQrKR6M7n75aeCkh3qxi+7CZq2gy91RokePG4A6hAfuoF9H9xP+CO8rq\n" +
                            "c4d/WhOyOlMSAyn1wnpGqAzDDB3B2l0orkNTydQrZcPF273fbLHv9Sa3xfjXzXIf\n" +
                            "9xwYyJKoNKGWuuQZK+Rk1dMlsuJo33HW+U36/a6teJxpFvThaS1Incl3OIcQWAbw\n" +
                            "QiO+2aWXkSkUlzAKPtIk+zZjcuJ5/QIDAQABozUwMzAOBgNVHQ8BAf8EBAMCAIAw\n" +
                            "EwYDVR0lBAwwCgYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADALBgkqhkiG9w0BAQsD\n" +
                            "ggEBAAJCQF6tNqZeR1xNifxdI9ggXl9UwBiz8yDgi3eElY99uCjQAFbcaP1y0uat\n" +
                            "CelG+tQ/Tm4FEdQSxa8Y1u3g7DUlMYyHwMbQ/z2EYqgRgGP5kCQ0ZSqZN02hre3f\n" +
                            "5uM69Jm16tXZnleYBmk1AOSexnjiaX8YdX+/cZaQJ5Jtwdf52CfVi+2GvKtpL9+m\n" +
                            "WAiaW2T3K9g5BQM7Hdr1gfVF6NRMLMNsNAAghkuAM2WCxuwYjVFz6MdLaG0opwlr\n" +
                            "+JxFK4O0FrxxkYxvfBOSWe39RpqRCz0XFlSGiBDRb5hvVBMuUt1HY8+5lVlqKZHt\n" +
                            "GWQIN+jQbvR+/VmbkU/PrYuBwsE=\n" +
                            "-----END CERTIFICATE-----",
                    "-----BEGIN CERTIFICATE-----\n" +
                            "MIICzjCCAbigAwIBAgIRAIKm0vsudBl/pfiz/hs5ojIwCwYJKoZIhvcNAQELMBIx\n" +
                            "EDAOBgNVBAoTB2ludGVnZXIwHhcNMTUwNzA3MTczMjAwWhcNMTgwNjIxMTczMjAw\n" +
                            "WjASMRAwDgYDVQQKEwdpbnRlZ2VyMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n" +
                            "CgKCAQEAxsGzsCD/8f7ioQycjJT5YB14b0dbKFIqW31zppSJp0x1GvZmWisMhe4T\n" +
                            "rHjEctDpZHTcK5NFpoqYisRgpou0L/8CYaTaqtzLjRTp0zgO6fkgGaCBjHnm+To7\n" +
                            "wQLIcJPRGH+KQpuJzEDWWZtWgMwtxRdHcrW+gZjC6qSTnMprOYMOwwHcn1faznnl\n" +
                            "bV7aWfSq/eeTc/UCfKbtkGSZhmPjdJgJiPkJ4uFLWFE6nuQTEm06dol72zXWK07v\n" +
                            "1gfmO2xi6fWz2HwUYG29bUuoRtjYwwjSt5jixntDRyhsuFapMHy16gsObOi453s0\n" +
                            "+YeBoKVjJHPkB+0uXeCHwtwKVN2+RQIDAQABoyMwITAOBgNVHQ8BAf8EBAMCAKQw\n" +
                            "DwYDVR0TAQH/BAUwAwEB/zALBgkqhkiG9w0BAQsDggEBAJDzA6FiNAR29Z56MGOI\n" +
                            "zOOrpGUCW3014enmqqv5YODzxrIxnMorkX6E6H5STthOtkeOMx7gbqLtBAFlXYa1\n" +
                            "BIbrO4XKbrhm/0v3b+FILBrZHB+Am3CFIFhSG5PXnlsfwFm8nX2VL12Aecx8iKkL\n" +
                            "PJuMQVlTgArCo0Lb0+nqUvJyHFZDRGR4C1VEPLOT5azD2rCQbGHk+tz3T/sYO38T\n" +
                            "tR/9Dx9a9OGFPwr8I1dLPyuruxZgRZ1pEOHh0LyX6tuGmoFuiSCTrErKTSg6A8ZH\n" +
                            "/+B3zrVogG++jrii9P7b0DqCcXJxf3pToCOV7M7oV8IaIwffjDPppCLYvVaeerqH\n" +
                            "j1M=\n" +
                            "-----END CERTIFICATE-----"
            );
            SystemCredentialsProvider.getInstance().getCredentials().add(dockerServerCredentials);

            final DockerConnector dockerConnector = new DockerConnector("https://192.168.99.100:2376")
                    .setCredentialsId(dockerServerCredentials.getId())
                    .setConnectTimeout(10);
            dockerConnector.testConnection();

            final DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher(new JNLPLauncher());
            final DockerContainerLifecycle containerLifecycle = new DockerContainerLifecycle()
                    .setImage("kostyasha/jenkins-slave:jdk-wget")
                    .setPullImage(new DockerPullImage().setPullStrategy(PULL_ALWAYS))
                    .setRemoveContainer(new DockerRemoveContainer().setRemoveVolumes(true).setForce(true));

            final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate()
                    .setLabelString(dockerLabel)
                    .setLauncher(launcher)
                    .setMode(Node.Mode.EXCLUSIVE)
                    .setRetentionStrategy(new DockerOnceRetentionStrategy(10))
                    .setDockerContainerLifecycle(containerLifecycle);

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
