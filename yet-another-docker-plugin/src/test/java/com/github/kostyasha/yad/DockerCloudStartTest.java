package com.github.kostyasha.yad;

import com.github.kostyasha.yad.launcher.DockerComputerLauncher;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.async.ResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.LogContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.StartContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Frame;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.HostConfig;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DockerCloudStartTest {
    private static final String CONTAINER_ID = "agent-container";
    private static final String IMAGE = "jenkins-agent:latest";

    private final DockerConnector connector = mock(DockerConnector.class);
    private final DockerClient client = mock(DockerClient.class);
    private final DockerSlaveTemplate template = mock(DockerSlaveTemplate.class);
    private final DockerComputerLauncher launcher = mock(DockerComputerLauncher.class);
    private final StartContainerCmd start = mock(StartContainerCmd.class);
    private final LogContainerCmd logs = mock(LogContainerCmd.class, RETURNS_SELF);
    private final RemoveContainerCmd remove = mock(RemoveContainerCmd.class, RETURNS_SELF);
    private final DockerCloud cloud = new DockerCloud("docker-cloud", List.of(), 1, connector);

    @Before
    public void setUp() {
        DockerContainerLifecycle lifecycle = new DockerContainerLifecycle();
        lifecycle.setImage(IMAGE);
        when(template.getDockerContainerLifecycle()).thenReturn(lifecycle);
        when(template.getLauncher()).thenReturn(launcher);

        CreateContainerResponse response = mock(CreateContainerResponse.class);
        when(response.getId()).thenReturn(CONTAINER_ID);
        CreateContainerCmd create = mock(CreateContainerCmd.class);
        when(create.getHostConfig()).thenReturn(HostConfig.newHostConfig());
        when(create.exec()).thenReturn(response);
        when(connector.getClient()).thenReturn(client);
        when(client.createContainerCmd(IMAGE)).thenReturn(create);
        when(client.startContainerCmd(CONTAINER_ID)).thenReturn(start);
        when(client.logContainerCmd(CONTAINER_ID)).thenReturn(logs);
        when(client.removeContainerCmd(CONTAINER_ID)).thenReturn(remove);
    }

    @Test
    public void successfulStartKeepsContainer() throws Exception {
        assertEquals(CONTAINER_ID, cloud.runContainer(template));

        verify(start).exec();
        verify(client, never()).removeContainerCmd(CONTAINER_ID);
        verify(client, never()).logContainerCmd(CONTAINER_ID);
    }

    @Test(timeout = 15_000)
    public void logTimeoutClosesStreamAndRemovesContainer() throws Exception {
        IllegalStateException failure = new IllegalStateException("start failed");
        doThrow(failure).when(start).exec();
        Closeable stream = mock(Closeable.class);
        when(logs.exec(any())).thenAnswer(invocation -> {
            ResultCallback<Frame> callback = invocation.getArgument(0);
            callback.onStart(stream);
            return callback;
        });

        assertSame(failure, assertThrows(IllegalStateException.class, () -> cloud.runContainer(template)));

        verify(stream).close();
        verify(remove).exec();
    }

    @Test
    public void launcherSetupFailureRemovesContainerBeforeStart() throws Exception {
        IOException failure = new IOException("launcher setup failed");
        doThrow(failure).when(launcher).afterContainerCreate(client, CONTAINER_ID);
        when(logs.exec(any())).thenAnswer(invocation -> {
            ResultCallback<Frame> callback = invocation.getArgument(0);
            callback.onComplete();
            return callback;
        });

        assertSame(failure, assertThrows(IOException.class, () -> cloud.runContainer(template)));

        verify(client, never()).startContainerCmd(CONTAINER_ID);
        verify(remove).exec();
    }

    @Test
    public void diagnosticAndCleanupErrorsPreserveStartFailure() {
        IllegalStateException failure = new IllegalStateException("start failed");
        IllegalStateException logFailure = new IllegalStateException("logs unavailable");
        IllegalStateException removeFailure = new IllegalStateException("removal failed");
        doThrow(failure).when(start).exec();
        when(logs.exec(any())).thenThrow(logFailure);
        doThrow(removeFailure).when(remove).exec();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> cloud.runContainer(template)));

        verify(remove).exec();
        assertArrayEquals(new Throwable[]{logFailure, removeFailure}, failure.getSuppressed());
    }
}
