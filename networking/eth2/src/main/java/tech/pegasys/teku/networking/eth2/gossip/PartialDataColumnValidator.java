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

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.PartialDataColumnSidecar;

/** Validates KZG proofs for partial data column cells. */
public class PartialDataColumnValidator {
  private static final Logger LOG = LogManager.getLogger();

  /**
   * Validates the KZG proofs for cells in a partial sidecar.
   *
   * @param partial The partial sidecar to validate
   * @return List of valid cell indices
   */
  public List<Integer> validateCells(final PartialDataColumnSidecar partial) {
    final List<Integer> validIndices = new ArrayList<>();
    final List<Integer> presentIndices = partial.getPresentCellIndices();

    // TODO: Implement actual KZG proof validation
    // For now, accept all cells
    for (int i = 0; i < presentIndices.size(); i++) {
      // Cell cell = partial.getPartialColumn().get(i);
      // SszKZGProof proof = partial.getKzgProofs().get(i);
      // if (verifyKzgProof(cell, proof)) {
      //   validIndices.add(presentIndices.get(i));
      // }
      validIndices.add(presentIndices.get(i));
    }

    LOG.trace("Validated {} of {} cells", validIndices.size(), presentIndices.size());
    return validIndices;
  }
}
