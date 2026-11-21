package com.github.kostyasha.yad;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.model.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Docker agents used by a build, retained after the agents are removed. */
public final class DockerAgentBuildAction implements Action {
    private static final Logger LOG = LoggerFactory.getLogger(DockerAgentBuildAction.class);
    private volatile List<Agent> agents = new ArrayList<>();

    @Override
    public String getIconFileName() {
        return "symbol-computer";
    }

    @Override
    public String getDisplayName() {
        return "Docker agents";
    }

    @Override
    public String getUrlName() {
        return "docker-agents";
    }

    public List<Agent> getAgents() {
        return List.copyOf(agents);
    }

    private static void record(Run<?, ?> run, Agent agent) throws IOException {
        synchronized (run) {
            DockerAgentBuildAction action = run.getAction(DockerAgentBuildAction.class);
            if (action == null) {
                action = new DockerAgentBuildAction();
                action.agents.add(agent);
                run.addAction(action);
            } else {
                if (action.agents.stream().anyMatch(existing -> existing.containerId.equals(agent.containerId)
                        && Objects.equals(existing.cloud, agent.cloud))) {
                    return;
                }
                List<Agent> updated = new ArrayList<>(action.agents);
                updated.add(agent);
                action.agents = updated;
            }
            run.save();
        }
    }

    public static final class Agent implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final String cloud;
        private final String containerId;
        private final String image;
        private final String dockerEndpoint;
        private final String workerName;
        private final String workerId;

        Agent(String name, String cloud, String containerId, String image, String dockerEndpoint,
              String workerName, String workerId) {
            this.name = name;
            this.cloud = cloud;
            this.containerId = containerId;
            this.image = image;
            this.dockerEndpoint = endpointForDisplay(dockerEndpoint);
            this.workerName = workerName;
            this.workerId = workerId;
        }

        private static String endpointForDisplay(String endpoint) {
            if (endpoint == null) {
                return null;
            }
            try {
                URI uri = URI.create(endpoint);
                if (!uri.isAbsolute() || uri.isOpaque()) {
                    return null;
                }
                String authority = Objects.toString(uri.getRawAuthority(), "");
                return uri.getScheme() + "://" + authority.substring(authority.lastIndexOf('@') + 1)
                        + Objects.toString(uri.getRawPath(), "");
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public String getName() {
            return name;
        }

        public String getCloud() {
            return cloud;
        }

        public String getContainerId() {
            return containerId;
        }

        public String getImage() {
            return image;
        }

        public String getDockerEndpoint() {
            return dockerEndpoint;
        }

        public String getWorkerName() {
            return workerName;
        }

        public String getWorkerId() {
            return workerId;
        }
    }

    @Extension
    public static final class Listener implements ExecutorListener {
        @Override
        public void taskStarted(Executor executor, Queue.Task task) {
            if (!(executor.getOwner().getNode() instanceof DockerSlave node) || node.getContainerId() == null) {
                return;
            }
            Queue.Executable executable = executor.getCurrentExecutable();
            if (!(executable instanceof Run)) {
                executable = task.getOwnerExecutable();
            }
            if (executable instanceof Run<?, ?> run) {
                try {
                    record(run, node.getBuildMetadata());
                } catch (IOException e) {
                    LOG.warn("Could not save Docker agent metadata for {}", run.getExternalizableId(), e);
                }
            }
        }
    }
}
