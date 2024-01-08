/* Copyright 2002-2024 Thales Alenia Space
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
package org.orekit.models.earth.troposphere;

import org.hipparchus.CalculusFieldElement;
import org.orekit.bodies.FieldGeodeticPoint;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.models.earth.weather.FieldPressureTemperatureHumidity;
import org.orekit.models.earth.weather.PressureTemperatureHumidity;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.FieldTrackingCoordinates;
import org.orekit.utils.TrackingCoordinates;

/** Adapter between {@link MappingFunction} and {@link TroposphereMappingFunction}.
 * <p>
 * This class is a temporary adapter, it will be removed when
 * {@link MappingFunction} is removed.
 * <p>
 * @author Luc Maisonobe
 * @since 12.1
 * @deprecated temporary adapter to be removed when {@link MappingFunction} is removed
 */
@Deprecated
public class TroposphereMappingFunctionAdapter implements TroposphereMappingFunction {

    /** Underlying model. */
    private final MappingFunction model;

    /** Simple constructor.
     * @param model underlying model
     */
    public TroposphereMappingFunctionAdapter(final MappingFunction model) {
        this.model = model;
    }

    /** This method allows the computation of the hydrostatic and
     * wet mapping functions. The resulting element is an array having the following form:
     * <ul>
     * <li>double[0] = m<sub>h</sub>(e) → hydrostatic mapping function
     * <li>double[1] = m<sub>w</sub>(e) → wet mapping function
     * </ul>
     * @param trackingCoordinates tracking coordinates of the satellite
     * @param point station location
     * @param weather weather parameters
     * @param date current date
     * @return a two components array containing the hydrostatic and wet mapping functions.
     */
    public double[] mappingFactors(final TrackingCoordinates trackingCoordinates, final GeodeticPoint point,
                                   final PressureTemperatureHumidity weather, final AbsoluteDate date) {
        return model.mappingFactors(trackingCoordinates.getElevation(), point, date);
    }

    /** This method allows the computation of the hydrostatic and
     * wet mapping functions. The resulting element is an array having the following form:
     * <ul>
     * <li>T[0] = m<sub>h</sub>(e) → hydrostatic mapping function
     * <li>T[1] = m<sub>w</sub>(e) → wet mapping function
     * </ul>
     * @param trackingCoordinates tracking coordinates of the satellite
     * @param point station location
     * @param weather weather parameters
     * @param date current date
     * @param <T> type of the elements
     * @return a two components array containing the hydrostatic and wet mapping functions.
     */
    public <T extends CalculusFieldElement<T>> T[] mappingFactors(final FieldTrackingCoordinates<T> trackingCoordinates,
                                                                  final FieldGeodeticPoint<T> point,
                                                                  final FieldPressureTemperatureHumidity<T> weather,
                                                                  final FieldAbsoluteDate<T> date) {
        return model.mappingFactors(trackingCoordinates.getElevation(), point, date);
    }

}
