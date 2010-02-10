/* Copyright 2002-2010 CS Communication & Systèmes
 * Licensed to CS Communication & Systèmes (CS) under one or more
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
package org.orekit.attitudes;


import org.apache.commons.math.geometry.Rotation;
import org.apache.commons.math.geometry.Vector3D;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;

public class SpinStabilizedTest {

    @Test
    public void testBBQMode() throws OrekitException {
        PVCoordinatesProvider sun = CelestialBodyFactory.getSun();
        AbsoluteDate date = new AbsoluteDate(new DateComponents(1970, 01, 01),
                                             new TimeComponents(3, 25, 45.6789),
                                             TimeScalesFactory.getUTC());
        double rate = 2.0 * Math.PI / (12 * 60);
        AttitudeLaw bbq =
            new SpinStabilized(new CelestialBodyPointed(FramesFactory.getEME2000(), sun, Vector3D.PLUS_K,
                                     Vector3D.PLUS_I, Vector3D.PLUS_K),
                               date, Vector3D.PLUS_K, rate);
        PVCoordinates pv =
            new PVCoordinates(new Vector3D(28812595.32012577, 5948437.4640250085, 0),
                              new Vector3D(0, 0, 3680.853673522056));
        Attitude attitude = bbq.getAttitude(new KeplerianOrbit(pv, FramesFactory.getEME2000(), date, 3.986004415e14));
        Vector3D xDirection = attitude.getRotation().applyInverseTo(Vector3D.PLUS_I);
        Assert.assertEquals(Math.atan(1.0 / 5000.0),
                     Vector3D.angle(xDirection, sun.getPVCoordinates(date, FramesFactory.getEME2000()).getPosition()),
                     1.0e-15);
        Assert.assertEquals(rate, attitude.getSpin().getNorm(), 1.0e-6);

    }

    @Test
    public void testSpin() throws OrekitException {

        AbsoluteDate date = new AbsoluteDate(new DateComponents(1970, 01, 01),
                                             new TimeComponents(3, 25, 45.6789),
                                             TimeScalesFactory.getUTC());
        double rate = 2.0 * Math.PI / (12 * 60);
        AttitudeLaw law =
            new SpinStabilized(new CelestialBodyPointed(FramesFactory.getEME2000(),
                                                        CelestialBodyFactory.getSun(),
                                                        Vector3D.PLUS_K,
                                     Vector3D.PLUS_I, Vector3D.PLUS_K),
                               date, Vector3D.PLUS_K, rate);
        KeplerianOrbit orbit =
            new KeplerianOrbit(7178000.0, 1.e-4, Math.toRadians(50.),
                              Math.toRadians(10.), Math.toRadians(20.),
                              Math.toRadians(30.), KeplerianOrbit.MEAN_ANOMALY, 
                              FramesFactory.getEME2000(), date, 3.986004415e14);

        Propagator propagator = new KeplerianPropagator(orbit, law);

        double h = 100.0;
        SpacecraftState sMinus = propagator.propagate(date.shiftedBy(-h));
        SpacecraftState s0     = propagator.propagate(date);
        SpacecraftState sPlus  = propagator.propagate(date.shiftedBy(h));

        // compute spin axis using finite differences
        Rotation rMinus = sMinus.getAttitude().getRotation();
        Rotation rPlus  = sPlus.getAttitude().getRotation();
        Rotation dr     = rPlus.applyTo(rMinus.revert());
        double period   = 4 * Math.PI * h / dr.getAngle();

        Vector3D spin0 = s0.getAttitude().getSpin();
        Assert.assertEquals(period, 2 * Math.PI / spin0.getNorm(), 0.05);
        Assert.assertEquals(0.0, Math.toDegrees(Vector3D.angle(dr.getAxis(), spin0)), 8.0e-4);

    }

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data");
    }

}

