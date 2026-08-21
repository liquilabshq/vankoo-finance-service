package com.liquilabs.vankoo.finance.infrastructure.configuration;

import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code Wallet} is the first aggregate in this service that actually needs a
 * snapshot: unlike {@code Deposit}, it accumulates one event per movement for
 * the investor's whole lifetime. {@code Snapshotter} itself is already
 * provided by Axon's Spring Boot autoconfiguration ({@code aggregateSnapshotter});
 * this only supplies the trigger.
 */
@Configuration
public class WalletSnapshotConfiguration {

    /** Provisional threshold — see the Snapshots section of wallet-contracts.md. */
    private static final int SNAPSHOT_THRESHOLD = 100;

    @Bean
    public SnapshotTriggerDefinition walletSnapshotTriggerDefinition(Snapshotter snapshotter) {
        return new EventCountSnapshotTriggerDefinition(snapshotter, SNAPSHOT_THRESHOLD);
    }
}
