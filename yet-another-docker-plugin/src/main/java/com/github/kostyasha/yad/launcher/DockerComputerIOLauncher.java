package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerComputer;
import com.github.kostyasha.yad.DockerSlave;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.AttachContainerResultCallback;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import static java.util.Objects.isNull;

public class DockerComputerIOLauncher extends DockerComputerLauncher {

    @DataBoundConstructor
    public DockerComputerIOLauncher() {
        super(null);
    }

    @Override
    public void afterCreate(DockerClient client, String containerId) throws IOException {
        // upload archive
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(byteArrayOutputStream)) {

            // @see hudson.model.Slave.JnlpJar.getURL()
//            byte[] slavejar = IOUtils.toByteArray(Jenkins.getInstance().servletContext.getResourceAsStream("/WEB-INF/slave.jar"));
//            if (isNull(null)) {
//                // during the development this path doesn't have the files.
//                slavejar = Files.readAllBytes(Paths.get("./target/jenkins/WEB-INF/slave.jar"));
//            }
            byte[] slaveJar = new Slave.JnlpJar("slave.jar").readFully();

            TarArchiveEntry entry = new TarArchiveEntry("slave.jar");
            entry.setSize(slaveJar.length);
            entry.setMode(0664);
            tarOut.putArchiveEntry(entry);
            tarOut.write(slaveJar);
            tarOut.closeArchiveEntry();


            tarOut.close();
            try (InputStream is = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {
                client.copyArchiveToContainerCmd(containerId)
                        .withTarInputStream(is)
                        .withRemotePath("/tmp")
                        .exec();
            }
        }
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener)
            throws IOException, InterruptedException {
        final PrintStream logger = listener.getLogger();
        DockerComputer dockerComputer;
        if (computer instanceof DockerComputer) {
            dockerComputer = (DockerComputer) computer;
        } else {
            listener.error("Docker JNLP Launcher accepts only DockerComputer.class");
            throw new IllegalArgumentException("Docker JNLP Launcher accepts only DockerComputer.class");
        }
        Objects.requireNonNull(dockerComputer);

        final String containerId = dockerComputer.getContainerId();
        final DockerCloud dockerCloud = dockerComputer.getCloud();
//        Objects.requireNonNull(dockerCloud, "Cloud not found for computer " + computer.getName());
        if (isNull(dockerCloud)) {
            listener.error("Cloud not found for computer " + computer.getName());
            throw new NullPointerException("Cloud not found for computer " + computer.getName());
        }
        final DockerClient client = dockerCloud.getClient();
        final DockerSlave node = dockerComputer.getNode();
        if (isNull(node)) {
            throw new NullPointerException("Node can't be null");
        }
        final DockerSlaveTemplate dockerSlaveTemplate = node.getDockerSlaveTemplate();
        final DockerComputerJNLPLauncher launcher = (DockerComputerJNLPLauncher) dockerSlaveTemplate.getLauncher();

        final String rootUrl = launcher.getJenkinsUrl(Jenkins.getInstance().getRootUrl());
//        Objects.requireNonNull(rootUrl, "Jenkins root url is not specified!");
        if (isNull(rootUrl)) {
            throw new NullPointerException("Jenkins root url is not specified!");
        }



        client.attachContainerCmd(containerId)
                .withStdIn()
                .withFollowStream()
                .exec()

    }

    @Override
    public DockerComputerLauncher getPreparedLauncher(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                                                      InspectContainerResponse ir) {
        return new DockerComputerIOLauncher();
    }

    @Override
    public void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate,
                                      CreateContainerCmd createContainerCmd) throws IOException {
        createContainerCmd.withEntrypoint("sh -c \"java -jar /tmp/slave-jar\"");
        createContainerCmd.withCmd("");
        createContainerCmd.withTty(true);
        createContainerCmd.withStdinOpen(true);
    }
}
