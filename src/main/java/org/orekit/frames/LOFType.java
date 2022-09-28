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
package org.orekit.frames;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.Field;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.FieldPVCoordinates;
import org.orekit.utils.PVCoordinates;

/**
 * Enumerate for different types of Local Orbital Frames.
 *
 * @author Luc Maisonobe
 */
public enum LOFType {

    /** Constant for TNW frame
     * (X axis aligned with velocity, Z axis aligned with orbital momentum).
     * <p>
     * The axes of this frame are parallel to the axes of the {@link #VNC}
     * and {@link #NTW} frames:
     * <ul>
     *   <li>X<sub>TNW</sub> =  X<sub>VNC</sub> =  Y<sub>NTW</sub></li>
     *   <li>Y<sub>TNW</sub> = -Z<sub>VNC</sub> = -X<sub>NTW</sub></li>
     *   <li>Z<sub>TNW</sub> =  Y<sub>VNC</sub> =  Z<sub>NTW</sub></li>
     * </ul>
     *
     * @see #VNC
     * @see #NTW
     */
    TNW {
        /** {@inheritDoc} */
        public Rotation rotationFromInertial(final PVCoordinates pv) {
            return new Rotation(pv.getVelocity(), pv.getMomentum(),
                                Vector3D.PLUS_I, Vector3D.PLUS_K);
        }

        @Override public Rotation rotationFromLOFType(final LOFType fromLOF, final PVCoordinates pv) {
            return rotationFromLOFInToLOFOut(fromLOF, TNW, pv);
        }

        /** {@inheritDoc} */
        public <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(final Field<T> field,
                                                                                         final FieldPVCoordinates<T> pv) {
            return new FieldRotation<>(pv.getVelocity(), pv.getMomentum(),
                                       new FieldVector3D<>(field, Vector3D.PLUS_I),
                                       new FieldVector3D<>(field, Vector3D.PLUS_K));
        }

    },

    /** Constant for QSW frame
     * (X axis aligned with position, Z axis aligned with orbital momentum).
     * <p>
     * This frame is also known as the {@link #LVLH} frame, both constants are equivalent.
     * </p>
     * <p>
     * The axes of these frames are parallel to the axes of the {@link #VVLH} frame:
     * <ul>
     *   <li>X<sub>QSW/LVLH</sub> = -Z<sub>VVLH</sub></li>
     *   <li>Y<sub>QSW/LVLH</sub> =  X<sub>VVLH</sub></li>
     *   <li>Z<sub>QSW/LVLH</sub> = -Y<sub>VVLH</sub></li>
     * </ul>
     *
     * @see #LVLH
     * @see #VVLH
     */
    QSW {
        /** {@inheritDoc} */
        public Rotation rotationFromInertial(final PVCoordinates pv) {
            return new Rotation(pv.getPosition(), pv.getMomentum(),
                                Vector3D.PLUS_I, Vector3D.PLUS_K);
        }

        @Override public Rotation rotationFromLOFType(final LOFType fromLOF, final PVCoordinates pv) {

            final Vector3D position = pv.getPosition();
            final Vector3D velocity = pv.getVelocity();
            final Vector3D momentum = pv.getMomentum();

            switch (fromLOF) {
                case EQW:
                    final Vector3D ascendingNode = new Vector3D(-momentum.getY(), momentum.getX(), 0);
                    return new Rotation(ascendingNode, Vector3D.crossProduct(momentum, ascendingNode),
                                        position, Vector3D.crossProduct(momentum, position));
                case NTW:
                    final Vector3D transverse = velocity.crossProduct(momentum);

                    final double cosAngle =
                            transverse.dotProduct(position) / (transverse.getNorm() * position.getNorm());

                    final double angle = FastMath.acos(cosAngle);

                    final double[][] rotationMatrixData = new double[][] {
                            { FastMath.cos(angle), FastMath.sin(angle), 0 },
                            { -FastMath.sin(angle), FastMath.cos(angle), 0 },
                            { 0, 0, 1 },
                    };

                    return new Rotation(rotationMatrixData, 1e-15);

                case VNC:
                    return new Rotation(velocity, velocity.crossProduct(momentum),
                                        position, momentum);
                case LVLH:
                    return Rotation.IDENTITY;
                case VVLH:
                    return new Rotation(position.negate(), position.negate().crossProduct(momentum.negate()),
                                        position, momentum);
                case LVLH_CCSDS:
                    return new Rotation(momentum.negate().crossProduct(position), position,
                                        position, momentum);
                case TNW:
                    return new Rotation(velocity, momentum.crossProduct(velocity),
                                        position, momentum.crossProduct(position));
                default:
                    return Rotation.IDENTITY;
            }
        }

        /** {@inheritDoc} */
        public <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(final Field<T> field,
                                                                                         final FieldPVCoordinates<T> pv) {
            return new FieldRotation<>(pv.getPosition(), pv.getMomentum(),
                                       new FieldVector3D<>(field, Vector3D.PLUS_I),
                                       new FieldVector3D<>(field, Vector3D.PLUS_K));
        }

    },

