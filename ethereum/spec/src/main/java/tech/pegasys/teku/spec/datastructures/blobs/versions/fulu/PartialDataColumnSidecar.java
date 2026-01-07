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

package tech.pegasys.teku.spec.datastructures.blobs.versions.fulu;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class PartialDataColumnSidecar
    extends Container3<PartialDataColumnSidecar, SszBitlist, SszList<Cell>, SszList<SszKZGProof>> {

  PartialDataColumnSidecar(
      final PartialDataColumnSidecarSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  PartialDataColumnSidecar(
      final PartialDataColumnSidecarSchema schema,
      final SszBitlist cellsPresentBitmap,
      final SszList<Cell> partialColumn,
      final SszList<SszKZGProof> kzgProofs) {
    super(schema, cellsPresentBitmap, partialColumn, kzgProofs);
  }

  public SszBitlist getCellsPresentBitmap() {
    return getField0();
  }

  public SszList<Cell> getPartialColumn() {
    return getField1();
  }

  public SszList<SszKZGProof> getKzgProofs() {
    return getField2();
  }

  /** Returns indices of cells that are present in this partial sidecar. */
  public List<Integer> getPresentCellIndices() {
    final List<Integer> indices = new ArrayList<>();
    final SszBitlist bitmap = getCellsPresentBitmap();
    for (int i = 0; i < bitmap.size(); i++) {
      if (bitmap.getBit(i)) {
        indices.add(i);
      }
    }
    return indices;
  }

  /** Returns the number of cells present. */
  public int getPresentCellCount() {
    return getCellsPresentBitmap().getBitCount();
  }

  /** Checks if a specific cell is present. */
  public boolean hasCellAt(final int index) {
    final SszBitlist bitmap = getCellsPresentBitmap();
    return index < bitmap.size() && bitmap.getBit(index);
  }

  @Override
  public PartialDataColumnSidecarSchema getSchema() {
    return (PartialDataColumnSidecarSchema) super.getSchema();
  }
}
