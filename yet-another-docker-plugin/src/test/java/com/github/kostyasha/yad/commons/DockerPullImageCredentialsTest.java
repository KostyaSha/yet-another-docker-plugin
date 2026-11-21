package com.github.kostyasha.yad.commons;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.github.kostyasha.yad.credentials.DockerRegistryAuthCredentials;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.PullImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
import hudson.model.TaskListener;
import jenkins.authentication.tokens.api.AuthenticationTokenSource;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryToken;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DockerPullImageCredentialsTest {
    private static final String REGISTRY = "registry.example.test:5000";
    private static final String IMAGE = REGISTRY + "/agent:latest";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void discoversAndUsesRegistryTokenForMatchingHost() throws Exception {
        registerTokenCredential();
        var options = new DockerRegistryCredential.DescriptorImpl().doFillCredentialsIdItems(j.jenkins);
        assertTrue(options.stream().anyMatch(option -> "ecr:region:aws".equals(option.value)));

        PullImageCmd command = pull(List.of(new DockerRegistryCredential(REGISTRY, "ecr:region:aws")));
        ArgumentCaptor<AuthConfig> config = ArgumentCaptor.forClass(AuthConfig.class);
        verify(command).withAuthConfig(config.capture());
        assertEquals(REGISTRY, config.getValue().getRegistryAddress());
        assertEquals("AWS", config.getValue().getUsername());
        assertEquals("example:password", config.getValue().getPassword());
    }

    @Test
    public void doesNotSendRegistryTokenToAnotherHost() throws Exception {
        registerTokenCredential();
        PullImageCmd command = pull(List.of(new DockerRegistryCredential("other.example.test", "ecr:region:aws")));
        verify(command, never()).withAuthConfig(any());
    }

    @Test
    public void preservesExistingDockerRegistryAuthMappings() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new DockerRegistryAuthCredentials(CredentialsScope.GLOBAL, "legacy", "",
                        "legacy-user", "legacy-password", ""));
        PullImageCmd command = pull(List.of(new DockerRegistryCredential("", "legacy")));
        ArgumentCaptor<AuthConfig> config = ArgumentCaptor.forClass(AuthConfig.class);
        verify(command).withAuthConfig(config.capture());
        assertEquals("legacy-user", config.getValue().getUsername());
        assertEquals("legacy-password", config.getValue().getPassword());
    }

    private void registerTokenCredential() {
        SystemCredentialsProvider.getInstance().getCredentials().add(new TestRegistryCredentials());
    }

    private PullImageCmd pull(List<DockerRegistryCredential> mappings) throws Exception {
        DockerClient client = mock(DockerClient.class);
        PullImageCmd command = mock(PullImageCmd.class);
        when(client.pullImageCmd(IMAGE)).thenReturn(command);
        when(command.exec(any(DockerPullImageListenerLogger.class))).thenAnswer(invocation -> {
            DockerPullImageListenerLogger callback = invocation.getArgument(0);
            callback.onComplete();
            return callback;
        });
        DockerPullImage pull = new DockerPullImage();
        pull.setPullStrategy(DockerImagePullStrategy.PULL_ALWAYS);
        pull.setRegistriesCreds(mappings);
        pull.execInternal(client, IMAGE, TaskListener.NULL);
        return command;
    }

    public static class TestRegistryCredentials extends BaseStandardCredentials {
        TestRegistryCredentials() {
            super(CredentialsScope.GLOBAL, "ecr:region:aws", "Example registry");
        }
    }

    @TestExtension
    public static class TestRegistryTokenSource extends AuthenticationTokenSource<DockerRegistryToken, TestRegistryCredentials> {
        public TestRegistryTokenSource() {
            super(DockerRegistryToken.class, TestRegistryCredentials.class);
        }

        @Override
        public DockerRegistryToken convert(TestRegistryCredentials credentials) {
            String token = Base64.getEncoder().encodeToString("AWS:example:password".getBytes(StandardCharsets.UTF_8));
            return new DockerRegistryToken("", token);
        }
    }
}
