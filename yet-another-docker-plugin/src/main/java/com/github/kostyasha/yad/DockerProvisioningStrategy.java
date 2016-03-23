package com.github.kostyasha.yad;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics.LoadStatisticsSnapshot;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.github.kostyasha.yad.utils.DockerFunctions.anyCloudForLabel;

/**
 * Idea picked from mansion-cloud @stephenc
 *
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerProvisioningStrategy extends NodeProvisioner.Strategy {
    private static final Logger LOG = LoggerFactory.getLogger(DockerProvisioningStrategy.class);

    @Nonnull
    @Override
    public NodeProvisioner.StrategyDecision apply(@Nonnull NodeProvisioner.StrategyState strategyState) {
        final Label label = strategyState.getLabel();
        DockerCloud dockerCloud = anyCloudForLabel(label);

        if (dockerCloud == null) {
            return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
        }

        LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();

        int availableCapacity = snapshot.getAvailableExecutors() +
                snapshot.getConnectingExecutors() +
                strategyState.getAdditionalPlannedCapacity();

        int currentDemand = snapshot.getQueueLength();

        LOG.debug("Available capacity={}, currentDemand={}", availableCapacity, currentDemand);

        if (availableCapacity < currentDemand) {
            Collection<PlannedNode> plannedNodes = dockerCloud.provision(label, currentDemand - availableCapacity);
            LOG.debug("Planned {} new nodes", plannedNodes.size());

            strategyState.recordPendingLaunches(plannedNodes);
            availableCapacity += plannedNodes.size();
            LOG.debug("After provisioning, available capacity={}, currentDemand={}", availableCapacity, currentDemand);
        }

        if (availableCapacity >= currentDemand) {
            LOG.debug("Provisioning completed");
            return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
        } else {
            LOG.debug("Provisioning not complete, consulting remaining strategies");
            return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
        }
    }
}
