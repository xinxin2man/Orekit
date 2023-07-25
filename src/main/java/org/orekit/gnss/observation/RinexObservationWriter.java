/* Copyright 2023 Thales Alenia Space
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
package org.orekit.gnss.observation;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.gnss.AppliedDCBS;
import org.orekit.gnss.AppliedPCVS;
import org.orekit.gnss.RinexLabels;
import org.orekit.gnss.SatInSystem;
import org.orekit.gnss.SatelliteSystem;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateTimeComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

/** Writer for Rinex observation file.
 * @author Luc Maisonobe
 * @since 12.0
 */
public class RinexObservationWriter {

    /** Index of label in header lines. */
    private static final int LABEL_INDEX = 60;

    /** Format for one 2 digits integer field. */
    private static final String PADDED_TWO_DIGITS_INTEGER = "%02d";

    /** Format for one 2 digits integer field. */
    private static final String TWO_DIGITS_INTEGER = "%2d";

    /** Format for one 4 digits integer field. */
    private static final String PADDED_FOUR_DIGITS_INTEGER = "%04d";

    /** Format for one 4 digits integer field. */
    private static final String FOUR_DIGITS_INTEGER = "%4d";

    /** Format for one 6 digits integer field. */
    private static final String SIX_DIGITS_INTEGER = "%6d";

    /** Format for one 8.( digits float field. */
    private static final String EIGHT_FIVE_DIGITS_FLOAT = "%8.5f";

    /** Format for one 9.4 digits float field. */
    private static final String NINE_FOUR_DIGITS_FLOAT = "%9.4f";

    /** Format for one 10.3 digits float field. */
    private static final String TEN_THREE_DIGITS_FLOAT = "%10.3f";

    /** Format for one 13.7 digits float field. */
    private static final String THIRTEEN_SEVEN_DIGITS_FLOAT = "%13.7f";

    /** Format for one 14.4 digits float field. */
    private static final String FOURTEEN_FOUR_DIGITS_FLOAT = "%14.4f";

    /** Destination of generated output. */
    private final Appendable output;

    /** Output name for error messages. */
    private final String outputName;

    /** Saved header. */
    private RinexObservationHeader savedHeader;

    /** Column number. */
    private int column;

    /** Simple constructor.
     * @param output destination of generated output
     * @param outputName output name for error messages
     */
    public RinexObservationWriter(final Appendable output, final String outputName)
        throws IOException {
        this.output      = output;
        this.outputName  = outputName;
        this.savedHeader = null;
        this.column      = 0;
    }

    /** Write a complete observation file.
     * <p>
     * This method calls {@link #writeHeader(RinexObservationHeader)} once
     * and then loops on calling {@link #writeObservationDataSet(ObservationDataSet)}
     * for all observation data sets in the file
     * </p>
     * @param rinexObservation Rinex observation file to write
     * @see #writeHeader(RinexObservationHeader)
     * @see #writeObservationDataSet(ObservationDataSet)
     * @exception IOException if an I/O error occurs.
     */
    public void writeCompleteFile(final RinexObservation rinexObservation)
        throws IOException {
        writeHeader(rinexObservation.getHeader());
        for (final ObservationDataSet observationDataSet : rinexObservation.getObservationDataSets()) {
            writeObservationDataSet(observationDataSet);
        }
    }

