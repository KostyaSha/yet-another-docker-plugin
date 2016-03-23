package com.github.kostyasha.yad;

import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics.LoadStatisticsSnapshot;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.github.kostyasha.yad.utils.DockerFunctions.getDockerClouds;

/**
 * Idea picked from mansion-cloud @stephenc
 *
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerProvisioningStrategy extends NodeProvisioner.Strategy {
    private static final Logger LOG = LoggerFactory.getLogger(DockerProvisioningStrategy.class);

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
