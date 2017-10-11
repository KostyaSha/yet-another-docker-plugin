package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerComputer;
import com.github.kostyasha.yad.DockerSlave;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Frame;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.StreamType;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.AttachContainerResultCallback;
import com.google.common.base.Throwables;
import hudson.Extension;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Objects;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class DockerComputerIOLauncher extends DockerComputerLauncher {

    @DataBoundConstructor
    public DockerComputerIOLauncher() {
        super(null);
    }

    @Override
    public void afterContainerCreate(DockerClient client, String containerId) throws IOException {
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
        final PrintStream llog = listener.getLogger();
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
        InspectContainerResponse inspect = client.inspectContainerCmd(containerId).exec();
        if (nonNull(inspect) && nonNull(inspect.getState().getRunning()) &&
                !inspect.getState().getRunning()) {
            throw new IllegalStateException("Container is not running!");
        }

        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream inputStream = new PipedInputStream(out, 4096);

        IOCallback callback = new IOCallback(listener, out);

        PipedInputStream pipedInputStream = new PipedInputStream(4096);
        PipedOutputStream outputStream = new PipedOutputStream(pipedInputStream);
        llog.println("Attaching to container...");

        ExecCreateCmdResponse cmdResponse = client.execCreateCmd(containerId)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(false)
                .withCmd("/bin/bash", "-c", "java -Dfile.encoding=UTF-8 -jar /tmp/slave.jar")
                .withUser("root")
                .exec();

        client.execStartCmd(cmdResponse.getId())
                .withStdIn(pipedInputStream)
                .exec(callback);

//        client.attachContainerCmd(containerId)
//                .withStdErr(true)
//                .withStdOut(true)
//                .withFollowStream(true)
//                .withStdIn(pipedInputStream)
//                .exec(callback);

        // container stdout is InputStream ... in.read()
        // container stdin is  out.write()
        computer.setChannel(inputStream, outputStream, listener.getLogger(), new Channel.Listener() {
            @Override
            public void onClosed(Channel channel, IOException cause) {
                try {
                    callback.close();
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
            }
        });
    }

    private static final class IOCallback extends AttachContainerResultCallback {
        private TaskListener listener;
        private OutputStream outputStream;

        IOCallback(TaskListener listener, OutputStream outputStream) {
            this.listener = listener;
            this.outputStream = outputStream;
        }

        @Override
        public void onError(Throwable throwable) {
            throwable.printStackTrace(listener.getLogger());
            super.onError(throwable);
        }

        @Override
        public void onNext(Frame item) {
            if (item.getStreamType() == StreamType.STDERR) {
                listener.error(new String(item.getPayload(), Charset.forName("UTF-8")));
            } else if (item.getStreamType() == StreamType.STDOUT) {
                try {
                    outputStream.write(item.getPayload());
                } catch (IOException e) {
                    listener.error("Can't write container stdout to channel");
                    Throwables.propagate(e);
                }
            }
            super.onNext(item);
        }
    }


    @Override
    public DockerComputerLauncher getPreparedLauncher(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                                                      InspectContainerResponse ir) {
        return new DockerComputerIOLauncher();
    }

    @Override
    public void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate,
                                      CreateContainerCmd createContainerCmd) throws IOException {
//        createContainerCmd.withEntrypoint("/bin/bash", "-c", "java -jar /tmp/slave.jar");
        createContainerCmd.withEntrypoint("sleep");
        createContainerCmd.withCmd("infinity");
        createContainerCmd.withTty(false);
//        createContainerCmd.withLogConfig(new LogConfig(NONE));
        createContainerCmd.withStdinOpen(true);
    }


    @Override
    public ComputerLauncher getLauncher() {
        return this;
    }

    @Extension
    public static final class DescriptorImpl extends DelegatingComputerLauncher.DescriptorImpl {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "[Experimental] Docker IO computer launcher";
        }
    }
}
