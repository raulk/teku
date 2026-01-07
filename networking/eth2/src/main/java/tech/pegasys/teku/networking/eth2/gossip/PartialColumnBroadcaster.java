/*
 * Copyright Consensys Software Inc., 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.eth2.gossip;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.PartialDataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.PartialDataColumnSidecarSchema;

/**
 * Manages partial data column broadcasting for PeerDAS. Handles quarantine cache for early arrivals
 * and validation semaphore.
 */
public class PartialColumnBroadcaster {
  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_QUARANTINE_SIZE = 1024;
  private static final int MAX_CONCURRENT_VALIDATIONS = 128;

  private final PartialDataColumnSidecarSchema schema;
  private final Semaphore validationSemaphore;

  // blockRoot -> columnIndex -> DataColumnPartialMessage
  private final Map<Bytes32, Map<Integer, DataColumnPartialMessage>> activeGroups;

  // Quarantine for partials that arrive before we have the parent
  private final Map<Bytes32, Map<Integer, PartialDataColumnSidecar>> quarantine;

  public PartialColumnBroadcaster(final PartialDataColumnSidecarSchema schema) {
    this.schema = schema;
    this.validationSemaphore = new Semaphore(MAX_CONCURRENT_VALIDATIONS);
    this.activeGroups = new ConcurrentHashMap<>();
    this.quarantine = new ConcurrentHashMap<>();
  }

  /** Broadcasts a full data column sidecar to the network as partial messages. */
  public SafeFuture<Void> broadcastDataColumn(final DataColumnSidecar sidecar) {
    final Bytes32 blockRoot = sidecar.getBeaconBlockRoot();
    final int columnIndex = sidecar.getIndex().intValue();

    LOG.debug("Broadcasting data column {} for block {}", columnIndex, blockRoot);

    final DataColumnPartialMessage partialMessage =
        DataColumnPartialMessage.fromFullSidecar(sidecar, schema);

    activeGroups
        .computeIfAbsent(blockRoot, k -> new ConcurrentHashMap<>())
        .put(columnIndex, partialMessage);

    // TODO: Integrate with GossipNetwork.publishPartial
    return SafeFuture.COMPLETE;
  }

  /** Handles incoming partial data column sidecar. */
  public SafeFuture<Void> onIncomingPartial(
      final Bytes32 blockRoot, final int columnIndex, final PartialDataColumnSidecar partial) {

    LOG.trace("Received partial for column {} block {}", columnIndex, blockRoot);

    // Check if we have an active group for this
    final Map<Integer, DataColumnPartialMessage> groupMap = activeGroups.get(blockRoot);
    if (groupMap == null) {
      // Quarantine for later
      return quarantinePartial(blockRoot, columnIndex, partial);
    }

    final DataColumnPartialMessage existing = groupMap.get(columnIndex);
    if (existing == null) {
      return quarantinePartial(blockRoot, columnIndex, partial);
    }

    // Validate and merge
    return validateAndMerge(existing, partial, groupMap, columnIndex);
  }

  private SafeFuture<Void> quarantinePartial(
      final Bytes32 blockRoot, final int columnIndex, final PartialDataColumnSidecar partial) {

    // Check quarantine size
    final int totalQuarantined = quarantine.values().stream().mapToInt(Map::size).sum();

    if (totalQuarantined >= MAX_QUARANTINE_SIZE) {
      LOG.warn("Quarantine full, dropping partial for block {}", blockRoot);
      return SafeFuture.COMPLETE;
    }

    quarantine.computeIfAbsent(blockRoot, k -> new ConcurrentHashMap<>()).put(columnIndex, partial);

    return SafeFuture.COMPLETE;
  }

  private SafeFuture<Void> validateAndMerge(
      final DataColumnPartialMessage existing,
      final PartialDataColumnSidecar incoming,
      final Map<Integer, DataColumnPartialMessage> groupMap,
      final int columnIndex) {

    return SafeFuture.of(
        () -> {
          try {
            validationSemaphore.acquire();
            try {
              // TODO: Implement KZG proof validation for cells
              // For now, accept all cells as valid
              final List<Integer> validCells = incoming.getPresentCellIndices();

              // Merge validated cells - merge returns a new instance
              final DataColumnPartialMessage merged = existing.merge(incoming, validCells);
              groupMap.put(columnIndex, merged);

              LOG.debug(
                  "Merged {} cells into column {}", validCells.size(), existing.getColumnIndex());

            } finally {
              validationSemaphore.release();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during validation", e);
          }
          return null;
        });
  }

  /** Cleans up state for a completed block. */
  public void onBlockFinalized(final Bytes32 blockRoot) {
    activeGroups.remove(blockRoot);
    quarantine.remove(blockRoot);
  }

  /** Returns the number of active groups being tracked. */
  public int getActiveGroupCount() {
    return activeGroups.size();
  }

  /** Returns the total number of quarantined partials. */
  public int getQuarantineSize() {
    return quarantine.values().stream().mapToInt(Map::size).sum();
  }
}