    /** Write header.
     * <p>
     * This method must be called exactly once at the beginning
     * (directly or by {@link #writeCompleteFile(RinexObservation)})
     * </p>
     * @param header header to write
     * @exception IOException if an I/O error occurs.
     */
    public void writeHeader(final RinexObservationHeader header)
        throws IOException {

        // check header is written exactly once
        if (savedHeader != null) {
            throw new OrekitException(OrekitMessages.HEADER_ALREADY_WRITTEN, outputName);
        }
        savedHeader = header;

        final ObservationTimeScale observationTimeScale = header.getSatelliteSystem().getObservationTimeScale() != null ?
                                                          header.getSatelliteSystem().getObservationTimeScale() :
                                                              ObservationTimeScale.GPS;
        final TimeScale timeScale = observationTimeScale.getTimeScale(TimeScalesFactory.getTimeScales());

        // RINEX VERSION / TYPE
        outputField("%9.2f", header.getFormatVersion(), 9);
        outputField("",                 20, true);
        outputField("OBSERVATION DATA", 40, true);
        outputField(header.getSatelliteSystem().getKey(), 41);
        finishHeaderLine(RinexLabels.VERSION);

        // PGM / RUN BY / DATE
        outputField(header.getProgramName(), 20, true);
        outputField(header.getRunByName(),   40, true);
        final DateTimeComponents dtc = header.getCreationDateComponents();
        if (header.getFormatVersion() < 3.0) {
            outputField(PADDED_TWO_DIGITS_INTEGER, dtc.getDate().getDay(), 42);
            outputField('-', 43);
            outputField(dtc.getDate().getMonthEnum().getUpperCaseAbbreviation(), 46,  true);
            outputField('-', 47);
            outputField(PADDED_TWO_DIGITS_INTEGER, dtc.getDate().getYear() % 100, 49);
            outputField(PADDED_TWO_DIGITS_INTEGER, dtc.getTime().getHour(), 52);
            outputField(':', 53);
            outputField(PADDED_TWO_DIGITS_INTEGER, dtc.getTime().getMinute(), 56);
            outputField(header.getCreationTimeZone(), 59, true);
        } else {
            outputField(PADDED_FOUR_DIGITS_INTEGER, dtc.getDate().getYear(), 44);
            outputField(PADDED_TWO_DIGITS_INTEGER, dtc.getDate().getMonth(), 46);
            outputField(PADDED_TWO_DIGITS_INTEGER, dtc.getDate().getDay(), 48);
            outputField(PADDED_TWO_DIGITS_INTEGER, dtc.getTime().getHour(), 51);
            outputField(':', 52);
            outputField(PADDED_TWO_DIGITS_INTEGER, dtc.getTime().getMinute(), 54);
            outputField(':', 55);
            outputField(PADDED_TWO_DIGITS_INTEGER, (int) FastMath.rint(dtc.getTime().getSecond()), 57);
            outputField(header.getCreationTimeZone(), 60, true);
        }
        finishHeaderLine(RinexLabels.PROGRAM);

        // COMMENT
        for (final String comment : header.getComments()) {
            outputField(comment, comment.length(), true);
            finishHeaderLine(RinexLabels.COMMENT);
        }

        // MARKER NAME
        outputField(header.getMarkerName(), 60, true);
        finishHeaderLine(RinexLabels.MARKER_NAME);

        // MARKER NUMBER
        if (header.getMarkerNumber() != null) {
            outputField(header.getMarkerNumber(), 20, true);
            finishHeaderLine(RinexLabels.MARKER_NUMBER);
        }

        // MARKER TYPE
        if (header.getFormatVersion() >= 3.0) {
            outputField(header.getMarkerType(), 20, true);
            finishHeaderLine( RinexLabels.MARKER_TYPE);
        }

        // OBSERVER / AGENCY
        outputField(header.getObserverName(), 20, true);
        outputField(header.getAgencyName(),   40, true);
        finishHeaderLine(RinexLabels.OBSERVER_AGENCY);

        // REC # / TYPE / VERS
        outputField(header.getReceiverNumber(),  20, true);
        outputField(header.getReceiverType(),    40, true);
        outputField(header.getReceiverVersion(), 60, true);
        finishHeaderLine(RinexLabels.REC_NB_TYPE_VERS);

        // ANT # / TYPE
        outputField(header.getAntennaNumber(), 20, true);
        outputField(header.getAntennaType(),   40, true);
        finishHeaderLine(RinexLabels.ANT_NB_TYPE);

        // APPROX POSITION XYZ
        writeHeaderLine(header.getApproxPos(), RinexLabels.APPROX_POSITION_XYZ);

        // ANTENNA: DELTA H/E/N
        if (!Double.isNaN(header.getAntennaHeight())) {
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, header.getAntennaHeight(),         14);
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, header.getEccentricities().getX(), 28);
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, header.getEccentricities().getY(), 42);
            finishHeaderLine(RinexLabels.ANTENNA_DELTA_H_E_N);
        }

        // ANTENNA: DELTA X/Y/Z
        writeHeaderLine(header.getAntennaReferencePoint(), RinexLabels.ANTENNA_DELTA_X_Y_Z);

        // ANTENNA: PHASECENTER
        if (header.getAntennaPhaseCenter() != null) {
            outputField(header.getSatelliteSystem().getKey(), 1);
            outputField(header.getObservationCode(), 5, true);
            outputField(NINE_FOUR_DIGITS_FLOAT,     header.getAntennaPhaseCenter().getX(), 14);
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, header.getAntennaPhaseCenter().getY(), 28);
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, header.getAntennaPhaseCenter().getZ(), 42);
            finishHeaderLine(RinexLabels.ANTENNA_PHASE_CENTER);
        }

        // ANTENNA: B.SIGHT XY
        writeHeaderLine(header.getAntennaBSight(), RinexLabels.ANTENNA_B_SIGHT_XYZ);

        // ANTENNA: ZERODIR AZI
        if (!Double.isNaN(header.getAntennaAzimuth())) {
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, header.getAntennaAzimuth(), 14);
            finishHeaderLine(RinexLabels.ANTENNA_ZERODIR_AZI);
        }

        // ANTENNA: ZERODIR XYZ
        writeHeaderLine(header.getAntennaZeroDirection(), RinexLabels.ANTENNA_ZERODIR_XYZ);

        // OBS SCALE FACTOR
        if (FastMath.abs(header.getFormatVersion() - 2.20) < 0.001) {
            for (final SatelliteSystem system : SatelliteSystem.values()) {
                for (final ScaleFactorCorrection sfc : header.getScaleFactorCorrections(system)) {
                    if (sfc != null) {
                        outputField(SIX_DIGITS_INTEGER, (int) FastMath.round(sfc.getCorrection()), 6);
                        outputField(SIX_DIGITS_INTEGER, sfc.getTypesObsScaled().size(), 12);
                        for (int i = 0; i < sfc.getTypesObsScaled().size(); ++i) {
                            outputField(sfc.getTypesObsScaled().get(i).name(), 18 + 6 * i, true);
                        }
                        finishHeaderLine(RinexLabels.OBS_SCALE_FACTOR);
                    }
                }
            }
        }

        // CENTER OF MASS: XYZ
        writeHeaderLine(header.getCenterMass(), RinexLabels.CENTER_OF_MASS_XYZ);

        // DOI
        writeHeaderLine(header.getDoi(), RinexLabels.DOI);

        // LICENSE OF USE
        writeHeaderLine(header.getLicense(), RinexLabels.LICENSE);

        // STATION INFORMATION
        writeHeaderLine(header.getStationInformation(), RinexLabels.STATION_INFORMATION);

        // SYS / # / OBS TYPES
        for (Map.Entry<SatelliteSystem, List<ObservationType>> entry : header.getTypeObs().entrySet()) {
            if (header.getFormatVersion() < 3.0) {
                outputField(SIX_DIGITS_INTEGER, entry.getValue().size(), 6);
            } else {
                outputField(entry.getKey().getKey(), 1);
                outputField(SIX_DIGITS_INTEGER, entry.getValue().size(), 6);
            }
            for (final ObservationType observationType : entry.getValue()) {
                int next = column + (header.getFormatVersion() < 3.0 ? 6 : 4);
                if (next >= LABEL_INDEX) {
                    // we need to set up a continuation line
                    finishHeaderLine(header.getFormatVersion() < 3.0 ?
                                     RinexLabels.NB_TYPES_OF_OBSERV :
                                     RinexLabels.SYS_NB_TYPES_OF_OBSERV);
                    outputField("", 6, true);
                    next = column + (header.getFormatVersion() < 3.0 ? 6 : 4);
                }
                outputField(observationType.name(), next, false);
            }
            finishHeaderLine(header.getFormatVersion() < 3.0 ?
                             RinexLabels.NB_TYPES_OF_OBSERV :
                             RinexLabels.SYS_NB_TYPES_OF_OBSERV);
        }

        // SIGNAL STRENGTH UNIT
        writeHeaderLine(header.getSignalStrengthUnit(), RinexLabels.SIGNAL_STRENGTH_UNIT);

        // INTERVAL
        if (!Double.isNaN(header.getInterval())) {
            outputField(TEN_THREE_DIGITS_FLOAT, header.getInterval(), 10);
            finishHeaderLine(RinexLabels.INTERVAL);
        }

        // TIME OF FIRST OBS
        final DateTimeComponents dtcFirst = header.getTFirstObs().getComponents(timeScale);
        outputField(SIX_DIGITS_INTEGER,          dtcFirst.getDate().getYear(), 6);
        outputField(SIX_DIGITS_INTEGER,          dtcFirst.getDate().getMonth(), 12);
        outputField(SIX_DIGITS_INTEGER,          dtcFirst.getDate().getDay(), 18);
        outputField(SIX_DIGITS_INTEGER,          dtcFirst.getTime().getHour(), 24);
        outputField(SIX_DIGITS_INTEGER,          dtcFirst.getTime().getMinute(), 30);
        outputField(THIRTEEN_SEVEN_DIGITS_FLOAT, dtcFirst.getTime().getSecond(), 43);
        outputField(observationTimeScale.name(), 51, false);
        finishHeaderLine(RinexLabels.TIME_OF_FIRST_OBS);

        // TIME OF LAST OBS
        if (!header.getTLastObs().equals(AbsoluteDate.FUTURE_INFINITY)) {
            final DateTimeComponents dtcLast = header.getTLastObs().getComponents(timeScale);
            outputField(SIX_DIGITS_INTEGER,          dtcLast.getDate().getYear(), 6);
            outputField(SIX_DIGITS_INTEGER,          dtcLast.getDate().getMonth(), 12);
            outputField(SIX_DIGITS_INTEGER,          dtcLast.getDate().getDay(), 18);
            outputField(SIX_DIGITS_INTEGER,          dtcLast.getTime().getHour(), 24);
            outputField(SIX_DIGITS_INTEGER,          dtcLast.getTime().getMinute(), 30);
            outputField(THIRTEEN_SEVEN_DIGITS_FLOAT, dtcLast.getTime().getSecond(), 43);
            outputField(observationTimeScale.name(), 51, false);
            finishHeaderLine(RinexLabels.TIME_OF_LAST_OBS);
        }

        // RCV CLOCK OFFS APPL
        if (header.getClkOffset() >= 0) {
            outputField(SIX_DIGITS_INTEGER, header.getClkOffset(), 6);
            finishHeaderLine(RinexLabels.RCV_CLOCK_OFFS_APPL);
        }

        // SYS / DCBS APPLIED
        for (final AppliedDCBS appliedDCBS : header.getListAppliedDCBS()) {
            outputField(appliedDCBS.getSatelliteSystem().getKey(),  1);
            outputField(appliedDCBS.getProgDCBS(),                 19, true);
            outputField(appliedDCBS.getSourceDCBS(),               60, true);
            finishHeaderLine(RinexLabels.SYS_DCBS_APPLIED);
        }

        // SYS / PCVS APPLIED
        for (final AppliedPCVS appliedPCVS : header.getListAppliedPCVS()) {
            outputField(appliedPCVS.getSatelliteSystem().getKey(),  1);
            outputField(appliedPCVS.getProgPCVS(),                 19, true);
            outputField(appliedPCVS.getSourcePCVS(),               60, true);
            finishHeaderLine(RinexLabels.SYS_PCVS_APPLIED);
        }

        // SYS / SCALE FACTOR
        if (header.getFormatVersion() >= 3.0) {
            for (final SatelliteSystem system : SatelliteSystem.values()) {
                for (final ScaleFactorCorrection sfc : header.getScaleFactorCorrections(system)) {
                    if (sfc != null) {
                        outputField(system.getKey(), 1);
                        outputField(FOUR_DIGITS_INTEGER, (int) FastMath.rint(sfc.getCorrection()), 6);
                        outputField(TWO_DIGITS_INTEGER,  sfc.getTypesObsScaled().size(), 10);
                        for (ObservationType observationType : sfc.getTypesObsScaled()) {
                            int next = column + 4;
                            if (next >= LABEL_INDEX) {
                                // we need to set up a continuation line
                                finishHeaderLine(RinexLabels.SYS_SCALE_FACTOR);
                                outputField("", 10, true);
                                next = column + 4;
                            }
                            outputField(observationType.name(), next, true);
                        }
                        finishHeaderLine(RinexLabels.SYS_SCALE_FACTOR);
                    }
                }
            }
        }

        // SYS / PHASE SHIFT
        for (final PhaseShiftCorrection psc : header.getPhaseShiftCorrections()) {
            outputField(psc.getSatelliteSystem().getKey(), 1);
            outputField(psc.getTypeObs().name(), 5, true);
            outputField(EIGHT_FIVE_DIGITS_FLOAT, psc.getCorrection(), 14);
            outputField(TWO_DIGITS_INTEGER, psc.getSatsCorrected().size(), 18);
            for (final SatInSystem sis : psc.getSatsCorrected()) {
                int next = column + 4;
                if (next >= LABEL_INDEX) {
                    // we need to set up a continuation line
                    finishHeaderLine(RinexLabels.SYS_PHASE_SHIFT);
                    outputField("", 18, true);
                    next = column + 4;
                }
                outputField(sis.getSystem().getKey(), next - 2);
                outputField(PADDED_TWO_DIGITS_INTEGER, sis.getPRN(), next);
            }
            finishHeaderLine(RinexLabels.SYS_PHASE_SHIFT);
        }

        if (header.getFormatVersion() >= 3.0) {
            // GLONASS SLOT / FRQ #
            writeHeaderLine(String.format(Locale.US, ""), RinexLabels.GLONASS_SLOT_FRQ_NB); // TODO
        }

        if (header.getFormatVersion() >= 3.0) {
            // GLONASS COD/PHS/BIS
            writeHeaderLine(String.format(Locale.US, ""), RinexLabels.GLONASS_COD_PHS_BIS); // TODO
        }

        // LEAP SECONDS
        if (header.getLeapSeconds() > 0) {
            outputField(SIX_DIGITS_INTEGER, header.getLeapSeconds(), 6);
            if (header.getFormatVersion() >= 3.0) {
                outputField(SIX_DIGITS_INTEGER, header.getLeapSecondsFuture(),  12);
                outputField(SIX_DIGITS_INTEGER, header.getLeapSecondsWeekNum(), 18);
                outputField(SIX_DIGITS_INTEGER, header.getLeapSecondsDayNum(),  24);
            }
            finishHeaderLine(RinexLabels.LEAP_SECONDS);
        }

        if (!header.getNbObsPerSat().isEmpty()) {

            // # OF SATELLITES
            outputField(SIX_DIGITS_INTEGER, header.getNbObsPerSat().size(), 6);
            finishHeaderLine(RinexLabels.NB_OF_SATELLITES);

            // PRN / # OF OBS
            for (final Map.Entry<SatInSystem, Map<ObservationType, Integer>> entry1 : header.getNbObsPerSat().entrySet()) {
                outputField(entry1.getKey().getSystem().getKey(), 4);
                outputField(PADDED_TWO_DIGITS_INTEGER, entry1.getKey().getPRN(), 6);
                for (final Map.Entry<ObservationType, Integer> entry2 : entry1.getValue().entrySet()) {
                    int next = column + 6;
                    if (next >= LABEL_INDEX) {
                        // we need to set up a continuation line
                        finishHeaderLine(RinexLabels.PRN_NB_OF_OBS);
                        outputField("", 6, true);
                        next = column + 6;
                    }
                    outputField(SIX_DIGITS_INTEGER, entry2.getValue(), next);
                }
                finishHeaderLine(RinexLabels.PRN_NB_OF_OBS);
            }

        }

        // END OF HEADER
        writeHeaderLine("", RinexLabels.END);

    }

    /** Write one observation data set.
     * @param observationDataSet observation data set to write
     * @exception IOException if an I/O error occurs.
     */
    public void writeObservationDataSet(final ObservationDataSet observationDataSet)
        throws IOException {

        // check header has already been written
        if (savedHeader == null) {
            throw new OrekitException(OrekitMessages.HEADER_NOT_WRITTEN, outputName);
        }

        // TODO

    }

    /** Write one header string.
     * @param s string data (may be null)
     * @param label line label
     * @throws IOException if an I/O error occurs.
     */
    private void writeHeaderLine(final String s, final RinexLabels label) throws IOException {
        if (s != null) {
            outputField(s, s.length(), true);
            finishHeaderLine(label);
        }
    }

    /** Write one header vector.
     * @param vector vector data (may be null)
     * @param label line label
     * @throws IOException if an I/O error occurs.
     */
    private void writeHeaderLine(final Vector3D vector, final RinexLabels label) throws IOException {
        if (vector != null) {
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, vector.getX(), 14);
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, vector.getY(), 28);
            outputField(FOURTEEN_FOUR_DIGITS_FLOAT, vector.getZ(), 42);
            finishHeaderLine(label);
        }
    }

    /** Finish one header line.
     * @param label line label
     * @throws IOException if an I/O error occurs.
     */
    private void finishHeaderLine(final RinexLabels label) throws IOException {
        for (int i = column; i < LABEL_INDEX; ++i) {
            output.append(' ');
        }
        output.append(label.getLabel());
        output.append(System.lineSeparator());
        column = 0;
    }

    /** Output one single character field.
     * @param c field value
     * @param next target column for next field
     * @throws IOException if an I/O error occurs.
     */
    private void outputField(final char c, final int next) throws IOException {
        outputField(Character.toString(c), next, false);
    }

    /** Output one integer field.
     * @param format format to use
     * @param value field value
     * @param next target column for next field
     * @throws IOException if an I/O error occurs.
     */
    private void outputField(final String format, final int value, final int next) throws IOException {
        outputField(String.format(Locale.US, format, value), next, false);
    }

    /** Output one double field.
     * @param format format to use
     * @param value field value
     * @param next target column for next field
     * @throws IOException if an I/O error occurs.
     */
    private void outputField(final String format, final double value, final int next) throws IOException {
        if (Double.isNaN(value)) {
            // NaN values are replaced by blank fields
            outputField("", next, true);
        } else {
            outputField(String.format(Locale.US, format, value), next, false);
        }
    }

    /** Output one field.
     * @param field field to output
     * @param next target column for next field
     * @param leftJustified if true, field is left-justified
     * @throws IOException if an I/O error occurs.
     */
    private void outputField(final String field, final int next, final boolean leftJustified) throws IOException {
        final int padding = next - (field == null ? 0 : field.length()) - column;
        if (leftJustified && field != null) {
            output.append(field);
        }
        for (int i = 0; i < padding; ++i) {
            output.append(' ');
        }
        if (!leftJustified && field != null) {
            output.append(field);
        }
        column = next;
    }

}
