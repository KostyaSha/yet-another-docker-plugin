package com.github.kostyasha.yad;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.action.DockerLabelAssignmentAction;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad.step.DockerTask;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.model.Node;
import hudson.model.Queue;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static com.github.kostyasha.yad.other.ConnectorType.NETTY;
import static java.util.Collections.emptyList;

/**
 * @author Kanstantsin Shautsou
 */
public class TaskStepTest {
    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void actionTask() throws Exception {
        final Jenkins jenkins = jRule.getInstance();

        final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate();

        slaveTemplate.setLabelString("docker-slave");

//        final DockerCloud dockerCloud = new DockerCloud("localTestCloud");

        final DockerLabelAssignmentAction assignmentAction = new DockerLabelAssignmentAction("docker-slave");
        final DockerConnector dockerConnector = new DockerConnector("tcp://192.168.1.3:2376/");
        dockerConnector.setConnectorType(NETTY);

        assignmentAction.setConnector(dockerConnector);

        final Queue.WaitingItem waitingItem = jenkins.getQueue().schedule(new DockerTask(), 0, assignmentAction);

        jRule.waitUntilNoActivity();
    }
}
