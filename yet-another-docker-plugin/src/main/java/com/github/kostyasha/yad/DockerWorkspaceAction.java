package com.github.kostyasha.yad;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.WorkspaceListener;
import hudson.slaves.WorkspaceList;
import jenkins.model.RunAction2;
import jenkins.util.VirtualFile;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/** Browses the shared volume for one recorded build workspace. */
public final class DockerWorkspaceAction implements RunAction2 {
    private final String root;
    private final String relativePath;
    private transient Run<?, ?> build;

    DockerWorkspaceAction(String root, String relativePath) {
        this.root = root;
        this.relativePath = relativePath;
    }

    @Override
    public void onAttached(Run<?, ?> build) {
        this.build = build;
    }

    @Override
    public void onLoad(Run<?, ?> build) {
        this.build = build;
    }

    @Override
    public String getIconFileName() {
        return build.getParent().hasPermission(Item.WORKSPACE) ? "symbol-folder" : null;
    }

    @Override
    public String getDisplayName() {
        return "Docker workspace";
    }

    @Override
    public String getUrlName() {
        return "docker-workspace";
    }

    public HttpResponse doIndex() {
        return doDynamic();
    }

    public HttpResponse doDynamic() {
        build.getParent().checkPermission(Item.WORKSPACE);
        try {
            Path directory = Path.of(root);
            if (directory.isAbsolute()) {
                Path base = directory.toRealPath();
                Path candidate = base.resolve(relativePath).normalize();
                if (candidate.startsWith(base) && !candidate.equals(base)
                        && candidate.equals(candidate.toRealPath()) && Files.isDirectory(candidate)) {
                    return new DirectoryBrowserSupport(build, VirtualFile.forFile(base.toFile()).child(relativePath),
                            build.getFullDisplayName() + " workspace", "folder.png", true);
                }
            }
        } catch (IOException | InvalidPathException ignored) {
            // The shared directory may have been unmounted or cleaned up.
        }
        return HttpResponses.notFound();
    }

    @Extension
    public static final class Listener extends WorkspaceListener {
        @Override
        public void beforeUse(AbstractBuild build, FilePath workspace, BuildListener listener) {
            if (!(build.getBuiltOn() instanceof DockerSlave agent) || !(build.getProject() instanceof TopLevelItem job)) {
                return;
            }
            String mapping = agent.getDockerSlaveTemplate().getRemoteFsMapping();
            if (mapping == null) {
                return;
            }
            FilePath standard = agent.getWorkspaceFor(job);
            // A job-configured custom workspace must not grant access to another job on the controller.
            if (!isJobWorkspace(standard, workspace)) {
                return;
            }
            FilePath root = agent.getRootPath();
            Deque<String> parts = new ArrayDeque<>();
            FilePath current = workspace;
            while (current != null && !current.equals(root)) {
                String name = current.getName();
                if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("\\") || name.contains(":")) {
                    return;
                }
                parts.addFirst(name);
                current = current.getParent();
            }
            if (current != null && !parts.isEmpty()) {
                build.addAction(new DockerWorkspaceAction(mapping, String.join("/", parts)));
            }
        }

        private static boolean isJobWorkspace(FilePath standard, FilePath workspace) {
            if (standard == null || standard.getParent() == null) {
                return false;
            }
            String concurrentName = Pattern.quote(standard.getName() + WorkspaceList.COMBINATOR) + "[0-9]+";
            return standard.equals(workspace) || (standard.getParent().equals(workspace.getParent())
                    && workspace.getName().matches(concurrentName));
        }
    }
}
