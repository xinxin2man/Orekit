/* Copyright 2002-2021 CS GROUP
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

package org.orekit.files.ccsds.ndm.odm;

import org.orekit.bodies.CelestialBodies;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.files.ccsds.utils.CcsdsModifiedFrame;
import org.orekit.files.ccsds.utils.CenterName;
import org.orekit.files.ccsds.utils.ParsingContext;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;

/** Common metadata for Orbit Parameter/Ephemeris/Mean Message files.
 * @author Luc Maisonobe
 * @since 11.0
 */
public class OCommonMetadata extends ODMMetadata {

    /** Object identifier of the object for which the orbit state is provided. */
    private String objectID;

    /** Origin of reference frame. */
    private String centerName;

    /** Celestial body corresponding to the center name. */
    private CelestialBody centerBody;

    /** Tests whether the body corresponding to the center name can be
     * created through {@link CelestialBodies} in order to obtain the
     * corresponding gravitational coefficient. */
    private boolean hasCreatableBody;

    /** Reference frame in which data are given: used for state vector
     * and Keplerian elements data (and for the covariance reference frame if none is given). */
    private Frame refFrame;

    /** Epoch of reference frame, if not intrinsic to the definition of the
     * reference frame. */
    private String frameEpochString;

    /** Epoch of reference frame, if not intrinsic to the definition of the
     * reference frame. */
    private AbsoluteDate frameEpoch;

    /** Simple constructor.
     */
    public OCommonMetadata() {
        super(null);
    }

    /** {@inheritDoc} */
    @Override
    public void checkMandatoryEntries() {
        super.checkMandatoryEntries();
        checkNotNull(objectID,   OCommonMetadataKey.OBJECT_ID);
        checkNotNull(centerName, OCommonMetadataKey.CENTER_NAME);
        checkNotNull(refFrame,   OCommonMetadataKey.REF_FRAME);
    }

    /** Finalize the metadata date.
     * <p>
     * ODM standard enforces {@code TIME_SYSTEM} to appear *after*
     * {@code REF_FRAME_EPOCH} so we have to wait until parsing end
     * to finalize date
     * <p>
     * @param context
     */
    public void finalizeMetadata(final ParsingContext context) {
        if (frameEpochString != null) {
            frameEpoch = context.getTimeScale().parseDate(frameEpochString,
                                                          context.getConventions(),
                                                          context.getMissionReferenceDate(),
                                                          context.getDataContext().getTimeScales());
        }
    }

    /** Get the spacecraft ID for which the orbit state is provided.
     * @return the spacecraft ID
     */
    public String getObjectID() {
        return objectID;
    }

    /** Set the spacecraft ID for which the orbit state is provided.
     * @param objectID the spacecraft ID to be set
     */
    public void setObjectID(final String objectID) {
        refuseFurtherComments();
        this.objectID = objectID;
    }

    /** Get the launch year.
     * @return launch year
     */
    public int getLaunchYear() {
        return getLaunchYear(objectID);
    }

    /** Get the launch number.
     * @return launch number
     */
    public int getLaunchNumber() {
        return getLaunchNumber(objectID);
    }

    /** Get the piece of launch.
     * @return piece of launch
     */
    public String getLaunchPiece() {
        return getLaunchPiece(objectID);
    }

    /** Get the origin of reference frame.
     * @return the origin of reference frame.
     */
    public String getCenterName() {
        return centerName;
    }

    /** Set the origin of reference frame.
     * @param name the origin of reference frame to be set
     * @param celestialBodies factory for celestial bodies
     */
    public void setCenterName(final String name, final CelestialBodies celestialBodies) {

        refuseFurtherComments();

        // store the name itself
        this.centerName = name;

        // change the name to a canonical one in some cases
        final String canonicalValue;
        if ("SOLAR SYSTEM BARYCENTER".equals(centerName) || "SSB".equals(centerName)) {
            canonicalValue = "SOLAR_SYSTEM_BARYCENTER";
        } else if ("EARTH MOON BARYCENTER".equals(centerName) || "EARTH-MOON BARYCENTER".equals(centerName) ||
                   "EARTH BARYCENTER".equals(centerName) || "EMB".equals(centerName)) {
            canonicalValue = "EARTH_MOON";
        } else {
            canonicalValue = centerName;
        }

        final CenterName c;
        try {
            c = CenterName.valueOf(canonicalValue);
        } catch (IllegalArgumentException iae) {
            hasCreatableBody = false;
            centerBody       = null;
            return;
        }

        hasCreatableBody = true;
        centerBody       = c.getCelestialBody(celestialBodies);

    }