    /** Constant for Local Vertical, Local Horizontal frame
     * (X axis aligned with position, Z axis aligned with orbital momentum).
     * <p>
     * BEWARE! Depending on the background (software used, textbook, community),
     * different incompatible definitions for LVLH are used. This one is consistent
     * with Vallado's book and with AGI's STK. However CCSDS standard, Wertz, and
     * a.i. solutions' FreeFlyer use another definition (see {@link #LVLH_CCSDS}).
     * </p>
     * <p>
     * This frame is also known as the {@link #QSW} frame, both constants are equivalent.
     * </p>
     * <p>
     * The axes of these frames are parallel to the axes of the {@link #LVLH_CCSDS} frame:
     * <ul>
     *   <li>X<sub>LVLH/QSW</sub> = -Z<sub>LVLH_CCSDS</sub></li>
     *   <li>Y<sub>LVLH/QSW</sub> =  X<sub>LVLH_CCSDS</sub></li>
     *   <li>Z<sub>LVLH/QSW</sub> = -Y<sub>LVLH_CCSDS</sub></li>
     * </ul>
     *
     * @see #QSW
     * @see #VVLH
     */
    LVLH {
        /** {@inheritDoc} */
        public Rotation rotationFromInertial(final PVCoordinates pv) {
            return new Rotation(pv.getPosition(), pv.getMomentum(),
                                Vector3D.PLUS_I, Vector3D.PLUS_K);
        }

        @Override public Rotation rotationFromLOFType(final LOFType fromLOF, final PVCoordinates pv) {
            return rotationFromLOFInToLOFOut(fromLOF, LVLH, pv);
        }

        /** {@inheritDoc} */
        public <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(final Field<T> field,
                                                                                         final FieldPVCoordinates<T> pv) {
            return new FieldRotation<>(pv.getPosition(), pv.getMomentum(),
                                       new FieldVector3D<>(field, Vector3D.PLUS_I),
                                       new FieldVector3D<>(field, Vector3D.PLUS_K));
        }

    },

    /** Constant for Local Vertical, Local Horizontal frame as defined by CCSDS
     * (Z axis aligned with opposite of position, Y axis aligned with opposite of orbital momentum).
     * <p>
     * BEWARE! Depending on the background (software used, textbook, community),
     * different incompatible definitions for LVLH are used. This one is consistent
     * with CCSDS standard, Wertz, and a.i. solutions' FreeFlyer. However Vallado's
     * book and with AGI's STK use another definition (see {@link #LVLH}).
     * </p>
     * <p>
     * The axes of this frame are parallel to the axes of both the {@link #QSW} and {@link #LVLH} frames:
     * <ul>
     *   <li>X<sub>LVLH_CCSDS/VVLH</sub> =  Y<sub>QSW/LVLH</sub></li>
     *   <li>Y<sub>LVLH_CCSDS/VVLH</sub> = -Z<sub>QSW/LVLH</sub></li>
     *   <li>Z<sub>LVLH_CCSDS/VVLH</sub> = -X<sub>QSW/LVLH</sub></li>
     * </ul>
     *
     * @see #QSW
     * @see #LVLH
     * @since 11.0
     */
    LVLH_CCSDS {
        /** {@inheritDoc} */
        public Rotation rotationFromInertial(final PVCoordinates pv) {
            return new Rotation(pv.getPosition(), pv.getMomentum(),
                                Vector3D.MINUS_K, Vector3D.MINUS_J);
        }

        @Override public Rotation rotationFromLOFType(final LOFType fromLOF, final PVCoordinates pv) {
            return rotationFromLOFInToLOFOut(fromLOF, LVLH_CCSDS, pv);
        }

        /** {@inheritDoc} */
        public <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(final Field<T> field,
                                                                                         final FieldPVCoordinates<T> pv) {
            return new FieldRotation<>(pv.getPosition(), pv.getMomentum(),
                                       new FieldVector3D<>(field, Vector3D.MINUS_K),
                                       new FieldVector3D<>(field, Vector3D.MINUS_J));
        }

    },

