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

import io.libp2p.pubsub.partial.PartialMessage;
import io.libp2p.pubsub.partial.PartialPublishAction;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.Cell;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.PartialDataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.PartialDataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

/** Wraps a DataColumnSidecar (or partial) to implement the PartialMessage interface. */
public class DataColumnPartialMessage implements PartialMessage {

  private final Bytes32 blockRoot;
  private final int columnIndex;
  private final SszBitlist cellsPresentBitmap;
  private final SszList<Cell> cells;
  private final SszList<SszKZGProof> kzgProofs;
  private final PartialDataColumnSidecarSchema schema;

  public DataColumnPartialMessage(
      final Bytes32 blockRoot,
      final int columnIndex,
      final SszBitlist cellsPresentBitmap,
      final SszList<Cell> cells,
      final SszList<SszKZGProof> kzgProofs,
      final PartialDataColumnSidecarSchema schema) {
    this.blockRoot = blockRoot;
    this.columnIndex = columnIndex;
    this.cellsPresentBitmap = cellsPresentBitmap;
    this.cells = cells;
    this.kzgProofs = kzgProofs;
    this.schema = schema;
  }

  /** Creates from a full DataColumnSidecar. */
  public static DataColumnPartialMessage fromFullSidecar(
      final DataColumnSidecar sidecar, final PartialDataColumnSidecarSchema schema) {
    final int cellCount = sidecar.getColumn().size();
    // Create bitmap with all cells present
    final int[] allIndices = IntStream.range(0, cellCount).toArray();
    final SszBitlist bitmap = schema.getCellsPresentBitmapSchema().ofBits(cellCount, allIndices);

    // Convert DataColumn (SszList<Cell>) to list for the partial message
    final List<Cell> cellList = new ArrayList<>(cellCount);
    for (int i = 0; i < cellCount; i++) {
      cellList.add(sidecar.getColumn().get(i));
    }

    return new DataColumnPartialMessage(
        sidecar.getBeaconBlockRoot(),
        sidecar.getIndex().intValue(),
        bitmap,
        schema.getPartialColumnSchema().createFromElements(cellList),
        sidecar.getKzgProofs(),
        schema);
  }

  @Override
  public byte[] groupId() {
    return blockRoot.toArray();
  }

  @Override
  public byte[] partsMetadata() {
    return cellsPresentBitmap.sszSerialize().toArray();
  }

  @Override
  public PartialPublishAction partialMessageBytes(final byte[] requestedMetadata) {
    if (requestedMetadata == null) {
      // Peer hasn't sent metadata yet, send our metadata only
      return new PartialPublishAction(false, null, partsMetadata());
    }

    // Decode peer's bitmap
    final SszBitlist peerHas =
        schema.getCellsPresentBitmapSchema().sszDeserialize(Bytes.wrap(requestedMetadata));

    // Find cells peer is missing that we have
    final List<Integer> toSend = new ArrayList<>();
    for (int i = 0; i < cellsPresentBitmap.size(); i++) {
      if (cellsPresentBitmap.getBit(i) && (i >= peerHas.size() || !peerHas.getBit(i))) {
        toSend.add(i);
      }
    }

    if (toSend.isEmpty()) {
      // Peer has everything we have
      final boolean needMore = checkIfNeedMore(peerHas);
      return new PartialPublishAction(needMore, null, null);
    }

    // Build partial sidecar with only the cells peer needs
    final List<Cell> partialCells = new ArrayList<>();
    final List<SszKZGProof> partialProofs = new ArrayList<>();
    final List<Integer> sentIndices = new ArrayList<>();

    int cellIndex = 0;
    for (int i = 0; i < cellsPresentBitmap.size(); i++) {
      if (cellsPresentBitmap.getBit(i)) {
        if (toSend.contains(i)) {
          partialCells.add(cells.get(cellIndex));
          partialProofs.add(kzgProofs.get(cellIndex));
          sentIndices.add(i);
        }
        cellIndex++;
      }
    }

    final SszBitlist sentBitmap =
        schema
            .getCellsPresentBitmapSchema()
            .ofBits(cellsPresentBitmap.size(), sentIndices.stream().mapToInt(i -> i).toArray());

    final PartialDataColumnSidecar partial =
        schema.create(
            sentBitmap,
            schema.getPartialColumnSchema().createFromElements(partialCells),
            schema.getKzgProofsSchema().createFromElements(partialProofs));

    final byte[] messageBytes = partial.sszSerialize().toArray();

    // Check if we need more from peer
    final boolean needMore = checkIfNeedMore(peerHas);

    // Updated metadata = union of what peer has and what we sent
    final List<Integer> unionIndices = new ArrayList<>();
    for (int i = 0; i < peerHas.size(); i++) {
      if (peerHas.getBit(i)) {
        unionIndices.add(i);
      }
    }
    for (final int idx : sentIndices) {
      if (!unionIndices.contains(idx)) {
        unionIndices.add(idx);
      }
    }

    final int unionSize = Math.max(peerHas.size(), cellsPresentBitmap.size());
    final SszBitlist updatedBitmap =
        schema
            .getCellsPresentBitmapSchema()
            .ofBits(unionSize, unionIndices.stream().mapToInt(i -> i).toArray());

    return new PartialPublishAction(needMore, messageBytes, updatedBitmap.sszSerialize().toArray());
  }

