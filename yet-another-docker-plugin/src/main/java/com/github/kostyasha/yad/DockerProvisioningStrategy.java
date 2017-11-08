package com.github.kostyasha.yad;

import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import com.github.kostyasha.yad.utils.DockerCloudLoadComparator;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Label;
import hudson.model.LoadStatistics.LoadStatisticsSnapshot;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.RetentionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

import static com.github.kostyasha.yad.utils.DockerFunctions.getAvailableDockerClouds;
import static com.github.kostyasha.yad.utils.DockerFunctions.getDockerClouds;
import static hudson.ExtensionList.lookup;
import static java.util.Objects.isNull;

/**
 * Idea picked from mansion-cloud @stephenc
 *
 * @author Kanstantsin Shautsou
 */
@Extension(ordinal = 10)
public class DockerProvisioningStrategy extends NodeProvisioner.Strategy {
    private static final Logger LOG = LoggerFactory.getLogger(DockerProvisioningStrategy.class);

    /**
     * For groovy.
     */
    public void setEnabled(boolean enabled) {
        final ExtensionList<NodeProvisioner.Strategy> strategies = lookup(NodeProvisioner.Strategy.class);
        DockerProvisioningStrategy strategy = strategies.get(DockerProvisioningStrategy.class);

        if (isNull(strategy)) {
            LOG.debug("YAD strategy was null, creating new.");
            strategy = new DockerProvisioningStrategy();
        } else {
            LOG.debug("Removing YAD strategy.");
            strategies.remove(strategy);
        }

        LOG.debug("Inserting YAD strategy at position 0");
        strategies.add(0, strategy);
    }

    /**
     * Do asap provisioning for OnceRetention with one executor.  The
     * provisioning strategy is to attempt to find a random least loaded cloud.
     * Some other configuration may also want such behaviour?
     */
    @Nonnull
    @Override
    public NodeProvisioner.StrategyDecision apply(@Nonnull NodeProvisioner.StrategyState strategyState) {
        LOG.debug("Applying provisioning.");
        final Label label = strategyState.getLabel();
        LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();

        //create a random list of docker clouds and prioritize idle clouds
        List<DockerCloud> provisionClouds = null;
        List<DockerCloud> availableClouds = getAvailableDockerClouds(label);
        if (availableClouds.size() > 0) {
            //select available clouds based on label which have potential capacity
            LOG.debug("Picking from available clouds.");
            provisionClouds = availableClouds;
        } else {
            //if there's no available clouds then fall back to original behavior
            LOG.debug("Falling back to getting all clouds regardless of availability.");
            provisionClouds = getDockerClouds();
        }
        //randomize the order of the DockerCloud list
        Collections.shuffle(provisionClouds);
        //sort by least loaded DockerCloud (i.e. fewest provisioned slaves)
        Collections.sort(provisionClouds, new DockerCloudLoadComparator());
        LOG.debug("Least loaded randomized DockerCloud: " +
                ((provisionClouds.size() > 0) ? provisionClouds.get(0).name : "none available"));

        for (DockerCloud dockerCloud : provisionClouds) {
            for (DockerSlaveTemplate template : dockerCloud.getTemplates(label)) {
                if (notAllowedStrategy(template)) {
                    continue;
                }

                int availableCapacity = snapshot.getAvailableExecutors() +
                        snapshot.getConnectingExecutors() +
                        strategyState.getAdditionalPlannedCapacity() +
                        strategyState.getPlannedCapacitySnapshot();

                int currentDemand = snapshot.getQueueLength();

                LOG.debug("Available capacity={}, currentDemand={}", availableCapacity, currentDemand);

                if (availableCapacity < currentDemand) {
                    // may happen that would be provisioned with other template
                    Collection<PlannedNode> plannedNodes = dockerCloud.provision(label, currentDemand - availableCapacity);
                    LOG.debug("Planned {} new nodes", plannedNodes.size());

                    strategyState.recordPendingLaunches(plannedNodes);
                    // FIXME calculate executors number?
                    availableCapacity += plannedNodes.size();
                    LOG.debug("After '{}' provisioning, available capacity={}, currentDemand={}",
                            dockerCloud, availableCapacity, currentDemand);
                }

                if (availableCapacity >= currentDemand) {
                    LOG.debug("Provisioning completed");
                    return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
                } else {
                    LOG.debug("Provisioning not complete, trying next template");
                }
            }

            LOG.debug("Provisioning not complete, trying next YAD Cloud");
        }

        LOG.debug("Provisioning not complete, consulting remaining strategies");
        return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
    }

    /**
     * Exclude unknown mix of configuration.
     */
    @VisibleForTesting
    protected static boolean notAllowedStrategy(DockerSlaveTemplate template) {
        if (isNull(template)) {
            LOG.debug("Skipping DockerProvisioningStrategy because: template is null");
            return true;
        }

        final RetentionStrategy retentionStrategy = template.getRetentionStrategy();
        if (isNull(retentionStrategy)) {
            LOG.debug("Skipping DockerProvisioningStrategy because: strategy is null for {}", template);
        }

        if (retentionStrategy instanceof DockerOnceRetentionStrategy) {
            if (template.getNumExecutors() == 1) {
                LOG.debug("Applying faster provisioning for single executor template {}", template);
                return false;
            } else {
                LOG.debug("Skipping DockerProvisioningStrategy because: numExecutors is {} for {}",
                        template.getNumExecutors(), template);
                return true;
            }
        }

        if (retentionStrategy instanceof RetentionStrategy.Demand) {
            LOG.debug("Applying faster provisioning for Demand strategy for template {}", template);
            return false;
        }

        // forbid by default
        LOG.trace("Skipping YAD provisioning for unknown mix of configuration for {}", template);
        return true;
    }
}
