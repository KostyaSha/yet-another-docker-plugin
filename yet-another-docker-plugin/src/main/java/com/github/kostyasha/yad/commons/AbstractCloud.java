package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad.DockerSlaveTemplate;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * (Very) Pure abstraction to clean up docker specific implementation.
 * Normally it should be in {@link hudson.slaves.AbstractCloudImpl}, but it doesn't provide templates
 * and has bad counter type.
 *
 * @author Kanstantsin Shautsou
 */
public abstract class AbstractCloud extends Cloud {
    /**
     * Track the count per image name for images currently being
     * provisioned, but not necessarily reported yet by docker.
     */
    protected final HashMap<DockerSlaveTemplate, Integer> provisionedImages = new HashMap<>();

    @Nonnull
    protected List<DockerSlaveTemplate> templates = new ArrayList<>(0);

    /**
     * Total max allowed number of containers
     */
    protected int containerCap = 50;

    protected AbstractCloud(String name) {
        super(name);
    }

    public int getContainerCap() {
        return containerCap;
    }

    public void setContainerCap(int containerCap) {
        this.containerCap = containerCap;
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    @CheckForNull
    public DockerSlaveTemplate getTemplate(String template) {
        for (DockerSlaveTemplate t : templates) {
            if (t.getDockerContainerLifecycle().getImage().equals(template)) {
                return t;
            }
        }
        return null;
    }

    @CheckForNull
    public DockerSlaveTemplate getTemplateById(String id) {
        for (DockerSlaveTemplate t : templates) {
            if (t.getId().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Gets first {@link DockerSlaveTemplate} that has the matching {@link Label}.
     */
    @CheckForNull
    public DockerSlaveTemplate getTemplate(Label label) {
        List<DockerSlaveTemplate> labelTemplates = getTemplates(label);
        if (!labelTemplates.isEmpty()) {
            return labelTemplates.get(0);
        }

        return null;
    }

    /**
     * Add a new template to the cloud
     */
    public synchronized void addTemplate(DockerSlaveTemplate t) {
        templates.add(t);
    }

    public List<DockerSlaveTemplate> getTemplates() {
        return templates;
    }

    /**
     * Multiple templates may have the same label.
     *
     * @return Templates matched to requested label assuming slave Mode
     */
    @Nonnull
    public List<DockerSlaveTemplate> getTemplates(Label label) {
        List<DockerSlaveTemplate> dockerSlaveTemplates = new ArrayList<>();

        for (DockerSlaveTemplate t : templates) {
            if (isNull(label) && t.getMode() == Node.Mode.NORMAL) {
                dockerSlaveTemplates.add(t);
            }

            if (nonNull(label) && label.matches(t.getLabelSet())) {
                dockerSlaveTemplates.add(t);
            }
        }

        return dockerSlaveTemplates;
    }

    /**
     * Set list of available templates
     */
    public void setTemplates(List<DockerSlaveTemplate> replaceTemplates) {
        if (replaceTemplates != null) {
            templates = new ArrayList<>(replaceTemplates);
        } else {
            templates = Collections.emptyList();
        }
    }

    /**
     * Remove slave template
     */
    public synchronized void removeTemplate(DockerSlaveTemplate t) {
        templates.remove(t);
    }

    /**
     * Decrease the count of slaves being "provisioned".
     */
    protected void decrementAmiSlaveProvision(DockerSlaveTemplate container) {
        synchronized (provisionedImages) {
            int currentProvisioning = 0;
            if (provisionedImages.containsKey(container)) {
                currentProvisioning = provisionedImages.get(container);
            }
            provisionedImages.put(container, Math.max(currentProvisioning - 1, 0));
        }
    }

}
