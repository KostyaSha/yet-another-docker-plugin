package com.github.kostyasha.yad.steps;


import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.CredentialsYADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.LocalDirectorySSLConfig;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;
import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * @author Kanstantsin Shautsou
 */
@Ignore
public class DockerShellStepTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void testDockerShellStep() throws Throwable {
        jRule.getInstance().setNumExecutors(0);
//        jRule.createSlave();
//        jRule.createSlave("my-slave", "remote-slave", new EnvVars());
        final UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM,
                null, "description", "vagrant", "vagrant");

        CredentialsStore store = CredentialsProvider.lookupStores(jRule.getInstance()).iterator().next();
        store.addCredentials(Domain.global(), credentials);

        final SSHLauncher sshLauncher = new SSHLauncher("192.168.33.10", 22, credentials.getId(),
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", //jvmopts
                "", //      String javaPath,
                "",  //      String prefixStartSlaveCmd,
                "", //      String suffixStartSlaveCmd,
                20, //      Integer launchTimeoutSeconds,
                1, //      Integer maxNumRetries,
                3//      Integer retryWaitTime
        );
        final DumbSlave dumbSlave = new DumbSlave("docker-daemon", "/home/vagrant/jenkins2", sshLauncher);
        jRule.getInstance().addNode(dumbSlave);
        await().timeout(60, SECONDS).until(() -> assertThat(dumbSlave.getChannel(), notNullValue()));
//        String dockerfilePath = dumbSlave.getChannel().call(new DockerBuildImageStepTest.StringThrowableCallable());


        final CredentialsYADockerConnector dockerConnector = new CredentialsYADockerConnector()
                .withConnectorType(JERSEY)
                .withServerUrl("tcp://127.0.0.1:2376")
                .withSslConfig(new LocalDirectorySSLConfig("/home/vagrant/keys"));
//                .withCredentials(new DockerDaemonFileCredentials(null, "docker-cert", "",
//                        "/home/vagrant/keys"));

        DockerShellStep dockerShellStep = new DockerShellStep();
        dockerShellStep.setShellScript("env && pwd");
        dockerShellStep.setConnector(dockerConnector);

        FreeStyleProject project = jRule.createFreeStyleProject("test");
        project.getBuildersList().add(dockerShellStep);

        project.save();

        QueueTaskFuture<FreeStyleBuild> taskFuture = project.scheduleBuild2(0);
        FreeStyleBuild freeStyleBuild = taskFuture.get();

        jRule.waitForCompletion(freeStyleBuild);

        jRule.assertBuildStatusSuccess(freeStyleBuild);
    }
}
