package com.github.kostyasha.yad;

import com.github.kostyasha.yad_docker_java.com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DockerAgentBuildActionTest {
    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void retainsFreestyleMetadataAfterAgentRemovalAndBuildReload() throws Exception {
        DockerSlave node = addAgent("worker-a", "container-a", """
                {"Node":{"Name":"docker-worker","ID":"worker-id"}}
                """);
        FreeStyleProject job = j.createFreeStyleProject();
        job.setAssignedNode(node);
        FreeStyleBuild build = j.buildAndAssertSuccess(job);
        j.jenkins.removeNode(node);
        job._getRuns().purgeCache();
        build = job.getBuildByNumber(build.number);

        DockerAgentBuildAction action = build.getAction(DockerAgentBuildAction.class);
        assertNotNull(action);
        assertEquals(1, action.getAgents().size());
        DockerAgentBuildAction.Agent agent = action.getAgents().get(0);
        assertEquals("worker-a", agent.getName());
        assertEquals("cloud", agent.getCloud());
        assertEquals("container-a", agent.getContainerId());
        assertEquals("agent-image:latest", agent.getImage());
        assertEquals("tcp://manager.example:2376", agent.getDockerEndpoint());
        assertEquals("docker-worker", agent.getWorkerName());
        assertEquals("worker-id", agent.getWorkerId());
        try (JenkinsRule.WebClient client = j.createWebClient()) {
            String page = client.goTo(build.getUrl() + "docker-agents/").asNormalizedText();
            assertTrue(page.contains("docker-worker"));
            assertFalse(page.contains("api-secret"));
        }
    }

    @Test
    public void recordsEveryPipelineAgentOnce() throws Exception {
        addAgent("worker-a", "container-a", "{}");
        addAgent("worker-b", "container-b", "{}");
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("""
                parallel a: { node('worker-a') {} }, b: { node('worker-b') {} }
                node('worker-a') {}
                """, true));
        WorkflowRun build = j.buildAndAssertSuccess(job);

        DockerAgentBuildAction action = build.getAction(DockerAgentBuildAction.class);
        assertNotNull(action);
        assertEquals(2, action.getAgents().size());
        assertEquals(Set.of("container-a", "container-b"), action.getAgents().stream()
                .map(DockerAgentBuildAction.Agent::getContainerId).collect(Collectors.toSet()));
    }

    private DockerSlave addAgent(String name, String containerId, String inspection) throws Exception {
        DockerSlaveTemplate template = new DockerSlaveTemplate();
        template.setRemoteFs(tmp.newFolder(name).getAbsolutePath());
        template.setRetentionStrategy(RetentionStrategy.INSTANCE);
        DockerContainerLifecycle lifecycle = new DockerContainerLifecycle();
        lifecycle.setImage("agent-image:latest");
        template.setDockerContainerLifecycle(lifecycle);
        DockerSlave node = new DockerSlave(name, "", j.createComputerLauncher(null), containerId,
                template, "cloud", null);
        node.captureBuildMetadata("tcp://user:api-secret@manager.example:2376?token=api-secret",
                new ObjectMapper().readValue(inspection, InspectContainerResponse.class));
        j.jenkins.addNode(node);
        j.waitOnline(node);
        return node;
    }
}
