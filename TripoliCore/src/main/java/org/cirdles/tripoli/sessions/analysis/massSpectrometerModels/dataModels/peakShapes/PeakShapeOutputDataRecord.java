/*
 * Copyright 2022 James Bowring, Noah McLean, Scott Burdick, and CIRDLES.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.peakShapes;

import org.ojalgo.matrix.store.Primitive64Store;

import java.io.Serializable;

/**
 * @param magnetMasses
 * @param measuredPeakIntensities
 * @param peakCenterMass
 * @param integrationPeriodMS
 * @param massID
 * @param detectorName
 * @param collectorWidthAMU
 * @param theoreticalBeamWidthAMU
 * @param collectorLimits
 * @param deltaMagnetMass
 * @param beamWindow
 */
public record PeakShapeOutputDataRecord(
        Primitive64Store magnetMasses, // vector of masses for blockIntensities measurements
        Primitive64Store measuredPeakIntensities,    // vector of corresponding peak I0
        double peakCenterMass,             // mass at center of peak from header
        double integrationPeriodMS,              // integration period of measurements in ms
        String massID,                     // name of peak getting centered e.g. "205Pb"
        String detectorName,               // name of detector as string e.g. "L2"
        double collectorWidthAMU,          // width of collector aperture in AMU at center mass
        double theoreticalBeamWidthAMU,     // width of beam in AMU at center mass
        Primitive64Store collectorLimits,
        double deltaMagnetMass,
        double beamWindow
) implements Serializable {
}
