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

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class PartialDataColumnSidecarSchema
    extends ContainerSchema3<
        PartialDataColumnSidecar, SszBitlist, SszList<Cell>, SszList<SszKZGProof>> {

  public PartialDataColumnSidecarSchema(final SpecConfigFulu specConfig) {
    super(
        "PartialDataColumnSidecar",
        namedSchema(
            "cells_present_bitmap",
            SszBitlistSchema.create(specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(
            "partial_column",
            SszListSchema.create(
                new CellSchema(specConfig), specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(
            "kzg_proofs",
            SszListSchema.create(
                SszKZGProofSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())));
  }

  public SszBitlistSchema<?> getCellsPresentBitmapSchema() {
    return (SszBitlistSchema<?>) getChildSchema(0);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Cell, ?> getPartialColumnSchema() {
    return (SszListSchema<Cell, ?>) getChildSchema(1);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGProof, ?> getKzgProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(2);
  }

  @Override
  public PartialDataColumnSidecar createFromBackingNode(final TreeNode node) {
    return new PartialDataColumnSidecar(this, node);
  }

  public PartialDataColumnSidecar create(
      final SszBitlist cellsPresentBitmap,
      final SszList<Cell> partialColumn,
      final SszList<SszKZGProof> kzgProofs) {
    return new PartialDataColumnSidecar(this, cellsPresentBitmap, partialColumn, kzgProofs);
  }
}
