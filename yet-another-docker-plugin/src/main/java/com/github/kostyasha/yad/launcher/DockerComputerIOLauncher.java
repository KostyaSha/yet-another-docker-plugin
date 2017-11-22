package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerComputer;
import com.github.kostyasha.yad.DockerSlave;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.OsType;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Frame;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.StreamType;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.AttachContainerResultCallback;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils;
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
import org.kohsuke.stapler.DataBoundSetter;

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

import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils.trimToEmpty;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class DockerComputerIOLauncher extends DockerComputerLauncher {
    protected String javaPath = "";

    protected String jvmOpts = "-Dfile.encoding=UTF-8";

    private static final String SLAVE_JAR = "/tmp/slave.jar";

    @DataBoundConstructor
    public DockerComputerIOLauncher() {
        super(null);
    }

    @DataBoundSetter
    public void setJavaPath(String javaPath) {
        this.javaPath = trimToEmpty(javaPath);
    }

    @Nonnull
    public String getJavaPath() {
        return trimToEmpty(javaPath);
    }

    @DataBoundSetter
    public void setJvmOpts(String jvmOpts) {
        this.jvmOpts = trimToEmpty(jvmOpts);
    }

    @Nonnull
    public String getJvmOpts() {
        return trimToEmpty(jvmOpts);
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

            TarArchiveEntry entry = new TarArchiveEntry(SLAVE_JAR);
            entry.setSize(slaveJar.length);
            entry.setMode(0664);
            tarOut.putArchiveEntry(entry);
            tarOut.write(slaveJar);
            tarOut.closeArchiveEntry();
            tarOut.close();

            try (InputStream is = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {
                client.copyArchiveToContainerCmd(containerId)
                        .withTarInputStream(is)
                        .withRemotePath("/")
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

        String java = getJavaPath();

        if (StringUtils.isEmpty(java)) {
            java = "java";
        }

        OsType osType = node.getDockerSlaveTemplate().getOsType();

        ExecCreateCmdResponse cmdResponse = client.execCreateCmd(containerId)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(false)
                .withCmd(
                        osType == OsType.WINDOWS ? "cmd" : "/bin/sh",
                        osType == OsType.WINDOWS ? "/c" : "-c",
                        java + " " + getJvmOpts() + " -jar " + SLAVE_JAR)
                .exec();

        client.execStartCmd(cmdResponse.getId())
                .withStdIn(pipedInputStream)
                .exec(callback);

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
                    outputStream.flush();
                } catch (IOException e) {
                    listener.error("Can't write container stdout to channel");
                    Throwables.propagate(e);
                }
            }
            super.onNext(item);
        }
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
    }

    @Override
    public DockerComputerLauncher getPreparedLauncher(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                                                      InspectContainerResponse ir) {
        final DockerComputerIOLauncher launcher = new DockerComputerIOLauncher();
        launcher.setJavaPath(getJavaPath());
        launcher.setJvmOpts(getJvmOpts());
        return launcher;
    }

    @Override
    public void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate,
                                      CreateContainerCmd createContainerCmd) throws IOException {
        if (createContainerCmd.getCmd() == null || createContainerCmd.getCmd().length == 0) {
            createContainerCmd.withCmd("cat");
        }
        createContainerCmd.withTty(false);
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