  private boolean checkIfNeedMore(final SszBitlist peerHas) {
    for (int i = 0; i < peerHas.size(); i++) {
      if (peerHas.getBit(i) && (i >= cellsPresentBitmap.size() || !cellsPresentBitmap.getBit(i))) {
        return true;
      }
    }
    return false;
  }

  /** Merges cells from a received partial sidecar into this message. */
  public DataColumnPartialMessage merge(
      final PartialDataColumnSidecar received, final List<Integer> validatedCellIndices) {
    // Create list of all indices we'll have after merge
    final List<Integer> newIndices = new ArrayList<>();
    for (int i = 0; i < cellsPresentBitmap.size(); i++) {
      if (cellsPresentBitmap.getBit(i)) {
        newIndices.add(i);
      }
    }
    for (final int idx : validatedCellIndices) {
      if (!newIndices.contains(idx)) {
        newIndices.add(idx);
      }
    }
    newIndices.sort(Integer::compareTo);

    final int newSize =
        Math.max(cellsPresentBitmap.size(), received.getCellsPresentBitmap().size());
    final SszBitlist newBitmap =
        schema
            .getCellsPresentBitmapSchema()
            .ofBits(newSize, newIndices.stream().mapToInt(i -> i).toArray());

    // Merge cell data - need to maintain sorted order by index
    final List<Cell> newCells = new ArrayList<>();
    final List<SszKZGProof> newProofs = new ArrayList<>();

    final List<Integer> receivedIndices = received.getPresentCellIndices();

    for (final int idx : newIndices) {
      // Check if we already have this cell
      int ourPosition = -1;
      int posCounter = 0;
      for (int i = 0; i < cellsPresentBitmap.size(); i++) {
        if (cellsPresentBitmap.getBit(i)) {
          if (i == idx) {
            ourPosition = posCounter;
            break;
          }
          posCounter++;
        }
      }

      if (ourPosition >= 0) {
        // We already have this cell
        newCells.add(cells.get(ourPosition));
        newProofs.add(kzgProofs.get(ourPosition));
      } else if (validatedCellIndices.contains(idx)) {
        // Get from received partial
        final int posInReceived = receivedIndices.indexOf(idx);
        if (posInReceived >= 0) {
          newCells.add(received.getPartialColumn().get(posInReceived));
          newProofs.add(received.getKzgProofs().get(posInReceived));
        }
      }
    }

    return new DataColumnPartialMessage(
        blockRoot,
        columnIndex,
        newBitmap,
        schema.getPartialColumnSchema().createFromElements(newCells),
        schema.getKzgProofsSchema().createFromElements(newProofs),
        schema);
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public int getColumnIndex() {
    return columnIndex;
  }

  public SszBitlist getCellsPresentBitmap() {
    return cellsPresentBitmap;
  }

  public SszList<Cell> getCells() {
    return cells;
  }

  public SszList<SszKZGProof> getKzgProofs() {
    return kzgProofs;
  }

  public boolean isComplete(final int expectedCells) {
    return cellsPresentBitmap.getBitCount() >= expectedCells;
  }
}