    /** Get the {@link CelestialBody} corresponding to the center name.
     * @return the center body
     */
    public CelestialBody getCenterBody() {
        return centerBody;
    }

    /** Set the {@link CelestialBody} corresponding to the center name.
     * @param centerBody the {@link CelestialBody} to be set
     */
    public void setCenterBody(final CelestialBody centerBody) {
        refuseFurtherComments();
        this.centerBody = centerBody;
    }

    /** Get boolean testing whether the body corresponding to the centerName
     * attribute can be created through the {@link CelestialBodies}.
     * @return true if {@link CelestialBody} can be created from centerName
     *         false otherwise
     */
    public boolean getHasCreatableBody() {
        return hasCreatableBody;
    }

    /** Set boolean testing whether the body corresponding to the centerName
     * attribute can be created through the {@link CelestialBodies}.
     * @param hasCreatableBody the boolean to be set.
     */
    public void setHasCreatableBody(final boolean hasCreatableBody) {
        refuseFurtherComments();
        this.hasCreatableBody = hasCreatableBody;
    }

    /**
     * Get the reference frame in which data are given: used for state vector and
     * Keplerian elements data (and for the covariance reference frame if none is given).
     *
     * @return the reference frame
     */
    public Frame getFrame() {
        final Frame frame = this.getRefFrame();
        final CelestialBody body = this.getCenterBody();
        if (body == null) {
            throw new OrekitException(OrekitMessages.NO_DATA_LOADED_FOR_CELESTIAL_BODY,
                    this.getCenterName());
        }
        // Just return frame if we don't need to shift the center based on CENTER_NAME
        // MCI and ICRF are the only non-earth centered frames specified in Annex A.
        final String frameString = this.getFrameString();
        final boolean isMci = "MCI".equals(frameString);
        final boolean isIcrf = "ICRF".equals(frameString);
        final boolean isSolarSystemBarycenter =
                CelestialBodyFactory.SOLAR_SYSTEM_BARYCENTER.equals(body.getName());
        if ((!(isMci || isIcrf) && CelestialBodyFactory.EARTH.equals(body.getName())) ||
                (isMci && CelestialBodyFactory.MARS.equals(body.getName())) ||
                (isIcrf && isSolarSystemBarycenter)) {
            return frame;
        }
        // else, translate frame to specified center.
        return new CcsdsModifiedFrame(frame, frameString, body, this.getCenterName());
    }

    /**
     * Get the the value of {@code REF_FRAME} as an Orekit {@link Frame}. The {@code
     * CENTER_NAME} key word has not been applied yet, so the returned frame may not
     * correspond to the reference frame of the data in the file.
     *
     * @return The reference frame specified by the {@code REF_FRAME} keyword.
     * @see #getFrame()
     */
    public Frame getRefFrame() {
        return refFrame;
    }

    /** Set the reference frame in which data are given: used for state vector
     * and Keplerian elements data (and for the covariance reference frame if none is given).
     * @param refFrame the reference frame to be set
     */
    public void setRefFrame(final Frame refFrame) {
        refuseFurtherComments();
        this.refFrame = refFrame;
    }

    /** Get epoch of reference frame, if not intrinsic to the definition of the
     * reference frame.
     * @return epoch of reference frame
     */
    String getFrameEpochString() {
        return frameEpochString;
    }

    /** Set epoch of reference frame, if not intrinsic to the definition of the
     * reference frame.
     * @param frameEpochString the epoch of reference frame to be set
     */
    void setFrameEpochString(final String frameEpochString) {
        refuseFurtherComments();
        this.frameEpochString = frameEpochString;
    }

    /** Get epoch of reference frame, if not intrinsic to the definition of the
     * reference frame.
     * @return epoch of reference frame
     */
    public AbsoluteDate getFrameEpoch() {
        return frameEpoch;
    }

    /** Set epoch of reference frame, if not intrinsic to the definition of the
     * reference frame.
     * @param frameEpoch the epoch of reference frame to be set
     */
    public void setFrameEpoch(final AbsoluteDate frameEpoch) {
        refuseFurtherComments();
        this.frameEpoch = frameEpoch;
    }

}



