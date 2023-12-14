/* Copyright 2002-2023 CS GROUP
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

import java.util.Collections;
import java.util.List;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.FieldGeodeticPoint;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.models.earth.weather.ConstantPressureTemperatureHumidityProvider;
import org.orekit.models.earth.weather.FieldPressureTemperatureHumidity;
import org.orekit.models.earth.weather.PressureTemperatureHumidity;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.ParameterDriver;

/** An estimated tropospheric model. The tropospheric delay is computed according to the formula:
 * <p>
 * δ = δ<sub>h</sub> * m<sub>h</sub> + (δ<sub>t</sub> - δ<sub>h</sub>) * m<sub>w</sub>
 * <p>
 * With:
 * <ul>
 * <li>δ<sub>h</sub>: Tropospheric zenith hydro-static delay.</li>
 * <li>δ<sub>t</sub>: Tropospheric total zenith delay.</li>
 * <li>m<sub>h</sub>: Hydro-static mapping function.</li>
 * <li>m<sub>w</sub>: Wet mapping function.</li>
 * </ul>
 * <p>
 * The mapping functions m<sub>h</sub>(e) and m<sub>w</sub>(e) are
 * computed thanks to a {@link #model} initialized by the user.
 * The user has the possibility to use several mapping function models for the computations:
 * the {@link GlobalMappingFunctionModel Global Mapping Function}, or
 * the {@link NiellMappingFunctionModel Niell Mapping Function}
 * </p> <p>
 * The tropospheric zenith delay δ<sub>h</sub> is computed empirically with a
 * {@link DiscreteTroposphericModel tropospheric model}
 * while the tropospheric total zenith delay δ<sub>t</sub> is estimated as a {@link ParameterDriver},
 * hence the wet part is the difference between the two.
 * @since 12.1
 */
public class EstimatedModel implements TroposphericModel {

    /** Name of the parameter of this model: the total zenith delay. */
    public static final String TOTAL_ZENITH_DELAY = "total zenith delay";

    /** Mapping Function model. */
    private final TroposphereMappingFunction model;

    /** Driver for the tropospheric zenith total delay.*/
    private final ParameterDriver totalZenithDelay;

    /** Model for hydrostatic component. */
    private final TroposphericModel hydrostatic;

    /** Build a new instance using the given environmental conditions.
     * <p>
     * This constructor uses a {@link ModifiedSaastamoinenModel} for the hydrostatic contribution.
     * </p>
     * @param h0 altitude of the station [m]
     * @param t0 the temperature at the station [K]
     * @param p0 the atmospheric pressure at the station [mbar]
     * @param model mapping function model.
     * @param totalDelay initial value for the tropospheric zenith total delay [m]
     */
    public EstimatedModel(final double h0, final double t0, final double p0,
                          final TroposphereMappingFunction model, final double totalDelay) {
        this(new ModifiedSaastamoinenModel(new ConstantPressureTemperatureHumidityProvider(new PressureTemperatureHumidity(h0,
                                                                                                                           TroposphericModelUtils.HECTO_PASCAL.toSI(p0),
                                                                                                                           t0,
                                                                                                                           0.0))),
             model, totalDelay);
    }

    /** Build a new instance using the given environmental conditions.
     * @param hydrostatic model for hydrostatic component
     * @param model mapping function model.
     * @param totalDelay initial value for the tropospheric zenith total delay [m]
     * @since 12.1
     */
    public EstimatedModel(final TroposphericModel hydrostatic,
                          final TroposphereMappingFunction model,
                          final double totalDelay) {

        totalZenithDelay = new ParameterDriver(EstimatedModel.TOTAL_ZENITH_DELAY,
                                               totalDelay, FastMath.scalb(1.0, 0), 0.0, Double.POSITIVE_INFINITY);

        this.hydrostatic = hydrostatic;
        this.model = model;
    }

    /** Build a new instance using a standard atmosphere model.
     * <ul>
     * <li>altitude: 0m
     * <li>temperature: 18 degree Celsius
     * <li>pressure: 1013.25 mbar
     * </ul>
     * @param model mapping function model.
     * @param totalDelay initial value for the tropospheric zenith total delay [m]
     */
    public EstimatedModel(final TroposphereMappingFunction model, final double totalDelay) {
        this(0.0, 273.15 + 18.0, 1013.25, model, totalDelay);
    }

    /** {@inheritDoc} */
    @Override
    public List<ParameterDriver> getParametersDrivers() {
        return Collections.singletonList(totalZenithDelay);
    }

    /** {@inheritDoc} */
    @Override
    public double pathDelay(final double elevation, final GeodeticPoint point,
                            final PressureTemperatureHumidity weather,
                            final double[] parameters, final AbsoluteDate date) {
        // Zenith delays. elevation = pi/2 because we compute the delay in the zenith direction
        final double zhd = hydrostatic.pathDelay(0.5 * FastMath.PI, point, weather, parameters, date);
        final double ztd = parameters[0];
        // Mapping functions
        final double[] mf = model.mappingFactors(elevation, point, weather, date);
        // Total delay
        return mf[0] * zhd + mf[1] * (ztd - zhd);
    }

    /** {@inheritDoc} */
    @Override
    public <T extends CalculusFieldElement<T>> T pathDelay(final T elevation, final FieldGeodeticPoint<T> point,
                                                           final FieldPressureTemperatureHumidity<T> weather,
                                                           final T[] parameters, final FieldAbsoluteDate<T> date) {
        // Zenith delays. elevation = pi/2 because we compute the delay in the zenith direction
        final T zhd = hydrostatic.pathDelay(elevation.getPi().multiply(0.5), point, weather, parameters, date);
        final T ztd = parameters[0];
        // Mapping functions
        final T[] mf = model.mappingFactors(elevation, point, weather, date);
        // Total delay
        return mf[0].multiply(zhd).add(mf[1].multiply(ztd.subtract(zhd)));
    }

}
