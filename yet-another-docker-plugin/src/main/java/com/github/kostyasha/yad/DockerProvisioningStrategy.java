package com.github.kostyasha.yad;

import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Label;
import hudson.model.LoadStatistics.LoadStatisticsSnapshot;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;

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
     * Do asap provisioning for OnceRetention with one executor.
     * Some other configuration may also want such behaviour?
     */
    @Nonnull
    @Override
    public NodeProvisioner.StrategyDecision apply(@Nonnull NodeProvisioner.StrategyState strategyState) {
        final Label label = strategyState.getLabel();
        LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();

        for (DockerCloud dockerCloud : getDockerClouds()) {
            DockerSlaveTemplate template = dockerCloud.getTemplate(label);
            // exclude unknown mix of configuration
            if (template != null &&
                    template.getRetentionStrategy() instanceof DockerOnceRetentionStrategy &&
                    template.getNumExecutors() == 1) {
                LOG.info("Skipping unknown mix of YAD configuration for {}", template);
                return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
            }

            int availableCapacity = snapshot.getAvailableExecutors() +
                    snapshot.getConnectingExecutors() +
                    strategyState.getAdditionalPlannedCapacity();

            int currentDemand = snapshot.getQueueLength();

            LOG.debug("Available capacity={}, currentDemand={}", availableCapacity, currentDemand);

            if (availableCapacity < currentDemand) {
                // may happen that would be provisioned with other template
                Collection<PlannedNode> plannedNodes = dockerCloud.provision(label, currentDemand - availableCapacity);
                LOG.debug("Planned {} new nodes", plannedNodes.size());

                strategyState.recordPendingLaunches(plannedNodes);
                availableCapacity += plannedNodes.size();
                LOG.debug("After '{}' provisioning, available capacity={}, currentDemand={}",
                        dockerCloud, availableCapacity, currentDemand);
            }

            if (availableCapacity >= currentDemand) {
                LOG.debug("Provisioning completed");
                return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
            } else {
                LOG.debug("Provisioning not complete, consulting remaining strategies");
                return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
            }
        }

        LOG.debug("Provisioning not complete, consulting remaining strategies");
        return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
    }
}
