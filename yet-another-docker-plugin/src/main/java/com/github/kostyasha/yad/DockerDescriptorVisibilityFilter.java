package com.github.kostyasha.yad;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.kostyasha.yad.utils.DockerFunctions.getDockerComputerLauncherDescriptors;
import static com.github.kostyasha.yad.utils.DockerFunctions.getDockerRetentionStrategyDescriptors;

/**
 * Since 2.12 will not show Docker related strategies for DumbSlave and any other plugin.
 *
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerDescriptorVisibilityFilter extends DescriptorVisibilityFilter {
    private static final Logger LOG = LoggerFactory.getLogger(DockerDescriptorVisibilityFilter.class);

    @Override
    public boolean filter(Object context, Descriptor descriptor) {
        if (getDockerComputerLauncherDescriptors().contains(descriptor) ||
                getDockerRetentionStrategyDescriptors().contains(descriptor)) {
            LOG.trace("Filtering '{}', for '{}'.", descriptor, context);
            return false;
        }

        return true;
    }
}
