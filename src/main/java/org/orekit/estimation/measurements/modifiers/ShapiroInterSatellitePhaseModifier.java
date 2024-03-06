/* Copyright 2002-2024 CS GROUP
 * Licensed to CS GROUP (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.estimation.measurements.modifiers;

import java.util.Collections;
import java.util.List;

import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.EstimationModifier;
import org.orekit.estimation.measurements.gnss.InterSatellitesPhase;
import org.orekit.utils.ParameterDriver;
import org.orekit.utils.TimeStampedPVCoordinates;

/** Class modifying theoretical inter-satellites phase measurement with Shapiro time delay.
 * <p>
 * Shapiro time delay is a relativistic effect due to gravity.
 * </p>
 * @author Bryan Cazabonne
 * @since 10.3
 */
public class ShapiroInterSatellitePhaseModifier extends AbstractShapiroBaseModifier implements EstimationModifier<InterSatellitesPhase> {

    /** Simple constructor.
     * @param gm gravitational constant for main body in signal path vicinity.
     */
    public ShapiroInterSatellitePhaseModifier(final double gm) {
        super(gm);
    }

    /** {@inheritDoc} */
    @Override
    public List<ParameterDriver> getParametersDrivers() {
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
    public void modifyWithoutDerivatives(final EstimatedMeasurementBase<InterSatellitesPhase> estimated) {
        // Compute correction
        final TimeStampedPVCoordinates[] pv = estimated.getParticipants();
        final double correction = shapiroCorrection(pv[0], pv[1]);
        // Update estimated value taking into account the Shapiro time delay.
        final double wavelength = estimated.getObservedMeasurement().getWavelength();
        final double[] newValue = estimated.getEstimatedValue().clone();
        newValue[0] = newValue[0] + (correction / wavelength);
        estimated.modifyEstimatedValue(this, newValue);
    }

}
