//package com.github.kostyasha.yad.steps;
//
//import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
//import com.github.kostyasha.yad.connector.YADockerConnector;
//import com.github.kostyasha.yad.utils.VariableUtils;
//import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
//import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.BuildImageCmd;
//import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.PushImageCmd;
//import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
//import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfigurations;
//import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.NameParser;
//import com.google.common.base.Throwables;
//import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
//import hudson.AbortException;
//import hudson.EnvVars;
//import hudson.model.Run;
//import hudson.model.TaskListener;
//import hudson.remoting.VirtualChannel;
//import jenkins.MasterToSlaveFileCallable;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import javax.annotation.Nonnull;
//import java.io.File;
//import java.io.IOException;
//import java.io.PrintStream;
//import java.util.ArrayList;
//import java.util.List;
//
//import static com.github.kostyasha.yad.utils.DockerJavaUtils.getAuthConfig;
//import static com.github.kostyasha.yad.utils.VariableUtils.resolveVar;
//import static java.util.Objects.isNull;
//import static java.util.Objects.nonNull;
//import static org.apache.commons.lang.StringUtils.isEmpty;
//
///**
// * @author Kanstantsin Shautsou
// */
//public class DockerShellStepFileCallable extends MasterToSlaveFileCallable<Boolean> {
//
//    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImageStepFileCallable.class);
//    private static final long serialVersionUID = 1L;
//
//    private final YADockerConnector connector;
//
//    private final TaskListener taskListener;
//
//    private final String shellScript;
//    private final String dockerImage;
//
//    public DockerShellStepFileCallable(YADockerConnector connector,
//                                       TaskListener taskListener,
//                                       String shellScript,
//                                       String dockerImage) {
//        this.connector = connector;
//        this.taskListener = taskListener;
//        this.shellScript = shellScript;
//        this.dockerImage = dockerImage;
//    }
//
//    public static Builder newDockerShellStepFileCallableBuilder() {
//        return new Builder();
//    }
//
//    @Override
//    public Boolean invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
//        PrintStream llog = taskListener.getLogger();
//        llog.println("Creating connection to docker daemon...");
//        try (DockerClient client = connector.getClient()) {
//            invoke(client);
//        } catch (Exception ex) {
//            Throwables.propagate(ex);
//            return false;
//        }
//
//        return true;
//    }
//
//    /**
//     * less indents
//     */
//    private void invoke(DockerClient client) throws AbortException {
//        PrintStream llog = taskListener.getLogger();
//        // id for container that we run for shell execution
//        String containerId = null;
//
//        try {
//
//        } finally {
//            invokeCleanup(client, containerId);
//        }
//    }
//
//    private void invokeCleanup(DockerClient client, String containerId) {
//
//    }
//
//    @SuppressFBWarnings(value = "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
//    public static class Builder {
//        private YADockerConnector connector;
//        private String shellScript;
//
//        private TaskListener taskListener;
//        private Run run;
//        private String dockerImage;
//
//        public Builder() {
//        }
//
//        public Builder withConnector(@Nonnull YADockerConnector connector) {
//            this.connector = connector;
//            return this;
//        }
//
//        public Builder withTaskListener(@Nonnull TaskListener taskListener) {
//            this.taskListener = taskListener;
//            return this;
//        }
//
//        public Builder withRun(@Nonnull Run run) {
//            this.run = run;
//            return this;
//        }
//
//        public Builder withShellScript(String shellScript) {
//            this.shellScript = shellScript;
//            return this;
//        }
//
//        public Builder withDockerImage(String dockerImage) {
//            this.dockerImage = dockerImage;
//            return this;
//        }
//
//        public DockerShellStepFileCallable build() throws IOException, InterruptedException {
//            if (isNull(run) || isNull(taskListener) || isNull(connector) || isNull(shellScript)) {
//                throw new IllegalStateException("Specify shellscript!");
//            }
////            // if something should be resolved on master side do it here
////            final ArrayList<String> expandedTags = new ArrayList<>(tags.size());
////            for (String tag : tags) {
////                expandedTags.add(resolveVar(tag, run, taskListener));
////            }
//
//            return new DockerShellStepFileCallable(
//                    connector,
//                    taskListener,
//                    shellScript,
//                    dockerImage
//            );
//        }
//
//    }
//
//}
