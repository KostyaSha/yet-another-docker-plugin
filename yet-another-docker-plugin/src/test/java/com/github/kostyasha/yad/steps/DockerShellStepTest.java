package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.steps.DockerShellStep.ScriptPlatform;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class DockerShellStepTest {
    private final String defaultExecutor = new DockerShellStep().getExecutorScript();

    @Test
    public void windowsRunsBatchFilesAsTheConfiguredUser() throws IOException {
        ScriptPlatform platform = ScriptPlatform.forImage("windows");
        CreateContainerCmd cmd = mock(CreateContainerCmd.class);
        platform.configureExecutor(cmd);
        verify(cmd).withEntrypoint("cmd.exe", "/D", "/C", "C:\\yad-shell\\executor.cmd");
        verify(cmd, never()).withUser(anyString());
        assertEquals("C:\\", platform.directory);
        assertEquals(Map.of("yad-shell/script.cmd", "echo café\r\nexit /b 7\r\n",
                        "yad-shell/executor.cmd", "@call \"%~dp0script.cmd\"\r\n@exit /b %ERRORLEVEL%"),
                contents(platform, "echo café\nexit /b 7\r\n", defaultExecutor));
    }

    @Test
    public void windowsPreservesCustomAndAbsentExecutors() throws IOException {
        ScriptPlatform platform = ScriptPlatform.forImage("Windows");
        assertEquals(Map.of("yad-shell/executor.cmd", "exit /b 3"), contents(platform, null, "exit /b 3"));
        assertEquals(Map.of("yad-shell/script.cmd", "echo hello"), contents(platform, "echo hello", null));
    }

    @Test
    public void linuxKeepsItsShellAndUploadPaths() throws IOException {
        ScriptPlatform platform = ScriptPlatform.forImage("linux");
        CreateContainerCmd cmd = mock(CreateContainerCmd.class);
        platform.configureExecutor(cmd);
        verify(cmd).withUser("root");
        verify(cmd).withEntrypoint("/bin/sh", "/tmp/executor.sh");
        assertEquals("/tmp", platform.directory);
        assertEquals(Map.of("script.sh", "echo café\n", "executor.sh", "exec /tmp/script.sh"),
                contents(platform, "echo café\n", defaultExecutor));
    }

    private Map<String, String> contents(ScriptPlatform platform, String script, String executor) throws IOException {
        Map<String, String> files = new HashMap<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(platform.archive(script, executor))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                files.put(entry.getName(), new String(tar.readAllBytes(), UTF_8));
            }
        }
        return files;
    }
}
