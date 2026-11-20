package com.github.kostyasha.yad;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import jenkins.model.Jenkins;
import hudson.slaves.RetentionStrategy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DockerWorkspaceActionTest {
    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void browsesRecordedWorkspaceAfterAgentRemovalAndBuildReload() throws Exception {
        Path agentRoot = tmp.newFolder("agent").toPath();
        Path controllerRoot = tmp.newFolder("share").toPath();
        DockerSlaveTemplate template = new DockerSlaveTemplate();
        template.setRemoteFs(agentRoot.toString());
        template.setRemoteFsMapping(controllerRoot.toString());
        template.setRetentionStrategy(RetentionStrategy.INSTANCE);
        DockerSlave node = new DockerSlave("docker-agent", "", j.createComputerLauncher(null),
                null, template, "cloud", null);
        j.jenkins.addNode(node);
        j.waitOnline(node);
        FreeStyleProject job = j.createFreeStyleProject();
        job.setAssignedNode(node);
        FreeStyleBuild build = j.buildAndAssertSuccess(job);
        assertNotNull(build.getAction(DockerWorkspaceAction.class));
        Path relative = agentRoot.relativize(Path.of(build.getWorkspace().getRemote()));
        Path mapped = Files.createDirectories(controllerRoot.resolve(relative));
        Files.writeString(mapped.resolve("result.txt"), "shared volume content");

        // A custom workspace belonging to another job must not get a controller-side link.
        job.setCustomWorkspace(agentRoot.resolve("workspace/other-job").toString());
        assertNull(j.buildAndAssertSuccess(job).getAction(DockerWorkspaceAction.class));

        String url = build.getUrl() + "docker-workspace/";
        j.jenkins.removeNode(node);
        job._getRuns().purgeCache();
        try (JenkinsRule.WebClient client = j.createWebClient()) {
            assertTrue(client.goTo(url).asNormalizedText().contains("result.txt"));
            assertEquals("shared volume content", client.goTo(url + "result.txt", "text/plain")
                    .getWebResponse().getContentAsString());
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                    .grant(Jenkins.READ, Item.READ).everywhere().toEveryone()
                    .grant(Item.WORKSPACE).onItems(job).to("reader"));
            client.login("viewer");
            client.assertFails(url + "result.txt", 403);
            client.login("reader");
            Files.delete(mapped.resolve("result.txt"));
            Files.delete(mapped);
            client.assertFails(url, 404);
            if (!Functions.isWindows()) {
                Files.createSymbolicLink(mapped, agentRoot);
                client.assertFails(url, 404);
            }
        }
    }
}
