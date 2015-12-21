package com.github.kostyasha.it.SomeTest

import com.github.kostyasha.yad.DockerCloud
import com.github.kostyasha.yad.DockerConnector
import com.github.kostyasha.yad.DockerSlaveTemplate
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy
import hudson.model.Node
import hudson.slaves.JNLPLauncher
import jenkins.model.Jenkins



final DockerConnector dockerConnector = new DockerConnector("https://192.168.99.100:2376");
final DockerComputerJNLPLauncher launcher = new DockerComputerJNLPLauncher(new JNLPLauncher());
final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate()
        .setLabelString("docker-label")
        .setLauncher(launcher)
        .setMode(Node.Mode.EXCLUSIVE)
        .setRetentionStrategy(new DockerOnceRetentionStrategy(10));
final ArrayList<DockerSlaveTemplate> templates = new ArrayList<>();
templates.add(slaveTemplate);

final DockerCloud dockerCloud = new DockerCloud(
        testVar,
        templates,
        3,
        dockerConnector
);

Jenkins.getActiveInstance().clouds.add(dockerCloud);
