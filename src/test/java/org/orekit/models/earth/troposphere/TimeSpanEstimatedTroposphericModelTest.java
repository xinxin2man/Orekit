/* Copyright 2002-2022 CS GROUP
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

import java.util.List;

import org.hipparchus.Field;
import org.hipparchus.CalculusFieldElement;
import org.hipparchus.analysis.differentiation.DSFactory;
import org.hipparchus.analysis.differentiation.DerivativeStructure;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.Decimal64Field;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.Precision;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.attitudes.Attitude;
import org.orekit.bodies.FieldGeodeticPoint;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.errors.OrekitException;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.FieldKeplerianOrbit;
import org.orekit.orbits.FieldOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.ParameterDriver;
import org.orekit.utils.ParameterDriversList;

public class TimeSpanEstimatedTroposphericModelTest {

    @BeforeClass
    public static void setUpGlobal() {
        Utils.setDataRoot("atmosphere");
    }

    @Before
    public void setUp() throws OrekitException {
        Utils.setDataRoot("regular-data:potential/shm-format");
    }

    @Test
    public void testFixedHeight() {
        final AbsoluteDate date = new AbsoluteDate();
        GeodeticPoint point = new GeodeticPoint(FastMath.toRadians(45.0), FastMath.toRadians(45.0), 350.0);
        MappingFunction mapping = new NiellMappingFunctionModel();
        EstimatedTroposphericModel model = new EstimatedTroposphericModel(mapping, 2.0);
        DiscreteTroposphericModel  timeSpanModel = new TimeSpanEstimatedTroposphericModel(model);
        double lastDelay = Double.MAX_VALUE;
        // delay shall decline with increasing elevation angle
        for (double elev = 10d; elev < 90d; elev += 8d) {
            final double delay = timeSpanModel.pathDelay(FastMath.toRadians(elev), point, timeSpanModel.getParameters(date), date);
            Assert.assertTrue(Precision.compareTo(delay, lastDelay, 1.0e-6) < 0);
            lastDelay = delay;
        }
    }

    @Test
    public void testDelay() {
        final double elevation = 10d;
        final double height = 100d;
        final AbsoluteDate date = new AbsoluteDate();
        GeodeticPoint point = new GeodeticPoint(FastMath.toRadians(45.0), FastMath.toRadians(45.0), height);
        MappingFunction mapping = new NiellMappingFunctionModel();
        EstimatedTroposphericModel model = new EstimatedTroposphericModel(mapping, 2.0);
        DiscreteTroposphericModel  timeSpanModel = new TimeSpanEstimatedTroposphericModel(model);
        final double path = timeSpanModel.pathDelay(FastMath.toRadians(elevation), point, timeSpanModel.getParameters(date), date);
        Assert.assertTrue(Precision.compareTo(path, 20d, 1.0e-6) < 0);
        Assert.assertTrue(Precision.compareTo(path, 0d, 1.0e-6) > 0);
    }

    @Test
    public void testStateDerivativesGMF() {
        final double latitude     = FastMath.toRadians(45.0);
        final double longitude    = FastMath.toRadians(45.0);
        GeodeticPoint point = new GeodeticPoint(latitude, longitude, 0.0);
        final MappingFunction gmf = new GlobalMappingFunctionModel();
        doTestDelayStateDerivatives(gmf, point, 4.7e-9);
    }

    @Test
    public void testStateDerivativesNMF() {
        final double latitude     = FastMath.toRadians(45.0);
        final double longitude    = FastMath.toRadians(45.0);
        GeodeticPoint point = new GeodeticPoint(latitude, longitude, 0.0);
        final MappingFunction nmf = new NiellMappingFunctionModel();
        doTestDelayStateDerivatives(nmf, point, 4.4e-9);
    }

    private void doTestDelayStateDerivatives(final MappingFunction func, final GeodeticPoint point, final double tolerance) {

        // Body: earth
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                                            Constants.WGS84_EARTH_FLATTENING,
                                                            FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        // Topocentric frame
        final TopocentricFrame baseFrame = new TopocentricFrame(earth, point, "topo");

        // Station
        final GroundStation station = new GroundStation(baseFrame);
        
        // Tropospheric model
        EstimatedTroposphericModel model = new EstimatedTroposphericModel(func, 2.0);
        DiscreteTroposphericModel  timeSpanModel = new TimeSpanEstimatedTroposphericModel(model);

        // Derivative Structure
        final DSFactory factory = new DSFactory(6, 1);
        final DerivativeStructure a0       = factory.variable(0, 24464560.0);
        final DerivativeStructure e0       = factory.variable(1, 0.05);
        final DerivativeStructure i0       = factory.variable(2, 0.122138);
        final DerivativeStructure pa0      = factory.variable(3, 3.10686);
        final DerivativeStructure raan0    = factory.variable(4, 1.00681);
        final DerivativeStructure anomaly0 = factory.variable(5, 0.048363);
        final Field<DerivativeStructure> field = a0.getField();
        final DerivativeStructure zero = field.getZero();

        // Field Date
        final FieldAbsoluteDate<DerivativeStructure> dsDate = new FieldAbsoluteDate<>(field);
        // Field Orbit
        final Frame frame = FramesFactory.getEME2000();
        final FieldOrbit<DerivativeStructure> dsOrbit = new FieldKeplerianOrbit<>(a0, e0, i0, pa0, raan0, anomaly0,
                                                                                  PositionAngle.MEAN, frame,
                                                                                  dsDate, zero.add(3.9860047e14));
        // Field State
        final FieldSpacecraftState<DerivativeStructure> dsState = new FieldSpacecraftState<>(dsOrbit);

        // Initial satellite elevation
        final FieldVector3D<DerivativeStructure> position = dsState.getPVCoordinates().getPosition();
        final DerivativeStructure dsElevation = baseFrame.getElevation(position, frame, dsDate);

        // Set drivers reference date
        for (final ParameterDriver driver : timeSpanModel.getParametersDrivers()) {
            driver.setReferenceDate(dsDate.toAbsoluteDate());
        }

        // Compute Delay with state derivatives
        final FieldGeodeticPoint<DerivativeStructure> dsPoint = new FieldGeodeticPoint<>(zero.add(point.getLatitude()), zero.add(point.getLongitude()), zero.add(point.getAltitude()));
        final DerivativeStructure delay = timeSpanModel.pathDelay(dsElevation, dsPoint, timeSpanModel.getParameters(field, dsDate), dsDate);

        final double[] compDeriv = delay.getAllDerivatives();

        // Field -> non-field
        final Orbit orbit = dsOrbit.toOrbit();
        final SpacecraftState state = dsState.toSpacecraftState();

        // Finite differences for reference values
        final double[][] refDeriv = new double[1][6];
        final OrbitType orbitType = OrbitType.KEPLERIAN;
        final PositionAngle angleType = PositionAngle.MEAN;
        double dP = 0.001;
        double[] steps = NumericalPropagator.tolerances(1000000 * dP, orbit, orbitType)[0];
        for (int i = 0; i < 6; i++) {
            SpacecraftState stateM4 = shiftState(state, orbitType, angleType, -4 * steps[i], i);
            final Vector3D positionM4 = stateM4.getPVCoordinates().getPosition();
            final double elevationM4  = station.getBaseFrame().getElevation(positionM4, stateM4.getFrame(), stateM4.getDate());
            double  delayM4 = timeSpanModel.pathDelay(elevationM4, point, timeSpanModel.getParameters(stateM4.getDate()), stateM4.getDate());
            
            SpacecraftState stateM3 = shiftState(state, orbitType, angleType, -3 * steps[i], i);
            final Vector3D positionM3 = stateM3.getPVCoordinates().getPosition();
            final double elevationM3  = station.getBaseFrame().getElevation(positionM3, stateM3.getFrame(), stateM3.getDate());
            double  delayM3 = timeSpanModel.pathDelay(elevationM3, point, timeSpanModel.getParameters(stateM3.getDate()), stateM3.getDate());
            
            SpacecraftState stateM2 = shiftState(state, orbitType, angleType, -2 * steps[i], i);
            final Vector3D positionM2 = stateM2.getPVCoordinates().getPosition();
            final double elevationM2  = station.getBaseFrame().getElevation(positionM2, stateM2.getFrame(), stateM2.getDate());
            double  delayM2 = timeSpanModel.pathDelay(elevationM2, point, timeSpanModel.getParameters(stateM2.getDate()), stateM2.getDate());
 
            SpacecraftState stateM1 = shiftState(state, orbitType, angleType, -1 * steps[i], i);
            final Vector3D positionM1 = stateM1.getPVCoordinates().getPosition();
            final double elevationM1  = station.getBaseFrame().getElevation(positionM1, stateM1.getFrame(), stateM1.getDate());
            double  delayM1 = timeSpanModel.pathDelay(elevationM1, point, timeSpanModel.getParameters(stateM1.getDate()), stateM1.getDate());
           
            SpacecraftState stateP1 = shiftState(state, orbitType, angleType, 1 * steps[i], i);
            final Vector3D positionP1 = stateP1.getPVCoordinates().getPosition();
            final double elevationP1  = station.getBaseFrame().getElevation(positionP1, stateP1.getFrame(), stateP1.getDate());
            double  delayP1 = timeSpanModel.pathDelay(elevationP1, point, timeSpanModel.getParameters(stateP1.getDate()), stateP1.getDate());
            
            SpacecraftState stateP2 = shiftState(state, orbitType, angleType, 2 * steps[i], i);
            final Vector3D positionP2 = stateP2.getPVCoordinates().getPosition();
            final double elevationP2  = station.getBaseFrame().getElevation(positionP2, stateP2.getFrame(), stateP2.getDate());
            double  delayP2 = timeSpanModel.pathDelay(elevationP2, point, timeSpanModel.getParameters(stateP2.getDate()), stateP2.getDate());
            
            SpacecraftState stateP3 = shiftState(state, orbitType, angleType, 3 * steps[i], i);
            final Vector3D positionP3 = stateP3.getPVCoordinates().getPosition();
            final double elevationP3  = station.getBaseFrame().getElevation(positionP3, stateP3.getFrame(), stateP3.getDate());
            double  delayP3 = timeSpanModel.pathDelay(elevationP3, point, timeSpanModel.getParameters(stateP3.getDate()), stateP3.getDate());
            
            SpacecraftState stateP4 = shiftState(state, orbitType, angleType, 4 * steps[i], i);
            final Vector3D positionP4 = stateP4.getPVCoordinates().getPosition();
            final double elevationP4  = station.getBaseFrame().getElevation(positionP4, stateP4.getFrame(), stateP4.getDate());
            double  delayP4 = timeSpanModel.pathDelay(elevationP4, point, timeSpanModel.getParameters(stateP4.getDate()), stateP4.getDate());
            
            fillJacobianColumn(refDeriv, i, orbitType, angleType, steps[i],
                               delayM4, delayM3, delayM2, delayM1,
                               delayP1, delayP2, delayP3, delayP4);
        }

        for (int i = 0; i < 6; i++) {
            Assert.assertEquals(compDeriv[i + 1], refDeriv[0][i], tolerance);
        }
    }

    @Test
    public void testDelayParameterDerivative() {
        doTestParametersDerivatives(EstimatedTroposphericModel.TOTAL_ZENITH_DELAY, 5.0e-15);
    }

    private void doTestParametersDerivatives(String parameterName, double tolerance) {

        // Geodetic point
        final double latitude     = FastMath.toRadians(45.0);
        final double longitude    = FastMath.toRadians(45.0);
        final double height       = 0.0;
        final GeodeticPoint point = new GeodeticPoint(latitude, longitude, height);
        // Body: earth
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                                            Constants.WGS84_EARTH_FLATTENING,
                                                            FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        // Topocentric frame
        final TopocentricFrame baseFrame = new TopocentricFrame(earth, point, "topo");
        
        // Tropospheric model
        final MappingFunction gmf = new GlobalMappingFunctionModel();
        final DiscreteTroposphericModel model = new EstimatedTroposphericModel(gmf, 5.0);

        // Set Parameter Driver
        for (final ParameterDriver driver : model.getParametersDrivers()) {
            driver.setValue(driver.getReferenceValue(), null);
            driver.setSelected(driver.getName().equals(parameterName));
        }

        // Count the required number of parameters
        int nbParams = 0;
        for (final ParameterDriver driver : model.getParametersDrivers()) {
            if (driver.isSelected()) {
                ++nbParams;
            }
        }

        // Derivative Structure
        final DSFactory factory = new DSFactory(6 + nbParams, 1);
        final DerivativeStructure a0       = factory.variable(0, 24464560.0);
        final DerivativeStructure e0       = factory.variable(1, 0.05);
        final DerivativeStructure i0       = factory.variable(2, 0.122138);
        final DerivativeStructure pa0      = factory.variable(3, 3.10686);
        final DerivativeStructure raan0    = factory.variable(4, 1.00681);
        final DerivativeStructure anomaly0 = factory.variable(5, 0.048363);
        final Field<DerivativeStructure> field = a0.getField();
        final DerivativeStructure zero = field.getZero();

        // Field Date
        final FieldAbsoluteDate<DerivativeStructure> dsDate = new FieldAbsoluteDate<>(field, 2018, 11, 19, 18, 0, 0.0,
                                                                                      TimeScalesFactory.getUTC());

        // Set drivers reference date
        for (final ParameterDriver driver : model.getParametersDrivers()) {
            driver.setReferenceDate(dsDate.toAbsoluteDate());
        }

        // Field Orbit
        final Frame frame = FramesFactory.getEME2000();
        final FieldOrbit<DerivativeStructure> dsOrbit = new FieldKeplerianOrbit<>(a0, e0, i0, pa0, raan0, anomaly0,
                                                                                  PositionAngle.MEAN, frame,
                                                                                  dsDate, zero.add(3.9860047e14));

        // Field State
        final FieldSpacecraftState<DerivativeStructure> dsState = new FieldSpacecraftState<>(dsOrbit);

        // Initial satellite elevation
        final FieldVector3D<DerivativeStructure> position = dsState.getPVCoordinates().getPosition();
        final DerivativeStructure dsElevation = baseFrame.getElevation(position, frame, dsState.getDate());

        // Add parameter as a variable
        final List<ParameterDriver> drivers = model.getParametersDrivers();
        final DerivativeStructure[] parameters = new DerivativeStructure[drivers.size()];
        int index = 6;
        for (int i = 0; i < drivers.size(); ++i) {
            parameters[i] = drivers.get(i).isSelected() ?
                            factory.variable(index++, drivers.get(i).getValue(null)) :
                            factory.constant(drivers.get(i).getValue(null));
        }

        // Compute delay state derivatives
        final FieldGeodeticPoint<DerivativeStructure> dsPoint = new FieldGeodeticPoint<>(zero.add(point.getLatitude()), zero.add(point.getLongitude()), zero.add(point.getAltitude()));
        final DerivativeStructure delay = model.pathDelay(dsElevation, dsPoint, parameters, dsState.getDate());

        final double[] compDeriv = delay.getAllDerivatives(); 

        // Field -> non-field
        final SpacecraftState state = dsState.toSpacecraftState();
        final double elevation = dsElevation.getReal();

        // Finite differences for reference values
        final double[][] refDeriv = new double[1][1];
        ParameterDriversList bound = new ParameterDriversList();
        for (final ParameterDriver driver : model.getParametersDrivers()) {
            if (driver.getName().equals(parameterName)) {
                driver.setSelected(true);
                bound.add(driver);
            } else {
                driver.setSelected(false);
            }
        }
        ParameterDriver selected = bound.getDrivers().get(0);
        double p0 = selected.getReferenceValue();
        double h  = selected.getScale();

        final OrbitType orbitType = OrbitType.KEPLERIAN;
        final PositionAngle angleType = PositionAngle.MEAN;

        selected.setValue(p0 - 4 * h, state.getDate());
        double  delayM4 = model.pathDelay(elevation, point, model.getParameters(state.getDate()), state.getDate());
        
        selected.setValue(p0 - 3 * h, state.getDate());
        double  delayM3 = model.pathDelay(elevation, point, model.getParameters(state.getDate()), state.getDate());
        
        selected.setValue(p0 - 2 * h, state.getDate());
        double  delayM2 = model.pathDelay(elevation, point, model.getParameters(state.getDate()), state.getDate());

        selected.setValue(p0 - 1 * h, state.getDate());
        double  delayM1 = model.pathDelay(elevation, point, model.getParameters(state.getDate()), state.getDate());

        selected.setValue(p0 + 1 * h, state.getDate());
        double  delayP1 = model.pathDelay(elevation, point, model.getParameters(state.getDate()), state.getDate());

        selected.setValue(p0 + 2 * h, state.getDate());
        double  delayP2 = model.pathDelay(elevation, point, model.getParameters(state.getDate()), state.getDate());

        selected.setValue(p0 + 3 * h, state.getDate());
        double  delayP3 = model.pathDelay(elevation, point, model.getParameters(state.getDate()), state.getDate());

        selected.setValue(p0 + 4 * h, state.getDate());
        double  delayP4 = model.pathDelay(elevation, point, model.getParameters(state.getDate()), state.getDate());
            
        fillJacobianColumn(refDeriv, 0, orbitType, angleType, h,
                           delayM4, delayM3, delayM2, delayM1,
                           delayP1, delayP2, delayP3, delayP4);

        Assert.assertEquals(compDeriv[7], refDeriv[0][0], tolerance);

    }

    @Test
    public void testComparisonWithEstimatedModel() {
        final AbsoluteDate date = new AbsoluteDate();
        MappingFunction mapping = new NiellMappingFunctionModel();
        EstimatedTroposphericModel estimatedModel = new EstimatedTroposphericModel(mapping, 2.0);
        DiscreteTroposphericModel  timeSpanModel  = new TimeSpanEstimatedTroposphericModel(estimatedModel);
        final double elevation = 45.0;
        final double height    = 100.0;
        final double[] estimatedParameters = estimatedModel.getParameters(date);
        final double[] timeSpanParameters = estimatedModel.getParameters(date);
        GeodeticPoint point = new GeodeticPoint(FastMath.toRadians(45.0), FastMath.toRadians(45.0), height);

        Assert.assertEquals(estimatedModel.pathDelay(elevation, point, estimatedParameters, date),
                            timeSpanModel.pathDelay(elevation, point, timeSpanParameters, date),
                            Double.MIN_VALUE);
    }

    @Test
    public void testFieldComparisonWithEstimatedModel() {
        doTestFieldComparisonWithEstimatedModel(Decimal64Field.getInstance());
    }

    private <T extends CalculusFieldElement<T>> void doTestFieldComparisonWithEstimatedModel(final Field<T> field) {
        final T zero = field.getZero();
        final FieldAbsoluteDate<T> date = new FieldAbsoluteDate<>(field);
        MappingFunction mapping = new NiellMappingFunctionModel();
        EstimatedTroposphericModel estimatedModel = new EstimatedTroposphericModel(mapping, 2.0);
        DiscreteTroposphericModel  timeSpanModel  = new TimeSpanEstimatedTroposphericModel(estimatedModel);
        final T elevation = zero.add(45.0);
        final T height    = zero.add(100.0);
        final T[] estimatedParameters = estimatedModel.getParameters(field, date);
        final T[] timeSpanParameters = estimatedModel.getParameters(field, date);
        final FieldGeodeticPoint<T> dsPoint = new FieldGeodeticPoint<>(zero.add(FastMath.toRadians(45.0)), zero.add(FastMath.toRadians(45.0)), height);

        Assert.assertEquals(estimatedModel.pathDelay(elevation, dsPoint, estimatedParameters, date).getReal(),
                            timeSpanModel.pathDelay(elevation, dsPoint, timeSpanParameters, date).getReal(),
                            Double.MIN_VALUE);
    }

    private SpacecraftState shiftState(SpacecraftState state, OrbitType orbitType, PositionAngle angleType,
                                       double delta, int column) {

        double[][] array = stateToArray(state, orbitType, angleType, true);
        array[0][column] += delta;

        return arrayToState(array, orbitType, angleType, state.getFrame(), state.getDate(),
                            state.getMu(), state.getAttitude());

    }

    private double[][] stateToArray(SpacecraftState state, OrbitType orbitType, PositionAngle angleType,
                                  boolean withMass) {
        double[][] array = new double[2][withMass ? 7 : 6];
        orbitType.mapOrbitToArray(state.getOrbit(), angleType, array[0], array[1]);
        if (withMass) {
            array[0][6] = state.getMass();
        }
        return array;
    }

    private SpacecraftState arrayToState(double[][] array, OrbitType orbitType, PositionAngle angleType,
                                         Frame frame, AbsoluteDate date, double mu,
                                         Attitude attitude) {
        Orbit orbit = orbitType.mapArrayToOrbit(array[0], array[1], angleType, date, mu, frame);
        return (array.length > 6) ?
               new SpacecraftState(orbit, attitude) :
               new SpacecraftState(orbit, attitude, array[0][6]);
    }

    private void fillJacobianColumn(double[][] jacobian, int column,
                                    OrbitType orbitType, PositionAngle angleType, double h,
                                    double sM4h, double sM3h,
                                    double sM2h, double sM1h,
                                    double sP1h, double sP2h,
                                    double sP3h, double sP4h) {

        jacobian[0][column] = ( -3 * (sP4h - sM4h) +
                                32 * (sP3h - sM3h) -
                               168 * (sP2h - sM2h) +
                               672 * (sP1h - sM1h)) / (840 * h);
    }

}
