package com.github.kostyasha.yad.launcher;

import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

public class DockerComputerJNLPLauncherTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void slaveAgentJnlpIncludesConfiguredTunnel() throws Exception {
        DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher();
        launcher.setTunnel("agents.example.test:31067");

        assertThat(slaveAgentJnlp("with-tunnel", launcher),
                containsString("<argument>-tunnel</argument><argument>agents.example.test:31067</argument>"));
        assertThat(slaveAgentJnlp("without-tunnel", new DockerComputerJNLPLauncher()),
                not(containsString("<argument>-tunnel</argument>")));
    }

    private String slaveAgentJnlp(String name, DockerComputerJNLPLauncher launcher) throws Exception {
        DockerComputerLauncher prepared = launcher.getPreparedLauncher(null, null, null);
        jenkins.jenkins.addNode(new DumbSlave(name, "/tmp/agent", prepared));

        try (JenkinsRule.WebClient client = jenkins.createWebClient()) {
            return client.goTo("computer/" + name + "/slave-agent.jnlp", "application/x-java-jnlp-file")
                    .getWebResponse().getContentAsString().replaceAll(">\\s+<", "><");
        }
    }
}
