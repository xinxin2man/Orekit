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
package org.orekit.estimation.measurements.modifiers;

import java.util.List;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.MathUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.orekit.attitudes.LofOffset;
import org.orekit.estimation.Context;
import org.orekit.estimation.EstimationTestUtils;
import org.orekit.estimation.measurements.EstimatedMeasurement;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.orekit.estimation.measurements.OneWayRangeMeasurementCreator;
import org.orekit.estimation.measurements.Range;
import org.orekit.estimation.measurements.TwoWayRangeMeasurementCreator;
import org.orekit.frames.LOFType;
import org.orekit.gnss.antenna.FrequencyPattern;
import org.orekit.gnss.antenna.PhaseCenterVariationFunction;
import org.orekit.gnss.antenna.TwoDVariation;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.utils.Constants;

public class PhaseCentersRangeModifierTest {

    @Test
    public void testPreliminary() {

        // this test does not check PhaseCentersRangeModifier at all,
        // it just checks RangeMeasurementCreator behaves as necessary for the other test
        // the *real* test is testEffect below
        Context context = EstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final NumericalPropagatorBuilder propagatorBuilder =
                        context.createBuilder(OrbitType.KEPLERIAN, PositionAngle.TRUE, true,
                                              1.0e-6, 60.0, 0.001);
        propagatorBuilder.setAttitudeProvider(new LofOffset(propagatorBuilder.getFrame(), LOFType.LVLH));

        // create perfect range measurements without antenna offset
        final Propagator p1 = EstimationTestUtils.createPropagator(context.initialOrbit,
                                                                           propagatorBuilder);
        final List<ObservedMeasurement<?>> spacecraftCenteredMeasurements =
                        EstimationTestUtils.createMeasurements(p1,
                                                               new TwoWayRangeMeasurementCreator(context),
                                                               1.0, 3.0, 300.0);

        // create perfect range measurements with antenna offset
        final double xOffset = -2.5;
        final Propagator p2 = EstimationTestUtils.createPropagator(context.initialOrbit,
                                                                   propagatorBuilder);
        final List<ObservedMeasurement<?>> antennaCenteredMeasurements =
                        EstimationTestUtils.createMeasurements(p2,
                                                               new TwoWayRangeMeasurementCreator(context,
                                                                                                 Vector3D.ZERO, null,
                                                                                                 new Vector3D(xOffset, 0, 0), null,
                                                                                                 0),
                                                               1.0, 3.0, 300.0);

        for (int i = 0; i < spacecraftCenteredMeasurements.size(); ++i) {
            Range sr = (Range) spacecraftCenteredMeasurements.get(i);
            Range ar = (Range) antennaCenteredMeasurements.get(i);
            double alphaMax = FastMath.asin(Constants.WGS84_EARTH_EQUATORIAL_RADIUS / sr.getObservedValue()[0]);
            Assertions.assertEquals(0.0, sr.getDate().durationFrom(ar.getDate()), 1.0e-8);
            Assertions.assertTrue(ar.getObservedValue()[0] - sr.getObservedValue()[0] >= xOffset);
            Assertions.assertTrue(ar.getObservedValue()[0] - sr.getObservedValue()[0] <= xOffset * FastMath.cos(alphaMax));
        }
    }

    @Test
    public void testOneWayEffect() {

        Context context = EstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final NumericalPropagatorBuilder propagatorBuilder =
                        context.createBuilder(OrbitType.KEPLERIAN, PositionAngle.TRUE, true,
                                              1.0e-6, 60.0, 0.001);
        propagatorBuilder.setAttitudeProvider(new LofOffset(propagatorBuilder.getFrame(), LOFType.LVLH));
        final double groundClockOffset = 1.234e-3;
        for (final GroundStation station : context.stations) {
            station.getClockOffsetDriver().setValue(groundClockOffset);
        }

        // create perfect range measurements without antenna offset
        final Propagator p1 = EstimationTestUtils.createPropagator(context.initialOrbit, propagatorBuilder);
        final List<ObservedMeasurement<?>> spacecraftCenteredMeasurements =
                        EstimationTestUtils.createMeasurements(p1,
                                                               new OneWayRangeMeasurementCreator(context,
                                                                                                 Vector3D.ZERO, null,
                                                                                                 Vector3D.ZERO, null,
                                                                                                 0),
                                                               1.0, 3.0, 300.0);

        // create perfect range measurements with antenna offset
        final Vector3D stationMeanPosition   = new Vector3D(0.25, 0.25, -0.5);
        final PhaseCenterVariationFunction stationPCV = new TwoDVariation(0, FastMath.PI, MathUtils.SEMI_PI,
                                                                          new double[][] {
                                                                              { 0.0,  0.25, -0.25, 0.5 },
                                                                              { 0.0, -0.25,  0.25, 0.5 }
                                                                          });
        final Vector3D satelliteMeanPosition = new Vector3D(-2.5,  0,  0);
        final PhaseCenterVariationFunction satellitePCV = new TwoDVariation(0, FastMath.PI, MathUtils.SEMI_PI,
                                                                            new double[][] {
                                                                                { 0.0,  0.5, -0.5, 1.0 },
                                                                                { 0.0, -0.5,  0.5, 1.0 }
                                                                            });
        final Propagator p2 = EstimationTestUtils.createPropagator(context.initialOrbit,
                                                                   propagatorBuilder);
        final List<ObservedMeasurement<?>> antennaCenteredMeasurements =
                        EstimationTestUtils.createMeasurements(p2,
                                                               new OneWayRangeMeasurementCreator(context,
                                                                                                 stationMeanPosition,   stationPCV,
                                                                                                 satelliteMeanPosition, satellitePCV,
                                                                                                 0),
                                                               1.0, 3.0, 300.0);

        final Propagator p3 = EstimationTestUtils.createPropagator(context.initialOrbit,
                                                                   propagatorBuilder);

        PhaseCentersRangeModifier modifier = new PhaseCentersRangeModifier(new FrequencyPattern(stationMeanPosition,
                                                                                                stationPCV),
                                                                           new FrequencyPattern(satelliteMeanPosition,
                                                                                                satellitePCV));
        for (int i = 0; i < spacecraftCenteredMeasurements.size(); ++i) {
            Range sr = (Range) spacecraftCenteredMeasurements.get(i);
            sr.addModifier(modifier);
            EstimatedMeasurement<Range> estimated = sr.estimate(0, 0, new SpacecraftState[] { p3.propagate(sr.getDate()) });
            Range ar = (Range) antennaCenteredMeasurements.get(i);
            Assertions.assertEquals(0.0, sr.getDate().durationFrom(ar.getDate()), 1.0e-8);
            Assertions.assertEquals(ar.getObservedValue()[0], estimated.getEstimatedValue()[0], 1.6e-6);
        }

    }