    /** Constant for Vehicle Velocity, Local Horizontal frame
     * (Z axis aligned with opposite of position, Y axis aligned with opposite of orbital momentum).
     * <p>
     * This is another name for {@link #LVLH_CCSDS}, kept here for compatibility with STK.
     * </p>
     * <p>
     * Beware that the name is misleading: in the general case (i.e. not perfectly circular),
     * none of the axes is perfectly aligned with velocity! The preferred name for this
     * should be {@link #LVLH_CCSDS}.
     * </p>
     * <p>
     * The axes of this frame are parallel to the axes of both the {@link #QSW} and {@link #LVLH} frames:
     * <ul>
     *   <li>X<sub>LVLH_CCSDS/VVLH</sub> =  Y<sub>QSW/LVLH</sub></li>
     *   <li>Y<sub>LVLH_CCSDS/VVLH</sub> = -Z<sub>QSW/LVLH</sub></li>
     *   <li>Z<sub>LVLH_CCSDS/VVLH</sub> = -X<sub>QSW/LVLH</sub></li>
     * </ul>
     * @see #LVLH_CCSDS
     */
    VVLH {
        /** {@inheritDoc} */
        public Rotation rotationFromInertial(final PVCoordinates pv) {
            return LVLH_CCSDS.rotationFromInertial(pv);
        }

        @Override public Rotation rotationFromLOFType(final LOFType fromLOF, final PVCoordinates pv) {
            return rotationFromLOFInToLOFOut(fromLOF, VVLH, pv);
        }

        /** {@inheritDoc} */
        public <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(final Field<T> field,
                                                                                         final FieldPVCoordinates<T> pv) {
            return LVLH_CCSDS.rotationFromInertial(field, pv);
        }

    },

    /** Constant for Velocity - Normal - Co-normal frame
     * (X axis aligned with velocity, Y axis aligned with orbital momentum).
     * <p>
     * The axes of this frame are parallel to the axes of the {@link #TNW}
     * and {@link #NTW} frames:
     * <ul>
     *   <li>X<sub>VNC</sub> =  X<sub>TNW</sub> = Y<sub>NTW</sub></li>
     *   <li>Y<sub>VNC</sub> =  Z<sub>TNW</sub> = Z<sub>NTW</sub></li>
     *   <li>Z<sub>VNC</sub> = -Y<sub>TNW</sub> = X<sub>NTW</sub></li>
     * </ul>
     *
     * @see #TNW
     * @see #NTW
     */
    VNC {
        /** {@inheritDoc} */
        public Rotation rotationFromInertial(final PVCoordinates pv) {
            return new Rotation(pv.getVelocity(), pv.getMomentum(),
                                Vector3D.PLUS_I, Vector3D.PLUS_J);
        }

        @Override public Rotation rotationFromLOFType(final LOFType fromLOF, final PVCoordinates pv) {
            return rotationFromLOFInToLOFOut(fromLOF, VNC, pv);
        }

        @Override
        public <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(final Field<T> field,
                                                                                         final FieldPVCoordinates<T> pv) {
            return new FieldRotation<>(pv.getVelocity(), pv.getMomentum(),
                                       new FieldVector3D<>(field, Vector3D.PLUS_I),
                                       new FieldVector3D<>(field, Vector3D.PLUS_J));
        }

    },

    /** Constant for Equinoctial Coordinate System
     * (X axis aligned with ascending node, Z axis aligned with orbital momentum).
     * @since 11.0
     */
    EQW {
        /** {@inheritDoc} */
        public Rotation rotationFromInertial(final PVCoordinates pv) {
            final Vector3D m = pv.getMomentum();
            return new Rotation(new Vector3D(-m.getY(), m.getX(), 0), m,
                                Vector3D.PLUS_I, Vector3D.PLUS_J);
        }

        @Override public Rotation rotationFromLOFType(final LOFType fromLOF, final PVCoordinates pv) {
            return rotationFromLOFInToLOFOut(fromLOF, EQW, pv);
        }

        @Override
        public <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(final Field<T> field,
                                                                                         final FieldPVCoordinates<T> pv) {
            final FieldVector3D<T> m = pv.getMomentum();
            return new FieldRotation<>(new FieldVector3D<>(m.getY().negate(), m.getX(), field.getZero()),
                                       m,
                                       new FieldVector3D<>(field, Vector3D.PLUS_I),
                                       new FieldVector3D<>(field, Vector3D.PLUS_J));
        }

    },

    /** Constant for Transverse Velocity Normal coordinate system
     * (Y axis aligned with velocity, Z axis aligned with orbital momentum).
     * <p>
     * The axes of this frame are parallel to the axes of the {@link #TNW}
     * and {@link #VNC} frames:
     * <ul>
     *   <li>X<sub>NTW</sub> = -Y<sub>TNW</sub> = Z<sub>VNC</sub></li>
     *   <li>Y<sub>NTW</sub> =  X<sub>TNW</sub> = X<sub>VNC</sub></li>
     *   <li>Z<sub>NTW</sub> =  Z<sub>TNW</sub> = Y<sub>VNC</sub></li>
     * </ul>
     * @see #TNW
     * @see #VNC
     * @since 11.0
     */
    NTW {
        /** {@inheritDoc} */
        public Rotation rotationFromInertial(final PVCoordinates pv) {
            return new Rotation(pv.getVelocity(), pv.getMomentum(),
                                Vector3D.PLUS_J, Vector3D.PLUS_K);
        }

        @Override public Rotation rotationFromLOFType(final LOFType fromLOF, final PVCoordinates pv) {
            return rotationFromLOFInToLOFOut(fromLOF, NTW, pv);
        }

        @Override
        public <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(final Field<T> field,
                                                                                         final FieldPVCoordinates<T> pv) {
            return new FieldRotation<>(pv.getVelocity(), pv.getMomentum(),
                                       new FieldVector3D<>(field, Vector3D.PLUS_J),
                                       new FieldVector3D<>(field, Vector3D.PLUS_K));
        }

    };

