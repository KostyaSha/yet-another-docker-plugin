package com.github.kostyasha.yad.launcher;


import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerSSHConnector;
import hudson.plugins.sshslaves.verifiers.KnownHostsFileKeyVerificationStrategy;
import hudson.slaves.Cloud;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

public class DockerComputerSSHLauncherTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

//    @WithPlugin("ssh-slaves-1.30.0")
    @LocalData
    @Test
    public void migration() {
        Cloud cl = jenkinsRule.getInstance().getCloud("cloudName");
        assertThat(cl, notNullValue());

        DockerCloud cloud = (DockerCloud) cl;
        assertThat(cloud, notNullValue());

        DockerSlaveTemplate template = cloud.getTemplateById("fb5f32a8-bfdd-4f00-8df8-fcf3cf14ab63");
        assertThat(template, notNullValue());

        DockerComputerLauncher l = template.getLauncher();
        assertThat(l, instanceOf(DockerComputerSSHLauncher.class));
        DockerComputerSSHLauncher sshLauncher = (DockerComputerSSHLauncher) l;

        DockerSSHConnector dockerSSHConnector = sshLauncher.getSshConnector();
        assertThat(dockerSSHConnector, notNullValue());

        assertThat(dockerSSHConnector.getSshHostKeyVerificationStrategy(), notNullValue());
        assertThat(dockerSSHConnector.getSshHostKeyVerificationStrategy(), instanceOf(KnownHostsFileKeyVerificationStrategy.class));
        assertThat(dockerSSHConnector.getSuffixStartSlaveCmd(), is("suffixStartSlave"));
        assertThat(dockerSSHConnector.getPrefixStartSlaveCmd(), is("prefixStartSlave"));


    }

}