package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerBuildImageStepTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void testBuild() throws Exception {
        File tempFolder = folder.newFolder();
        File dockerfile = new File(tempFolder, "Dockerfile");
        String dockerfileContent = "FROM busybox\nRUN echo hello";
        FileUtils.writeStringToFile(dockerfile, dockerfileContent);


        final DockerConnector dockerConnector = new DockerConnector("tcp://localhost:2376/");
        dockerConnector.setConnectorType(JERSEY);

        DockerBuildImage buildImage = new DockerBuildImage();
        buildImage.setBaseDirectory(tempFolder.getAbsolutePath());
        buildImage.setPull(true);

        DockerBuildImageStep dockerBuildImageStep = new DockerBuildImageStep(dockerConnector, buildImage);

        FreeStyleProject project = jRule.createFreeStyleProject("test");
        project.getBuildersList().add(dockerBuildImageStep);

        project.save();

        QueueTaskFuture<FreeStyleBuild> taskFuture = project.scheduleBuild2(0);
        FreeStyleBuild freeStyleBuild = taskFuture.get();
        jRule.waitForCompletion(freeStyleBuild);
        jRule.assertBuildStatusSuccess(freeStyleBuild);
    }

}