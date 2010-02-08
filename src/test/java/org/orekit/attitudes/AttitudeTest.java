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
import org.junit.Test;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FramesFactory;

public class AttitudeTest {

    @Test
    public void testZeroRate() throws OrekitException {
        Attitude attitude = new Attitude(FramesFactory.getEME2000(),
                                         new Rotation(0.48, 0.64, 0.36, 0.48, false),
                                         Vector3D.ZERO);
        Assert.assertEquals(Vector3D.ZERO, attitude.getSpin());
        double dt = 10.0;
        Attitude shifted = attitude.shiftedBy(dt);
        Assert.assertEquals(Vector3D.ZERO, shifted.getSpin());
        Assert.assertEquals(attitude.getRotation(), shifted.getRotation());
    }

    @Test
    public void testShift() throws OrekitException {
        double rate = 2 * Math.PI / (12 * 60);
        Attitude attitude = new Attitude(FramesFactory.getEME2000(),
                                         Rotation.IDENTITY,
                                         new Vector3D(rate, Vector3D.PLUS_K));
        Assert.assertEquals(rate, attitude.getSpin().getNorm(), 1.0e-10);
        double dt = 10.0;
        double alpha = rate * dt;
        Attitude shifted = attitude.shiftedBy(dt);
        Assert.assertEquals(rate, shifted.getSpin().getNorm(), 1.0e-10);
        Assert.assertEquals(alpha, Rotation.distance(attitude.getRotation(), shifted.getRotation()), 1.0e-10);

        Vector3D xSat = shifted.getRotation().applyInverseTo(Vector3D.PLUS_I);
        Assert.assertEquals(0.0, xSat.subtract(new Vector3D(Math.cos(alpha), Math.sin(alpha), 0)).getNorm(), 1.0e-10);
        Vector3D ySat = shifted.getRotation().applyInverseTo(Vector3D.PLUS_J);
        Assert.assertEquals(0.0, ySat.subtract(new Vector3D(-Math.sin(alpha), Math.cos(alpha), 0)).getNorm(), 1.0e-10);
        Vector3D zSat = shifted.getRotation().applyInverseTo(Vector3D.PLUS_K);
        Assert.assertEquals(0.0, zSat.subtract(Vector3D.PLUS_K).getNorm(), 1.0e-10);

    }

    @Test
    public void testSpin() throws OrekitException {
        double rate = 2 * Math.PI / (12 * 60);
        Attitude attitude = new Attitude(FramesFactory.getEME2000(),
                                       new Rotation(0.48, 0.64, 0.36, 0.48, false),
                                       new Vector3D(rate, Vector3D.PLUS_K));
        Assert.assertEquals(rate, attitude.getSpin().getNorm(), 1.0e-10);
        double dt = 10.0;
        Attitude shifted = attitude.shiftedBy(dt);
        Assert.assertEquals(rate, shifted.getSpin().getNorm(), 1.0e-10);
        Assert.assertEquals(rate * dt, Rotation.distance(attitude.getRotation(), shifted.getRotation()), 1.0e-10);

        Vector3D forward = Attitude.estimateSpin(attitude.getRotation(), shifted.getRotation(), dt);
        Assert.assertEquals(0.0, forward.subtract(attitude.getSpin()).getNorm(), 1.0e-10);

        Vector3D reversed = Attitude.estimateSpin(shifted.getRotation(), attitude.getRotation(), dt);
        Assert.assertEquals(0.0, reversed.add(attitude.getSpin()).getNorm(), 1.0e-10);

    }

}