    /**
     * Get the rotation from (common) input to output local orbital frame.
     *
     * @param in  input (common) local orbital frame
     * @param out output (common) local orbital frame
     * @param pv  position-velocity of the spacecraft in some inertial frame
     * @return rotation from (common) input to output local orbital frame.
     */
    public static Rotation rotationFromLOFInToLOFOut(final LOFType in, final LOFType out, final PVCoordinates pv) {

        final Rotation inToQSW = LOFType.QSW.rotationFromLOFType(in, pv);

        final Rotation QSWToOut = LOFType.QSW.rotationFromLOFType(out, pv).revert();

        return QSWToOut.compose(inToQSW, RotationConvention.VECTOR_OPERATOR);
    }

    /**
     * Get the transform from an inertial frame defining position-velocity and the local orbital frame.
     *
     * @param date current date
     * @param pv   position-velocity of the spacecraft in some inertial frame
     * @return transform from the frame where position-velocity are defined to local orbital frame
     */
    public Transform transformFromInertial(final AbsoluteDate date, final PVCoordinates pv) {

        // compute the translation part of the transform
        final Transform translation = new Transform(date, pv.negate());

        // compute the rotation part of the transform
        final Rotation r        = rotationFromInertial(pv);
        final Vector3D p        = pv.getPosition();
        final Vector3D momentum = pv.getMomentum();
        final Transform rotation =
                new Transform(date, r, new Vector3D(1.0 / p.getNormSq(), r.applyTo(momentum)));

        return new Transform(date, translation, rotation);

    }

    /**
     * Get the rotation from inertial frame to local orbital frame.
     * <p>
     * This rotation does not include any time derivatives. If first time derivatives (i.e. rotation rate) is needed as
     * well, the full {@link #transformFromInertial(AbsoluteDate, PVCoordinates) transformFromInertial} method must be
     * called and the complete rotation transform must be extracted from it.
     * </p>
     *
     * @param pv position-velocity of the spacecraft in some inertial frame
     * @return rotation from inertial frame to local orbital frame
     */
    public abstract Rotation rotationFromInertial(PVCoordinates pv);

    /**
     * Get the transform from an inertial frame defining position-velocity and the local orbital frame.
     *
     * @param date current date
     * @param pv   position-velocity of the spacecraft in some inertial frame
     * @param <T>  type of the fields elements
     * @return transform from the frame where position-velocity are defined to local orbital frame
     * @since 9.0
     */
    public <T extends CalculusFieldElement<T>> FieldTransform<T> transformFromInertial(final FieldAbsoluteDate<T> date,
                                                                                       final FieldPVCoordinates<T> pv) {

        // compute the translation part of the transform
        final FieldTransform<T> translation = new FieldTransform<>(date, pv.negate());

        // compute the rotation part of the transform
        final FieldRotation<T> r        = rotationFromInertial(date.getField(), pv);
        final FieldVector3D<T> p        = pv.getPosition();
        final FieldVector3D<T> momentum = pv.getMomentum();
        final FieldTransform<T> rotation =
                new FieldTransform<T>(date, r, new FieldVector3D<>(p.getNormSq().reciprocal(), r.applyTo(momentum)));

        return new FieldTransform<>(date, translation, rotation);

    }

    public abstract Rotation rotationFromLOFType(LOFType fromLOF, PVCoordinates pv);

    /** Get the rotation from inertial frame to local orbital frame.
     * <p>
     * This rotation does not include any time derivatives. If first
     * time derivatives (i.e. rotation rate) is needed as well, the full
     * {@link #transformFromInertial(FieldAbsoluteDate, FieldPVCoordinates) transformFromInertial}
     * method must be called and the complete rotation transform must be extracted
     * from it.
     * </p>
     * @param field field to which the elements belong
     * @param pv position-velocity of the spacecraft in some inertial frame
     * @param <T> type of the field elements
     * @return rotation from inertial frame to local orbital frame
     * @since 9.0
     */
    public abstract <T extends CalculusFieldElement<T>> FieldRotation<T> rotationFromInertial(Field<T> field,
                                                                                          FieldPVCoordinates<T> pv);

}