    @Test
    public void testTwoWayEffect() {

        Context context = EstimationTestUtils.eccentricContext("regular-data:potential:tides");

        final NumericalPropagatorBuilder propagatorBuilder =
                        context.createBuilder(OrbitType.KEPLERIAN, PositionAngle.TRUE, true,
                                              1.0e-6, 60.0, 0.001);
        propagatorBuilder.setAttitudeProvider(new LofOffset(propagatorBuilder.getFrame(), LOFType.LVLH));
        final double groundClockOffset = 1.234e-3;
        for (final GroundStation station : context.stations) {
            station.getClockOffsetDriver().setValue(groundClockOffset);
        }

        // create perfect range measurements without antenna offset
        final Propagator p1 = EstimationTestUtils.createPropagator(context.initialOrbit, propagatorBuilder);
        final List<ObservedMeasurement<?>> spacecraftCenteredMeasurements =
                        EstimationTestUtils.createMeasurements(p1,
                                                               new TwoWayRangeMeasurementCreator(context,
                                                                                                 Vector3D.ZERO, null,
                                                                                                 Vector3D.ZERO, null,
                                                                                                 0),
                                                               1.0, 3.0, 300.0);

        // create perfect range measurements with antenna offset
        final Vector3D stationMeanPosition   = new Vector3D(0.25, 0.25, -0.5);
        final PhaseCenterVariationFunction stationPCV = new TwoDVariation(0, FastMath.PI, MathUtils.SEMI_PI,
                                                                          new double[][] {
                                                                              { 0.0,  0.25, -0.25, 0.5 },
                                                                              { 0.0, -0.25,  0.25, 0.5 }
                                                                          });
        final Vector3D satelliteMeanPosition = new Vector3D(-2.5,  0,  0);
        final PhaseCenterVariationFunction satellitePCV = new TwoDVariation(0, FastMath.PI, MathUtils.SEMI_PI,
                                                                            new double[][] {
                                                                                { 0.0,  0.5, -0.5, 1.0 },
                                                                                { 0.0, -0.5,  0.5, 1.0 }
                                                                            });
        final Propagator p2 = EstimationTestUtils.createPropagator(context.initialOrbit,
                                                                   propagatorBuilder);
        final List<ObservedMeasurement<?>> antennaCenteredMeasurements =
                        EstimationTestUtils.createMeasurements(p2,
                                                               new TwoWayRangeMeasurementCreator(context,
                                                                                                 stationMeanPosition,   stationPCV,
                                                                                                 satelliteMeanPosition, satellitePCV,
                                                                                                 0),
                                                               1.0, 3.0, 300.0);

        final Propagator p3 = EstimationTestUtils.createPropagator(context.initialOrbit,
                                                                   propagatorBuilder);

        PhaseCentersRangeModifier modifier = new PhaseCentersRangeModifier(new FrequencyPattern(stationMeanPosition,
                                                                                                stationPCV),
                                                                           new FrequencyPattern(satelliteMeanPosition,
                                                                                                satellitePCV));
        for (int i = 0; i < spacecraftCenteredMeasurements.size(); ++i) {
            Range sr = (Range) spacecraftCenteredMeasurements.get(i);
            sr.addModifier(modifier);
            EstimatedMeasurement<Range> estimated = sr.estimate(0, 0, new SpacecraftState[] { p3.propagate(sr.getDate()) });
            Range ar = (Range) antennaCenteredMeasurements.get(i);
            Assertions.assertEquals(0.0, sr.getDate().durationFrom(ar.getDate()), 1.0e-8);
            Assertions.assertEquals(ar.getObservedValue()[0], estimated.getEstimatedValue()[0], 2.3e-7);
        }

    }

}


