package com.github.kostyasha.yad;

import jenkins.model.Jenkins;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static com.github.kostyasha.yad.other.ConnectorType.NETTY;

/**
 * @author Kanstantsin Shautsou
 */
public class TaskStepTest {
    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Ignore
    @Test
    public void actionTask() throws Exception {
        final Jenkins jenkins = jRule.getInstance();

        final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate();

        slaveTemplate.setLabelString("docker-slave");

//        final DockerCloud dockerCloud = new DockerCloud("localTestCloud");

//        final DockerLabelAssignmentAction assignmentAction = new DockerLabelAssignmentAction("docker-slave");
        final DockerConnector dockerConnector = new DockerConnector("tcp://192.168.1.3:2376/");
        dockerConnector.setConnectorType(NETTY);

//        assignmentAction.setConnector(dockerConnector);

//        final Queue.WaitingItem waitingItem = jenkins.getQueue().schedule(new DockerTask(), 0, assignmentAction);

        jRule.waitUntilNoActivity();
    }
}
