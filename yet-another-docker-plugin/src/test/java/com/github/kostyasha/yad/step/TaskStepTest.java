package com.github.kostyasha.yad.step;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.action.DockerLabelAssignmentAction;
import hudson.model.Queue;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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

        slaveTemplate.setLabelString();

//        final DockerCloud dockerCloud = new DockerCloud("localTestCloud");

        new DockerLabelAssignmentAction()
        final Queue.WaitingItem waitingItem = jenkins.getQueue().schedule(new DockerTask(), 0, );
        jRule.waitUntilNoActivity();
    }
}
