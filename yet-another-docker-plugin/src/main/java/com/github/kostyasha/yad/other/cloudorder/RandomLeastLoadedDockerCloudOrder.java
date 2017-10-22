package com.github.kostyasha.yad.other.cloudorder;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.utils.DockerCloudLoadComparator;
import hudson.Extension;
import hudson.model.Label;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.kostyasha.yad.utils.DockerFunctions.countCurrentDockerSlaves;
import static com.github.kostyasha.yad.utils.DockerFunctions.getAllDockerClouds;

public class RandomLeastLoadedDockerCloudOrder extends DockerCloudOrder {
    private static final Logger LOG = LoggerFactory.getLogger(RandomLeastLoadedDockerCloudOrder.class);

    @DataBoundConstructor
    public RandomLeastLoadedDockerCloudOrder() {
    }

    @Nonnull
    public List<DockerCloud> getDockerClouds(Label label) {
        List<DockerCloud> provisionClouds;

        //create a random list of docker clouds and prioritize idle clouds
        List<DockerCloud> availableClouds = getAvailableDockerClouds(label);
        if (availableClouds.size() > 0) {
            //select available clouds based on label which have potential capacity
            LOG.debug("Picking from available clouds.");
            provisionClouds = availableClouds;
        } else {
            //if there's no available clouds then fall back to original behavior
            LOG.debug("Falling back to getting all clouds regardless of availability.");
            provisionClouds = getAllDockerClouds();
        }

        //randomize the order of the DockerCloud list
        Collections.shuffle(provisionClouds);
        //sort by least loaded DockerCloud (i.e. fewest provisioned slaves)
        provisionClouds.sort(new DockerCloudLoadComparator());

        LOG.debug("Least loaded randomized DockerCloud: " +
                ((provisionClouds.size() > 0) ? provisionClouds.get(0).name : "none available"));

        return provisionClouds;
    }

    /**
     * Get a list of available DockerCloud clouds which are not at max
     * capacity.
     *
     * @param label A label expression of a Job Run requiring an executor.
     * @return A list of available DockerCloud clouds.
     */
    protected List<DockerCloud> getAvailableDockerClouds(Label label) {
        return getAllDockerClouds().stream()
                .filter(cloud ->
                        cloud.canProvision(label) &&
                                (countCurrentDockerSlaves(cloud) >= 0) &&
                                (countCurrentDockerSlaves(cloud) < cloud.getContainerCap()))
                .collect(Collectors.toList());
    }

    @Extension
    public static class DescriptorImpl extends DockerCloudOrderDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Least loaded with fallback to random";
        }
    }
}
