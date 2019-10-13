package com.github.kostyasha.yad.steps;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.CredentialsYADockerConnector;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad.credentials.DockerDaemonFileCredentials;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.LocalDirectorySSLConfig;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.remoting.Callable;
import hudson.slaves.DumbSlave;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;
import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * @author Kanstantsin Shautsou
 */
@Ignore
public class DockerBuildImageStepTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void testBuild() throws Throwable {
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
                3,//      Integer retryWaitTime
                new NonVerifyingKeyVerificationStrategy()
        );
        final DumbSlave dumbSlave = new DumbSlave("docker-daemon", "/home/vagrant/jenkins2", sshLauncher);
        jRule.getInstance().addNode(dumbSlave);
        await().timeout(60, SECONDS).until(() -> assertThat(dumbSlave.getChannel(), notNullValue()));
        String dockerfilePath = dumbSlave.getChannel().call(new StringThrowableCallable());


        final CredentialsYADockerConnector dockerConnector = new CredentialsYADockerConnector()
                .withConnectorType(JERSEY)
                .withServerUrl("tcp://127.0.0.1:2376")
                .withSslConfig(new LocalDirectorySSLConfig("/home/vagrant/keys"))
                ;
//                .withCredentials(new DockerDaemonFileCredentials(null, "docker-cert", "",
//                        "/home/vagrant/keys"));

        DockerBuildImage buildImage = new DockerBuildImage();
        buildImage.setBaseDirectory(dockerfilePath);
        buildImage.setPull(true);
        buildImage.setNoCache(true);

        DockerBuildImageStep dockerBuildImageStep = new DockerBuildImageStep(dockerConnector, buildImage);

        FreeStyleProject project = jRule.createFreeStyleProject("test");
        project.getBuildersList().add(dockerBuildImageStep);

        project.save();

        QueueTaskFuture<FreeStyleBuild> taskFuture = project.scheduleBuild2(0);
        FreeStyleBuild freeStyleBuild = taskFuture.get();
        jRule.waitForCompletion(freeStyleBuild);
        jRule.assertBuildStatusSuccess(freeStyleBuild);
    }

    private static class StringThrowableCallable implements Callable<String, Throwable> {
        private static final long serialVersionUID = 1L;

        @Override
        public void checkRoles(RoleChecker roleChecker) throws SecurityException {
        }

        @Override
        public String call() throws Throwable {
//                File tempFolder = folder.newFolder();
            File tempFolder = new File("/home/vagrant/jenkins2/docker");
            File dockerfile = new File(tempFolder, "Dockerfile");
//            String dockerfileContent = "FROM busybox\nRUN echo hello";
            String dockerfileContent =
                    "FROM maven:3.6.2-jdk-8\n" +
                    "RUN echo hello\n" +
                    "RUN mvn archetype:generate -DgroupId=com.mycompany.app -DartifactId=my-app -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.4 -DinteractiveMode=false\n" +
                    "RUN cd my-app && mvn install\n"
                    ;
            FileUtils.writeStringToFile(dockerfile, dockerfileContent);
            return tempFolder.getAbsolutePath();
        }
    }
}