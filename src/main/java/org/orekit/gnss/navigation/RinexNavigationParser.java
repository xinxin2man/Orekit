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
package org.orekit.gnss.navigation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.InputMismatchException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.hipparchus.util.FastMath;
import org.orekit.annotation.DefaultDataContext;
import org.orekit.data.DataContext;
import org.orekit.data.DataSource;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.gnss.Frequency;
import org.orekit.gnss.RegionCode;
import org.orekit.gnss.RinexUtils;
import org.orekit.gnss.SatelliteSystem;
import org.orekit.gnss.SbasId;
import org.orekit.gnss.TimeSystem;
import org.orekit.gnss.UtcId;
import org.orekit.propagation.analytical.gnss.data.BeidouCivilianNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.BeidouLegacyNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.BeidouSatelliteType;
import org.orekit.propagation.analytical.gnss.data.CivilianNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.GLONASSNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.GPSCivilianNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.GPSLegacyNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.GalileoNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.IRNSSNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.LegacyNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.QZSSCivilianNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.QZSSLegacyNavigationMessage;
import org.orekit.propagation.analytical.gnss.data.SBASNavigationMessage;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.GNSSDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScales;
import org.orekit.utils.Constants;
import org.orekit.utils.units.Unit;

/**
 * Parser for RINEX navigation messages files.
 * <p>
 * This parser handles RINEX version from 3.01 to 4.00. It is not adapted for RINEX 2.10 and 2.11 versions.
 * </p>
 * @see <a href="https://files.igs.org/pub/data/format/rinex301.pdf"> 3.01 navigation messages file format</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex302.pdf"> 3.02 navigation messages file format</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex303.pdf"> 3.03 navigation messages file format</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex304.pdf"> 3.04 navigation messages file format</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex305.pdf"> 3.05 navigation messages file format</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex_4.00.pdf"> 4.00 navigation messages file format</a>
 *
 * @author Bryan Cazabonne
 * @since 11.0
 *
 */
public class RinexNavigationParser {

    /** Converter for positions. */
    private static final Unit KM = Unit.KILOMETRE;

    /** Converter for velocities. */
    private static final Unit KM_PER_S = Unit.parse("km/s");

    /** Converter for accelerations. */
    private static final Unit KM_PER_S2 = Unit.parse("km/s²");;

    /** Converter for velocities. */
    private static final Unit M_PER_S = Unit.parse("m/s");

    /** Converter for GLONASS τₙ. */
    private static final Unit MINUS_SECONDS = Unit.parse("-1s");

    /** Converter for clock drift. */
    private static final Unit S_PER_S = Unit.parse("s/s");

    /** Converter for clock drift rate. */
    private static final Unit S_PER_S2 = Unit.parse("s/s²");

    /** Converter for ΔUT₁ first derivative. */
    private static final Unit S_PER_DAY = Unit.parse("s/d");

    /** Converter for ΔUT₁ second derivative. */
    private static final Unit S_PER_DAY2 = Unit.parse("s/d²");

    /** Converter for square root of semi-major axis. */
    private static final Unit SQRT_M = Unit.parse("√m");

    /** Converter for angular rates. */
    private static final Unit RAD_PER_S = Unit.parse("rad/s");;

    /** Converter for angular accelerations. */
    private static final Unit RAD_PER_S2 = Unit.parse("rad/s²");;

    /** Converter for rates of small angle. */
    private static final Unit AS_PER_DAY = Unit.parse("as/d");;

    /** Converter for accelerations of small angles. */
    private static final Unit AS_PER_DAY2 = Unit.parse("as/d²");;

    /** System initials. */
    private static final String INITIALS = "GRECIJS";

    /** Set of time scales. */
    private final TimeScales timeScales;

    /**
     * Constructor.
     * <p>This constructor uses the {@link DataContext#getDefault() default data context}.</p>
     * @see #RinexNavigationParser(TimeScales)
     *
     */
    @DefaultDataContext
    public RinexNavigationParser() {
        this(DataContext.getDefault().getTimeScales());
    }

    /**
     * Constructor.
     * @param timeScales the set of time scales used for parsing dates.
     */
    public RinexNavigationParser(final TimeScales timeScales) {
        this.timeScales = timeScales;
    }

    /**
     * Parse RINEX navigation messages.
     * @param source source providing the data to parse
     * @return a parsed  RINEX navigation messages file
     * @throws IOException if {@code reader} throws one
     */
    public RinexNavigation parse(final DataSource source) throws IOException {

        // initialize internal data structures
        final ParseInfo pi = new ParseInfo(source.getName());

        Stream<LineParser> candidateParsers = Stream.of(LineParser.HEADER_VERSION);
        try (Reader reader = source.getOpener().openReaderOnce();
             BufferedReader br = new BufferedReader(reader)) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                ++pi.lineNumber;
                final String l = line;
                final Optional<LineParser> selected = candidateParsers.filter(p -> p.canHandle.test(l)).findFirst();
                if (selected.isPresent()) {
                    try {
                        selected.get().parsingMethod.parse(line, pi);
                    } catch (StringIndexOutOfBoundsException | NumberFormatException | InputMismatchException e) {
                        throw new OrekitException(e,
                                                  OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                  pi.lineNumber, source.getName(), line);
                    }
                    candidateParsers = selected.get().allowedNextProvider.apply(pi);
                } else {
                    throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                              pi.lineNumber, source.getName(), line);
                }
            }
        }

        return pi.file;

    }

    /** Transient data used for parsing a RINEX navigation messages file. */
    private class ParseInfo {

        /** Name of the data source. */
        private final String name;

        /** Set of time scales for parsing dates. */
        private final TimeScales timeScales;

        /** The corresponding navigation messages file object. */
        private RinexNavigation file;

        /** Flag indicating the distinction between "alpha" and "beta" ionospheric coefficients. */
        private boolean isIonosphereAlphaInitialized;

        /** Satellite system line parser. */
        private SatelliteSystemLineParser systemLineParser;

        /** Current global line number. */
        private int lineNumber;

        /** Current line number within the navigation message. */
        private int messageLineNumber;

        /** Container for GPS navigation message. */
        private GPSLegacyNavigationMessage gpsLNav;

        /** Container for GPS navigation message. */
        private GPSCivilianNavigationMessage gpsCNav;

        /** Container for Galileo navigation message. */
        private GalileoNavigationMessage galileoNav;

        /** Container for Beidou navigation message. */
        private BeidouLegacyNavigationMessage beidouLNav;

        /** Container for Beidou navigation message. */
        private BeidouCivilianNavigationMessage beidouCNav;

        /** Container for QZSS navigation message. */
        private QZSSLegacyNavigationMessage qzssLNav;

        /** Container for QZSS navigation message. */
        private QZSSCivilianNavigationMessage qzssCNav;

        /** Container for IRNSS navigation message. */
        private IRNSSNavigationMessage irnssNav;

        /** Container for GLONASS navigation message. */
        private GLONASSNavigationMessage glonassNav;

        /** Container for SBAS navigation message. */
        private SBASNavigationMessage sbasNav;

        /** Container for System Time Offset message. */
        private SystemTimeOffsetMessage sto;

        /** Container for Earth Orientation Parameter message. */
        private EarthOrientationParameterMessage eop;

        /** Container for ionosphere Klobuchar message. */
        private IonosphereKlobucharMessage klobuchar;

        /** Container for ionosphere Nequick-G message. */
        private IonosphereNequickGMessage nequickG;

        /** Container for ionosphere BDGIM message. */
        private IonosphereBDGIMMessage bdgim;

        /** Constructor, build the ParseInfo object.
         * @param name name of the data source
         */
        ParseInfo(final String name) {
            // Initialize default values for fields
            this.name                         = name;
            this.timeScales                   = RinexNavigationParser.this.timeScales;
            this.isIonosphereAlphaInitialized = false;
            this.file                         = new RinexNavigation();
            this.systemLineParser             = SatelliteSystemLineParser.GPS_LNAV;
        }

    }

    /** Parsers for specific lines. */
    private enum LineParser {

        /** Parser for version, file type and satellite system. */
        HEADER_VERSION(line -> RinexUtils.matchesLabel(line, "RINEX VERSION / TYPE"),
                       (line, pi) -> RinexUtils.parseVersionFileTypeSatelliteSystem(line, pi.name, pi.file.getHeader(),
                                                                                    3.01, 3.02, 3.03, 3.04, 3.05, 4.00),
                       LineParser::headerNext),

        /** Parser for generating program and emitting agency. */
        HEADER_PROGRAM(line -> RinexUtils.matchesLabel(line, "PGM / RUN BY / DATE"),
                       (line, pi) -> RinexUtils.parseProgramRunByDate(line, pi.lineNumber, pi.name, pi.timeScales, pi.file.getHeader()),
                       LineParser::headerNext),

        /** Parser for comments. */
        HEADER_COMMENT(line -> RinexUtils.matchesLabel(line, "COMMENT"),
                       (line, pi) -> RinexUtils.parseComment(line, pi.file.getHeader()),
                       LineParser::headerNext),

        /** Parser for ionospheric correction parameters. */
        HEADER_IONOSPHERIC(line -> RinexUtils.matchesLabel(line, "IONOSPHERIC CORR"),
                           (line, pi) -> {

                               // Satellite system
                               final String ionoType = RinexUtils.parseString(line, 0, 3);
                               pi.file.getHeader().setIonosphericCorrectionType(ionoType);

                               // Read coefficients
                               final double[] parameters = new double[4];
                               parameters[0] = RinexUtils.parseDouble(line, 5,  12);
                               parameters[1] = RinexUtils.parseDouble(line, 17, 12);
                               parameters[2] = RinexUtils.parseDouble(line, 29, 12);
                               parameters[3] = RinexUtils.parseDouble(line, 41, 12);

                               // Verify if we are parsing Galileo ionospheric parameters
                               if ("GAL".equals(ionoType)) {

                                   // We are parsing Galileo ionospheric parameters
                                   pi.file.setNeQuickAlpha(parameters);

                               } else {
                                   // We are parsing Klobuchar ionospheric parameters

                                   // Verify if we are parsing "alpha" or "beta" ionospheric parameters
                                   if (pi.isIonosphereAlphaInitialized) {

                                       // Ionospheric "beta" parameters
                                       pi.file.setKlobucharBeta(parameters);

                                   } else {

                                       // Ionospheric "alpha" parameters
                                       pi.file.setKlobucharAlpha(parameters);

                                       // Set the flag to true
                                       pi.isIonosphereAlphaInitialized = true;

                                   }

                               }

                           },
                           LineParser::headerNext),

        /** Parser for corrections to transform the system time to UTC or to other time systems. */
        HEADER_TIME(line -> RinexUtils.matchesLabel(line, "TIME SYSTEM CORR"),
                    (line, pi) -> {

                        // Read fields
                        final String type    = RinexUtils.parseString(line, 0,  4);
                        final double a0      = RinexUtils.parseDouble(line, 5,  17);
                        final double a1      = RinexUtils.parseDouble(line, 22, 16);
                        final int    refTime = RinexUtils.parseInt(line, 38, 7);
                        final int    refWeek = RinexUtils.parseInt(line, 46, 5);

                        // Add to the list
                        final TimeSystemCorrection tsc = new TimeSystemCorrection(type, a0, a1, refTime, refWeek);
                        pi.file.getHeader().addTimeSystemCorrections(tsc);

                    },
                    LineParser::headerNext),

        /** Parser for leap seconds. */
        HEADER_LEAP_SECONDS(line -> RinexUtils.matchesLabel(line, "LEAP SECONDS"),
                            (line, pi) -> pi.file.getHeader().setNumberOfLeapSeconds(RinexUtils.parseInt(line, 0, 6)),
                            LineParser::headerNext),

        /** Parser for DOI.
         * @since 12.0
         */
        HEADER_DOI(line -> RinexUtils.matchesLabel(line, "DOI"),
                            (line, pi) -> pi.file.getHeader().setDoi(RinexUtils.parseString(line, 0, RinexUtils.LABEL_INDEX)),
                            LineParser::headerNext),

        /** Parser for license.
         * @since 12.0
         */
        HEADER_LICENSE(line -> RinexUtils.matchesLabel(line, "LICENSE OF USE"),
                            (line, pi) -> pi.file.getHeader().setLicense(RinexUtils.parseString(line, 0, RinexUtils.LABEL_INDEX)),
                            LineParser::headerNext),

        /** Parser for stationInformation.
         * @since 12.0
         */
        HEADER_STATION_INFORMATION(line -> RinexUtils.matchesLabel(line, "STATION INFORMATION"),
                            (line, pi) -> pi.file.getHeader().setStationInformation(RinexUtils.parseString(line, 0, RinexUtils.LABEL_INDEX)),
                            LineParser::headerNext),

        /** Parser for merged files.
         * @since 12.0
         */
        HEADER_MERGED_FILE(line -> RinexUtils.matchesLabel(line, "MERGED FILE"),
                            (line, pi) -> pi.file.getHeader().setMergedFiles(RinexUtils.parseInt(line, 0, 9)),
                            LineParser::headerNext),

       /** Parser for the end of header. */
        HEADER_END(line -> RinexUtils.matchesLabel(line, "END OF HEADER"),
                   (line, pi) -> {
                       // get rinex format version
                       final RinexNavigationHeader header = pi.file.getHeader();
                       final double version = header.getFormatVersion();

                       // check mandatory header fields
                       if (version < 4) {
                           if (header.getRunByName() == null) {
                               throw new OrekitException(OrekitMessages.INCOMPLETE_HEADER, pi.name);
                           }
                       } else {
                           if (header.getRunByName() == null ||
                               header.getNumberOfLeapSeconds() < 0) {
                               throw new OrekitException(OrekitMessages.INCOMPLETE_HEADER, pi.name);
                           }
                       }
                   },
                   LineParser::navigationNext),

        /** Parser for navigation message space vehicle epoch and clock. */
        NAVIGATION_SV_EPOCH_CLOCK(line -> INITIALS.indexOf(line.charAt(0)) >= 0,
                                 (line, pi) -> {

                                     // Set the line number to 0
                                     pi.messageLineNumber = 0;

                                     if (pi.file.getHeader().getFormatVersion() < 4) {
                                         // Current satellite system
                                         final SatelliteSystem system = SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 0, 1));

                                         // Initialize parser
                                         pi.systemLineParser = SatelliteSystemLineParser.getParser(system, null, pi, line);
                                     }

                                     // Read first line
                                     pi.systemLineParser.parseSvEpochSvClockLine(line, pi);

                                 },
                                 LineParser::navigationNext),

        /** Parser for navigation message type. */
        EPH_TYPE(line -> line.startsWith("> EPH"),
                 (line, pi) -> {
                     final SatelliteSystem system = SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 6, 1));
                     final String          type   = RinexUtils.parseString(line, 10, 4);
                     pi.systemLineParser = SatelliteSystemLineParser.getParser(system, type, pi, line);
                 },
                 pi -> Stream.of(NAVIGATION_SV_EPOCH_CLOCK)),

        /** Parser for broadcast orbit. */
        BROADCAST_ORBIT(line -> line.startsWith("    "),
                        (line, pi) -> {

                            // Increment the line number
                            pi.messageLineNumber++;

                            // Read the corresponding line
                            if (pi.messageLineNumber == 1) {
                                // BROADCAST ORBIT – 1
                                pi.systemLineParser.parseFirstBroadcastOrbit(line, pi);
                            } else if (pi.messageLineNumber == 2) {
                                // BROADCAST ORBIT – 2
                                pi.systemLineParser.parseSecondBroadcastOrbit(line, pi);
                            } else if (pi.messageLineNumber == 3) {
                                // BROADCAST ORBIT – 3
                                pi.systemLineParser.parseThirdBroadcastOrbit(line, pi);
                            } else if (pi.messageLineNumber == 4) {
                                // BROADCAST ORBIT – 4
                                pi.systemLineParser.parseFourthBroadcastOrbit(line, pi);
                            } else if (pi.messageLineNumber == 5) {
                                // BROADCAST ORBIT – 5
                                pi.systemLineParser.parseFifthBroadcastOrbit(line, pi);
                            } else if (pi.messageLineNumber == 6) {
                                // BROADCAST ORBIT – 6
                                pi.systemLineParser.parseSixthBroadcastOrbit(line, pi);
                            } else if (pi.messageLineNumber == 7) {
                                // BROADCAST ORBIT – 7
                                pi.systemLineParser.parseSeventhBroadcastOrbit(line, pi);
                            } else if (pi.messageLineNumber == 8) {
                                // BROADCAST ORBIT – 8
                                pi.systemLineParser.parseEighthBroadcastOrbit(line, pi);
                            } else {
                                // BROADCAST ORBIT – 9
                                pi.systemLineParser.parseNinthBroadcastOrbit(line, pi);
                            }

                        },
                        LineParser::navigationNext),

        /** Parser for system time offset message model. */
        STO_LINE_1(line -> true,
                  (line, pi) -> {
                      pi.sto.setTransmissionTime(Unit.SECOND.toSI(RinexUtils.parseDouble(line,  4, 19)));
                      pi.sto.setA0(Unit.SECOND.toSI(RinexUtils.parseDouble(line, 23, 19)));
                      pi.sto.setA1(S_PER_S.toSI(RinexUtils.parseDouble(line, 42, 19)));
                      pi.sto.setA2(S_PER_S2.toSI(RinexUtils.parseDouble(line, 61, 19)));
                      pi.file.addSystemTimeOffset(pi.sto);
                      pi.sto = null;
                  },
                  LineParser::navigationNext),

        /** Parser for system time offset message space vehicle epoch and clock. */
        STO_SV_EPOCH_CLOCK(line -> true,
                           (line, pi) -> {

                               pi.sto.setDefinedTimeSystem(TimeSystem.parseTwoLettersCode(RinexUtils.parseString(line, 24, 2)));
                               pi.sto.setReferenceTimeSystem(TimeSystem.parseTwoLettersCode(RinexUtils.parseString(line, 26, 2)));
                               final String sbas = RinexUtils.parseString(line, 43, 18);
                               pi.sto.setSbasId(sbas.length() > 0 ? SbasId.valueOf(sbas) : null);
                               final String utc = RinexUtils.parseString(line, 62, 18);
                               pi.sto.setUtcId(utc.length() > 0 ? UtcId.parseUtcId(utc) : null);

                               // TODO is the reference date relative to one or the other time scale?
                               final int year  = RinexUtils.parseInt(line, 4, 4);
                               final int month = RinexUtils.parseInt(line, 9, 2);
                               final int day   = RinexUtils.parseInt(line, 12, 2);
                               final int hours = RinexUtils.parseInt(line, 15, 2);
                               final int min   = RinexUtils.parseInt(line, 18, 2);
                               final int sec   = RinexUtils.parseInt(line, 21, 2);
                               pi.sto.setReferenceEpoch(new AbsoluteDate(year, month, day, hours, min, sec,
                                                                         pi.sto.getDefinedTimeSystem().getTimeScale(pi.timeScales)));

                           },
                           pi -> Stream.of(STO_LINE_1)),

        /** Parser for system time offset message type. */
        STO_TYPE(line -> line.startsWith("> STO"),
                 (line, pi) ->
                     pi.sto = new SystemTimeOffsetMessage(SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 6, 1)),
                                                          RinexUtils.parseInt(line, 7, 2),
                                                          RinexUtils.parseString(line, 10, 4)),
                 pi -> Stream.of(STO_SV_EPOCH_CLOCK)),

        /** Parser for Earth orientation parameter message model. */
        EOP_LINE_2(line -> true,
                   (line, pi) -> {
                       pi.eop.setTransmissionTime(Unit.SECOND.toSI(RinexUtils.parseDouble(line,  4, 19)));
                       pi.eop.setDut1(Unit.SECOND.toSI(RinexUtils.parseDouble(line, 23, 19)));
                       pi.eop.setDut1Dot(S_PER_DAY.toSI(RinexUtils.parseDouble(line, 42, 19)));
                       pi.eop.setDut1DotDot(S_PER_DAY2.toSI(RinexUtils.parseDouble(line, 61, 19)));
                       pi.file.addEarthOrientationParameter(pi.eop);
                       pi.eop = null;
                   },
                   LineParser::navigationNext),

        /** Parser for Earth orientation parameter message model. */
        EOP_LINE_1(line -> true,
                  (line, pi) -> {
                      pi.eop.setYp(Unit.ARC_SECOND.toSI(RinexUtils.parseDouble(line, 23, 19)));
                      pi.eop.setYpDot(AS_PER_DAY.toSI(RinexUtils.parseDouble(line, 42, 19)));
                      pi.eop.setYpDotDot(AS_PER_DAY2.toSI(RinexUtils.parseDouble(line, 61, 19)));
                  },
                  pi -> Stream.of(EOP_LINE_2)),

        /** Parser for Earth orientation parameter message space vehicle epoch and clock. */
        EOP_SV_EPOCH_CLOCK(line -> true,
                           (line, pi) -> {
                               final int year  = RinexUtils.parseInt(line, 4, 4);
                               final int month = RinexUtils.parseInt(line, 9, 2);
                               final int day   = RinexUtils.parseInt(line, 12, 2);
                               final int hours = RinexUtils.parseInt(line, 15, 2);
                               final int min   = RinexUtils.parseInt(line, 18, 2);
                               final int sec   = RinexUtils.parseInt(line, 21, 2);
                               pi.eop.setReferenceEpoch(new AbsoluteDate(year, month, day, hours, min, sec,
                                                                         pi.eop.getSystem().getDefaultTimeSystem(pi.timeScales)));
                               pi.eop.setXp(Unit.ARC_SECOND.toSI(RinexUtils.parseDouble(line, 23, 19)));
                               pi.eop.setXpDot(AS_PER_DAY.toSI(RinexUtils.parseDouble(line, 42, 19)));
                               pi.eop.setXpDotDot(AS_PER_DAY2.toSI(RinexUtils.parseDouble(line, 61, 19)));
                           },
                           pi -> Stream.of(EOP_LINE_1)),

        /** Parser for Earth orientation parameter message type. */
        EOP_TYPE(line -> line.startsWith("> EOP"),
                 (line, pi) ->
                     pi.eop = new EarthOrientationParameterMessage(SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 6, 1)),
                                                                   RinexUtils.parseInt(line, 7, 2),
                                                                   RinexUtils.parseString(line, 10, 4)),
                 pi -> Stream.of(EOP_SV_EPOCH_CLOCK)),

        /** Parser for ionosphere Klobuchar message model. */
        KLOBUCHAR_LINE_2(line -> true,
                         (line, pi) -> {
                             pi.klobuchar.setBetaI(3, IonosphereKlobucharMessage.S_PER_SC_N[3].toSI(RinexUtils.parseDouble(line,  4, 19)));
                             pi.klobuchar.setRegionCode(RinexUtils.parseDouble(line, 23, 19) < 0.5 ?
                                                        RegionCode.WIDE_AREA : RegionCode.JAPAN);
                             pi.file.addKlobucharMessage(pi.klobuchar);
                             pi.klobuchar = null;
                         },
                         LineParser::navigationNext),

        /** Parser for ionosphere Klobuchar message model. */
        KLOBUCHAR_LINE_1(line -> true,
                         (line, pi) -> {
                             pi.klobuchar.setAlphaI(3, IonosphereKlobucharMessage.S_PER_SC_N[3].toSI(RinexUtils.parseDouble(line,  4, 19)));
                             pi.klobuchar.setBetaI(0, IonosphereKlobucharMessage.S_PER_SC_N[0].toSI(RinexUtils.parseDouble(line, 23, 19)));
                             pi.klobuchar.setBetaI(1, IonosphereKlobucharMessage.S_PER_SC_N[1].toSI(RinexUtils.parseDouble(line, 42, 19)));
                             pi.klobuchar.setBetaI(2, IonosphereKlobucharMessage.S_PER_SC_N[2].toSI(RinexUtils.parseDouble(line, 61, 19)));
                         },
                         pi -> Stream.of(KLOBUCHAR_LINE_2)),

        /** Parser for ionosphere Klobuchar message model. */
        KLOBUCHAR_LINE_0(line -> true,
                         (line, pi) -> {
                             final int year  = RinexUtils.parseInt(line, 4, 4);
                             final int month = RinexUtils.parseInt(line, 9, 2);
                             final int day   = RinexUtils.parseInt(line, 12, 2);
                             final int hours = RinexUtils.parseInt(line, 15, 2);
                             final int min   = RinexUtils.parseInt(line, 18, 2);
                             final int sec   = RinexUtils.parseInt(line, 21, 2);
                             pi.klobuchar.setTransmitTime(new AbsoluteDate(year, month, day, hours, min, sec,
                                                                           pi.klobuchar.getSystem().getDefaultTimeSystem(pi.timeScales)));
                             pi.klobuchar.setAlphaI(0, IonosphereKlobucharMessage.S_PER_SC_N[0].toSI(RinexUtils.parseDouble(line, 23, 19)));
                             pi.klobuchar.setAlphaI(1, IonosphereKlobucharMessage.S_PER_SC_N[1].toSI(RinexUtils.parseDouble(line, 42, 19)));
                             pi.klobuchar.setAlphaI(2, IonosphereKlobucharMessage.S_PER_SC_N[2].toSI(RinexUtils.parseDouble(line, 61, 19)));
                         },
                         pi -> Stream.of(KLOBUCHAR_LINE_1)),

        /** Parser for ionosphere Nequick-G message model. */
        NEQUICK_LINE_1(line -> true,
                               (line, pi) -> {
                                   pi.nequickG.setFlags((int) FastMath.rint(RinexUtils.parseDouble(line, 4, 19)));
                                   pi.file.addNequickGMessage(pi.nequickG);
                                   pi.nequickG = null;
                               },
                               LineParser::navigationNext),

        /** Parser for ionosphere Nequick-G message model. */
        NEQUICK_LINE_0(line -> true,
                               (line, pi) -> {
                                   final int year  = RinexUtils.parseInt(line, 4, 4);
                                   final int month = RinexUtils.parseInt(line, 9, 2);
                                   final int day   = RinexUtils.parseInt(line, 12, 2);
                                   final int hours = RinexUtils.parseInt(line, 15, 2);
                                   final int min   = RinexUtils.parseInt(line, 18, 2);
                                   final int sec   = RinexUtils.parseInt(line, 21, 2);
                                   pi.nequickG.setTransmitTime(new AbsoluteDate(year, month, day, hours, min, sec,
                                                                                pi.nequickG.getSystem().getDefaultTimeSystem(pi.timeScales)));
                                   pi.nequickG.setAi0(IonosphereNequickGMessage.SFU.toSI(RinexUtils.parseDouble(line, 23, 19)));
                                   pi.nequickG.setAi1(IonosphereNequickGMessage.SFU_PER_DEG.toSI(RinexUtils.parseDouble(line, 42, 19)));
                                   pi.nequickG.setAi2(IonosphereNequickGMessage.SFU_PER_DEG2.toSI(RinexUtils.parseDouble(line, 61, 19)));
                               },
                               pi -> Stream.of(NEQUICK_LINE_1)),

        /** Parser for ionosphere BDGIM message model. */
        BDGIM_LINE_2(line -> true,
                     (line, pi) -> {
                         pi.bdgim.setAlphaI(7, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line,  4, 19)));
                         pi.bdgim.setAlphaI(8, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line, 23, 19)));
                         pi.file.addBDGIMMessage(pi.bdgim);
                         pi.bdgim = null;
                     },
                     LineParser::navigationNext),

        /** Parser for ionosphere BDGIM message model. */
        BDGIM_LINE_1(line -> true,
                     (line, pi) -> {
                         pi.bdgim.setAlphaI(3, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line,  4, 19)));
                         pi.bdgim.setAlphaI(4, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line, 23, 19)));
                         pi.bdgim.setAlphaI(5, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line, 42, 19)));
                         pi.bdgim.setAlphaI(6, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line, 61, 19)));
                     },
                     pi -> Stream.of(BDGIM_LINE_2)),

        /** Parser for ionosphere BDGIM message model. */
        BDGIM_LINE_0(line -> true,
                     (line, pi) -> {
                         final int year  = RinexUtils.parseInt(line, 4, 4);
                         final int month = RinexUtils.parseInt(line, 9, 2);
                         final int day   = RinexUtils.parseInt(line, 12, 2);
                         final int hours = RinexUtils.parseInt(line, 15, 2);
                         final int min   = RinexUtils.parseInt(line, 18, 2);
                         final int sec   = RinexUtils.parseInt(line, 21, 2);
                         pi.bdgim.setTransmitTime(new AbsoluteDate(year, month, day, hours, min, sec,
                                                                   pi.bdgim.getSystem().getDefaultTimeSystem(pi.timeScales)));
                         pi.bdgim.setAlphaI(0, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line, 23, 19)));
                         pi.bdgim.setAlphaI(1, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line, 42, 19)));
                         pi.bdgim.setAlphaI(2, Unit.TOTAL_ELECTRON_CONTENT_UNIT.toSI(RinexUtils.parseDouble(line, 61, 19)));
                     },
                     pi -> Stream.of(BDGIM_LINE_1)),

        /** Parser for ionosphere message type. */
        IONO_TYPE(line -> line.startsWith("> ION"),
                  (line, pi) -> {
                      final SatelliteSystem system = SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 6, 1));
                      final int             prn    = RinexUtils.parseInt(line, 7, 2);
                      final String          type   = RinexUtils.parseString(line, 10, 4);
                      if (system == SatelliteSystem.GALILEO) {
                          pi.nequickG = new IonosphereNequickGMessage(system, prn, type);
                      } else {
                          // in Rinex 4.00, tables A32 and A34 are ambiguous as both seem to apply
                          // to Beidou CNVX messages, we consider BDGIM is the proper model in this case
                          if (system == SatelliteSystem.BEIDOU && "CNVX".equals(type)) {
                              pi.bdgim = new IonosphereBDGIMMessage(system, prn, type);
                          } else {
                              pi.klobuchar = new IonosphereKlobucharMessage(system, prn, type);
                          }
                      }
                  },
                  pi -> Stream.of(pi.nequickG != null ? NEQUICK_LINE_0 : (pi.bdgim != null ? BDGIM_LINE_0 : KLOBUCHAR_LINE_0)));

        /** Predicate for identifying lines that can be parsed. */
        private final Predicate<String> canHandle;

        /** Parsing method. */
        private final ParsingMethod parsingMethod;

        /** Provider for next line parsers. */
        private final Function<ParseInfo, Stream<LineParser>> allowedNextProvider;

        /** Simple constructor.
         * @param canHandle predicate for identifying lines that can be parsed
         * @param parsingMethod parsing method
         * @param allowedNextProvider supplier for allowed parsers for next line
         */
        LineParser(final Predicate<String> canHandle, final ParsingMethod parsingMethod,
                   final Function<ParseInfo, Stream<LineParser>> allowedNextProvider) {
            this.canHandle           = canHandle;
            this.parsingMethod       = parsingMethod;
            this.allowedNextProvider = allowedNextProvider;
        }

        /** Get the allowed parsers for next lines while parsing Rinex header.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> headerNext(final ParseInfo parseInfo) {
            if (parseInfo.file.getHeader().getFormatVersion() < 4) {
                // Rinex 3.x header entries
                return Stream.of(HEADER_COMMENT, HEADER_PROGRAM,
                                 HEADER_IONOSPHERIC, HEADER_TIME,
                                 HEADER_LEAP_SECONDS, HEADER_END);
            } else {
                // Rinex 4.x header entries
                return Stream.of(HEADER_COMMENT, HEADER_PROGRAM,
                                 HEADER_DOI, HEADER_LICENSE, HEADER_STATION_INFORMATION, HEADER_MERGED_FILE,
                                 HEADER_LEAP_SECONDS, HEADER_END);
            }
        }

        /** Get the allowed parsers for next lines while parsing navigation date.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> navigationNext(final ParseInfo parseInfo) {
            if (parseInfo.gpsLNav    != null || parseInfo.gpsCNav    != null || parseInfo.galileoNav != null ||
                parseInfo.beidouLNav != null || parseInfo.beidouCNav != null || parseInfo.qzssLNav   != null ||
                parseInfo.qzssCNav   != null || parseInfo.irnssNav   != null || parseInfo.glonassNav != null ||
                parseInfo.sbasNav    != null) {
                return Stream.of(BROADCAST_ORBIT);
            } else if (parseInfo.file.getHeader().getFormatVersion() < 4) {
                return Stream.of(NAVIGATION_SV_EPOCH_CLOCK);
            } else {
                return Stream.of(EPH_TYPE, STO_TYPE, EOP_TYPE, IONO_TYPE);
            }
        }

    }

    /** Parsers for satellite system specific lines. */
    private enum SatelliteSystemLineParser {

        /** GPS legacy. */
        GPS_LNAV() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {
                parseSvEpochSvClockLine(line,
                                        pi.gpsLNav::setPRN,
                                        pi.gpsLNav::setEpochToc, pi.timeScales.getGPS(),
                                        pi.gpsLNav::setAf0, Unit.SECOND,
                                        pi.gpsLNav::setAf1, S_PER_S,
                                        pi.gpsLNav::setAf2, S_PER_S2);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsLNav::setIODE,   Unit.SECOND,
                          pi.gpsLNav::setCrs,    Unit.METRE,
                          pi.gpsLNav::setDeltaN, RAD_PER_S,
                          pi.gpsLNav::setM0,     Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsLNav::setCuc,   Unit.RADIAN,
                          pi.gpsLNav::setE,     Unit.NONE,
                          pi.gpsLNav::setCus,   Unit.RADIAN,
                          pi.gpsLNav::setSqrtA, SQRT_M,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsLNav::setTime,   Unit.SECOND,
                          pi.gpsLNav::setCic,    Unit.RADIAN,
                          pi.gpsLNav::setOmega0, Unit.RADIAN,
                          pi.gpsLNav::setCis,    Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsLNav::setI0,       Unit.RADIAN,
                          pi.gpsLNav::setCrc,      Unit.METRE,
                          pi.gpsLNav::setPa,       Unit.RADIAN,
                          pi.gpsLNav::setOmegaDot, RAD_PER_S,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
                // iDot
                pi.gpsLNav.setIDot(RinexUtils.parseDouble(line, 4, 19));
                // Codes on L2 channel (ignored)
                // RinexUtils.parseDouble(line, 23, 19)
                // GPS week (to go with Toe)
                pi.gpsLNav.setWeek((int) RinexUtils.parseDouble(line, 42, 19));
                pi.gpsLNav.setDate(new GNSSDate(pi.gpsLNav.getWeek(),
                                               pi.gpsLNav.getTime(),
                                               SatelliteSystem.GPS,
                                               pi.timeScales).getDate());
            }

            /** {@inheritDoc} */
            @Override
            public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsLNav::setSvAccuracy,                           Unit.METRE,
                          d -> pi.gpsLNav.setSvHealth((int) FastMath.rint(d)), Unit.NONE,
                          pi.gpsLNav::setTGD,                                  Unit.SECOND,
                          pi.gpsLNav::setIODC,                                 Unit.SECOND,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsLNav::setTransmissionTime, Unit.SECOND,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          () -> {
                              pi.file.addGPSLegacyNavigationMessage(pi.gpsLNav);
                              pi.gpsLNav = null;
                          });
            }

        },

        /** GPS civilian.
         * @since 12.0
         */
        GPS_CNAV() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {
                parseSvEpochSvClockLine(line,
                                        pi.gpsCNav::setPRN,
                                        pi.gpsCNav::setEpochToc, pi.timeScales.getGPS(),
                                        pi.gpsCNav::setAf0, Unit.SECOND,
                                        pi.gpsCNav::setAf1, S_PER_S,
                                        pi.gpsCNav::setAf2, S_PER_S2);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsCNav::setADot,   M_PER_S,
                          pi.gpsCNav::setCrs,    Unit.METRE,
                          pi.gpsCNav::setDeltaN, RAD_PER_S,
                          pi.gpsCNav::setM0,     Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsCNav::setCuc,   Unit.RADIAN,
                          pi.gpsCNav::setE,     Unit.NONE,
                          pi.gpsCNav::setCus,   Unit.RADIAN,
                          pi.gpsCNav::setSqrtA, SQRT_M,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsCNav::setTime,   Unit.SECOND,
                          pi.gpsCNav::setCic,    Unit.RADIAN,
                          pi.gpsCNav::setOmega0, Unit.RADIAN,
                          pi.gpsCNav::setCis,    Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsCNav::setI0,       Unit.RADIAN,
                          pi.gpsCNav::setCrc,      Unit.METRE,
                          pi.gpsCNav::setPa,       Unit.RADIAN,
                          pi.gpsCNav::setOmegaDot, RAD_PER_S,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsCNav::setIDot,                                 RAD_PER_S,
                          pi.gpsCNav::setDeltaN0Dot,                           RAD_PER_S2,
                          d -> pi.gpsCNav.setUraiNed0((int) FastMath.rint(d)), Unit.NONE,
                          d -> pi.gpsCNav.setUraiNed1((int) FastMath.rint(d)), Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          d -> pi.gpsCNav.setUraiEd((int) FastMath.rint(d)),   Unit.NONE,
                          d -> pi.gpsCNav.setSvHealth((int) FastMath.rint(d)), Unit.NONE,
                          pi.gpsCNav::setTGD,                                  Unit.SECOND,
                          d -> pi.gpsCNav.setUraiNed2((int) FastMath.rint(d)), Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsCNav::setIscL1CA, Unit.SECOND,
                          pi.gpsCNav::setIscL2C,  Unit.SECOND,
                          pi.gpsCNav::setIscL5I5, Unit.SECOND,
                          pi.gpsCNav::setIscL5Q5, Unit.SECOND,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseEighthBroadcastOrbit(final String line, final ParseInfo pi) {
                if (pi.gpsCNav.isCnv2()) {
                    // in CNAV2 messages, there is an additional line for L1 CD and L1 CP inter signal delay
                    parseLine(line,
                              pi.gpsCNav::setIscL1CD, Unit.SECOND,
                              pi.gpsCNav::setIscL1CP, Unit.SECOND,
                              null, Unit.NONE,
                              null, Unit.NONE,
                              null);
                } else {
                    parseTransmissionTimeLine(line, pi);
                }
            }

            /** {@inheritDoc} */
            @Override
            public void parseNinthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseTransmissionTimeLine(line, pi);
            }

            /** Parse transmission time line.
             * @param line line to parse
             * @param pi holder for transient data
             */
            private void parseTransmissionTimeLine(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.gpsCNav::setTransmissionTime, Unit.SECOND,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          () -> {
                              pi.file.addGPSLegacyNavigationMessage(pi.gpsCNav);
                              pi.gpsCNav = null;
                          });
            }

        },

        /** Galileo. */
        GALILEO() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {
                parseSvEpochSvClockLine(line,
                                        pi.galileoNav::setPRN,
                                        pi.galileoNav::setEpochToc, pi.timeScales.getGPS(),
                                        pi.galileoNav::setAf0, Unit.SECOND,
                                        pi.galileoNav::setAf1, S_PER_S,
                                        pi.galileoNav::setAf2, S_PER_S2);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          d -> pi.galileoNav.setIODNav((int) FastMath.rint(d)), Unit.SECOND,
                          pi.galileoNav::setCrs,                                Unit.METRE,
                          pi.galileoNav::setDeltaN,                             RAD_PER_S,
                          pi.galileoNav::setM0,                                 Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.galileoNav::setCuc,   Unit.RADIAN,
                          pi.galileoNav::setE,     Unit.NONE,
                          pi.galileoNav::setCus,   Unit.RADIAN,
                          pi.galileoNav::setSqrtA, SQRT_M,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.galileoNav::setTime,   Unit.SECOND,
                          pi.galileoNav::setCic,    Unit.RADIAN,
                          pi.galileoNav::setOmega0, Unit.RADIAN,
                          pi.galileoNav::setCis,    Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.galileoNav::setI0,       Unit.RADIAN,
                          pi.galileoNav::setCrc,      Unit.METRE,
                          pi.galileoNav::setPa,       Unit.RADIAN,
                          pi.galileoNav::setOmegaDot, RAD_PER_S,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
                // iDot
                pi.galileoNav.setIDot(RinexUtils.parseDouble(line, 4, 19));
                pi.galileoNav.setDataSource((int) FastMath.rint(RinexUtils.parseDouble(line, 23, 19)));
                // GAL week (to go with Toe)
                pi.galileoNav.setWeek((int) RinexUtils.parseDouble(line, 42, 19));
                pi.galileoNav.setDate(new GNSSDate(pi.galileoNav.getWeek(),
                                                   pi.galileoNav.getTime(),
                                                   SatelliteSystem.GPS, // in Rinex files, week number is aligned to GPS week!
                                                   pi.timeScales).getDate());
            }

            /** {@inheritDoc} */
            @Override
            public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.galileoNav::setSisa,     Unit.METRE,
                          pi.galileoNav::setSvHealth, Unit.NONE,
                          pi.galileoNav::setBGDE1E5a, Unit.SECOND,
                          pi.galileoNav::setBGDE5bE1, Unit.SECOND,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.galileoNav::setTransmissionTime, Unit.SECOND,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          () -> {
                              pi.file.addGalileoNavigationMessage(pi.galileoNav);
                              pi.galileoNav = null;
                          });
            }

        },

        /** Glonass. */
        GLONASS() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {

                parseSvEpochSvClockLine(line,
                                        pi.glonassNav::setPRN,
                                        pi.glonassNav::setEpochToc, pi.timeScales.getUTC(),
                                        pi.glonassNav::setTauN, MINUS_SECONDS,
                                        pi.glonassNav::setGammaN, Unit.NONE,
                                        d -> pi.glonassNav.setTime(fmod(d, Constants.JULIAN_DAY)), Unit.NONE);

                // Set the ephemeris epoch (same as time of clock epoch)
                pi.glonassNav.setDate(pi.glonassNav.getEpochToc());

            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.glonassNav::setX,       KM,
                          pi.glonassNav::setXDot,    KM_PER_S,
                          pi.glonassNav::setXDotDot, KM_PER_S2,
                          pi.glonassNav::setHealth,  Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.glonassNav::setY,               KM,
                          pi.glonassNav::setYDot,            KM_PER_S,
                          pi.glonassNav::setYDotDot,         KM_PER_S2,
                          pi.glonassNav::setFrequencyNumber, Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.glonassNav::setZ,       KM,
                          pi.glonassNav::setZDot,    KM_PER_S,
                          pi.glonassNav::setZDotDot, KM_PER_S2,
                          null,                      Unit.NONE,
                          () -> {
                              if (pi.file.getHeader().getFormatVersion() < 3.045) {
                                  pi.file.addGlonassNavigationMessage(pi.glonassNav);
                                  pi.glonassNav = null;
                              }
                          });
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                if (pi.file.getHeader().getFormatVersion() > 3.045) {
                    // this line has been introduced in 3.05
                    parseLine(line,
                              pi.glonassNav::setStatusFlags,          Unit.NONE,
                              pi.glonassNav::setGroupDelayDifference, Unit.NONE,
                              pi.glonassNav::setURA,                  Unit.NONE,
                              pi.glonassNav::setHealthFlags,          Unit.NONE,
                              () -> {
                                  pi.file.addGlonassNavigationMessage(pi.glonassNav);
                                  pi.glonassNav = null;
                              });
                }
            }

        },

        /** QZSS legacy. */
        QZSS_LNAV() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {
                parseSvEpochSvClockLine(line,
                                        pi.qzssLNav::setPRN,
                                        pi.qzssLNav::setEpochToc, pi.timeScales.getGPS(),
                                        pi.qzssLNav::setAf0, Unit.SECOND,
                                        pi.qzssLNav::setAf1, S_PER_S,
                                        pi.qzssLNav::setAf2, S_PER_S2);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssLNav::setIODE,   Unit.SECOND,
                          pi.qzssLNav::setCrs,    Unit.METRE,
                          pi.qzssLNav::setDeltaN, RAD_PER_S,
                          pi.qzssLNav::setM0,     Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssLNav::setCuc,   Unit.RADIAN,
                          pi.qzssLNav::setE,     Unit.NONE,
                          pi.qzssLNav::setCus,   Unit.RADIAN,
                          pi.qzssLNav::setSqrtA, SQRT_M,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssLNav::setTime,   Unit.SECOND,
                          pi.qzssLNav::setCic,    Unit.RADIAN,
                          pi.qzssLNav::setOmega0, Unit.RADIAN,
                          pi.qzssLNav::setCis,    Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssLNav::setI0,       Unit.RADIAN,
                          pi.qzssLNav::setCrc,      Unit.METRE,
                          pi.qzssLNav::setPa,       Unit.RADIAN,
                          pi.qzssLNav::setOmegaDot, RAD_PER_S,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
                // iDot
                pi.qzssLNav.setIDot(RinexUtils.parseDouble(line, 4, 19));
                // Codes on L2 channel (ignored)
                // RinexUtils.parseDouble(line, 23, 19)
                // GPS week (to go with Toe)
                pi.qzssLNav.setWeek((int) RinexUtils.parseDouble(line, 42, 19));
                pi.qzssLNav.setDate(new GNSSDate(pi.qzssLNav.getWeek(),
                                                 pi.qzssLNav.getTime(),
                                                 SatelliteSystem.GPS, // in Rinex files, week number is aligned to GPS week!
                                                 pi.timeScales).getDate());
            }

            /** {@inheritDoc} */
            @Override
            public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssLNav::setSvAccuracy,                           Unit.METRE,
                          d -> pi.qzssLNav.setSvHealth((int) FastMath.rint(d)), Unit.NONE,
                          pi.qzssLNav::setTGD,                                  Unit.SECOND,
                          pi.qzssLNav::setIODC,                                 Unit.SECOND,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssLNav::setTransmissionTime, Unit.SECOND,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          () -> {
                              pi.file.addQZSSLegacyNavigationMessage(pi.qzssLNav);
                              pi.qzssLNav = null;
                          });
            }

        },

        /** QZSS civilian.
         * @since 12.0
         */
        QZSS_CNAV() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {
                parseSvEpochSvClockLine(line,
                                        pi.qzssCNav::setPRN,
                                        pi.qzssCNav::setEpochToc, pi.timeScales.getGPS(),
                                        pi.qzssCNav::setAf0, Unit.SECOND,
                                        pi.qzssCNav::setAf1, S_PER_S,
                                        pi.qzssCNav::setAf2, S_PER_S2);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssCNav::setADot,   M_PER_S,
                          pi.qzssCNav::setCrs,    Unit.METRE,
                          pi.qzssCNav::setDeltaN, RAD_PER_S,
                          pi.qzssCNav::setM0,     Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssCNav::setCuc,   Unit.RADIAN,
                          pi.qzssCNav::setE,     Unit.NONE,
                          pi.qzssCNav::setCus,   Unit.RADIAN,
                          pi.qzssCNav::setSqrtA, SQRT_M,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssCNav::setTime,   Unit.SECOND,
                          pi.qzssCNav::setCic,    Unit.RADIAN,
                          pi.qzssCNav::setOmega0, Unit.RADIAN,
                          pi.qzssCNav::setCis,    Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssCNav::setI0,       Unit.RADIAN,
                          pi.qzssCNav::setCrc,      Unit.METRE,
                          pi.qzssCNav::setPa,       Unit.RADIAN,
                          pi.qzssCNav::setOmegaDot, RAD_PER_S,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssCNav::setIDot,                                 RAD_PER_S,
                          pi.qzssCNav::setDeltaN0Dot,                           RAD_PER_S2,
                          d -> pi.qzssCNav.setUraiNed0((int) FastMath.rint(d)), Unit.NONE,
                          d -> pi.qzssCNav.setUraiNed1((int) FastMath.rint(d)), Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          d -> pi.qzssCNav.setUraiEd((int) FastMath.rint(d)),   Unit.NONE,
                          d -> pi.qzssCNav.setSvHealth((int) FastMath.rint(d)), Unit.NONE,
                          pi.qzssCNav::setTGD,                                  Unit.SECOND,
                          d -> pi.qzssCNav.setUraiNed2((int) FastMath.rint(d)), Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssCNav::setIscL1CA, Unit.SECOND,
                          pi.qzssCNav::setIscL2C,  Unit.SECOND,
                          pi.qzssCNav::setIscL5I5, Unit.SECOND,
                          pi.qzssCNav::setIscL5Q5, Unit.SECOND,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseEighthBroadcastOrbit(final String line, final ParseInfo pi) {
                if (pi.qzssCNav.isCnv2()) {
                    // in CNAV2 messages, there is an additional line for L1 CD and L1 CP inter signal delay
                    parseLine(line,
                              pi.qzssCNav::setIscL1CD, Unit.SECOND,
                              pi.qzssCNav::setIscL1CP, Unit.SECOND,
                              null, Unit.NONE,
                              null, Unit.NONE,
                              null);
                } else {
                    parseTransmissionTimeLine(line, pi);
                }
            }

            /** {@inheritDoc} */
            @Override
            public void parseNinthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseTransmissionTimeLine(line, pi);
            }

            /** Parse transmission time line.
             * @param line line to parse
             * @param pi holder for transient data
             */
            private void parseTransmissionTimeLine(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.qzssCNav::setTransmissionTime, Unit.SECOND,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          () -> {
                              pi.file.addQZSSCivilianNavigationMessage(pi.qzssCNav);
                              pi.qzssCNav = null;
                          });
            }

        },

        /** Beidou legacy. */
        BEIDOU_D1_D2() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {
                parseSvEpochSvClockLine(line,
                                        pi.beidouLNav::setPRN,
                                        pi.beidouLNav::setEpochToc, pi.timeScales.getBDT(),
                                        pi.beidouLNav::setAf0, Unit.SECOND,
                                        pi.beidouLNav::setAf1, S_PER_S,
                                        pi.beidouLNav::setAf2, S_PER_S2);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouLNav::setAODE,   Unit.SECOND,
                          pi.beidouLNav::setCrs,    Unit.METRE,
                          pi.beidouLNav::setDeltaN, RAD_PER_S,
                          pi.beidouLNav::setM0,     Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouLNav::setCuc,   Unit.RADIAN,
                          pi.beidouLNav::setE,     Unit.NONE,
                          pi.beidouLNav::setCus,   Unit.RADIAN,
                          pi.beidouLNav::setSqrtA, SQRT_M,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouLNav::setTime,   Unit.SECOND,
                          pi.beidouLNav::setCic,    Unit.RADIAN,
                          pi.beidouLNav::setOmega0, Unit.RADIAN,
                          pi.beidouLNav::setCis,    Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouLNav::setI0,       Unit.RADIAN,
                          pi.beidouLNav::setCrc,      Unit.METRE,
                          pi.beidouLNav::setPa,       Unit.RADIAN,
                          pi.beidouLNav::setOmegaDot, RAD_PER_S,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
                // iDot
                pi.beidouLNav.setIDot(RinexUtils.parseDouble(line, 4, 19));
                // BDT week (to go with Toe)
                pi.beidouLNav.setWeek((int) RinexUtils.parseDouble(line, 42, 19));
                pi.beidouLNav.setDate(new GNSSDate(pi.beidouLNav.getWeek(),
                                                   pi.beidouLNav.getTime(),
                                                   SatelliteSystem.BEIDOU,
                                                   pi.timeScales).getDate());
            }

            /** {@inheritDoc} */
            @Override
            public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouLNav::setSvAccuracy, Unit.METRE,
                          null,                         Unit.NONE,
                          pi.beidouLNav::setTGD1,       Unit.SECOND,
                          pi.beidouLNav::setTGD2,       Unit.SECOND,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouLNav::setTransmissionTime, Unit.SECOND,
                          pi.beidouLNav::setAODC,             Unit.SECOND,
                          null,                               Unit.NONE,
                          null,                               Unit.NONE,
                          () -> {
                              pi.file.addBeidouLegacyNavigationMessage(pi.beidouLNav);
                              pi.beidouLNav = null;
                          });
            }

        },

        /** Beidou-3 CNAV. */
        BEIDOU_CNV_123() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {
                parseSvEpochSvClockLine(line,
                                        pi.beidouCNav::setPRN,
                                        pi.beidouCNav::setEpochToc, pi.timeScales.getBDT(),
                                        pi.beidouCNav::setAf0, Unit.SECOND,
                                        pi.beidouCNav::setAf1, S_PER_S,
                                        pi.beidouCNav::setAf2, S_PER_S2);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouCNav::setADot,   M_PER_S,
                          pi.beidouCNav::setCrs,    Unit.METRE,
                          pi.beidouCNav::setDeltaN, RAD_PER_S,
                          pi.beidouCNav::setM0,     Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouCNav::setCuc,   Unit.RADIAN,
                          pi.beidouCNav::setE,     Unit.NONE,
                          pi.beidouCNav::setCus,   Unit.RADIAN,
                          pi.beidouCNav::setSqrtA, SQRT_M,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouCNav::setTime,   Unit.SECOND,
                          pi.beidouCNav::setCic,    Unit.RADIAN,
                          pi.beidouCNav::setOmega0, Unit.RADIAN,
                          pi.beidouCNav::setCis,    Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouCNav::setI0,       Unit.RADIAN,
                          pi.beidouCNav::setCrc,      Unit.METRE,
                          pi.beidouCNav::setPa,       Unit.RADIAN,
                          pi.beidouCNav::setOmegaDot, RAD_PER_S,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouCNav::setIDot,       RAD_PER_S,
                          pi.beidouCNav::setDeltaN0Dot, RAD_PER_S2,
                          d -> {
                              switch ((int) FastMath.rint(d)) {
                                  case 0 :
                                      pi.beidouCNav.setSatelliteType(BeidouSatelliteType.RESERVED);
                                      break;
                                  case 1 :
                                      pi.beidouCNav.setSatelliteType(BeidouSatelliteType.GEO);
                                      break;
                                  case 2 :
                                      pi.beidouCNav.setSatelliteType(BeidouSatelliteType.IGSO);
                                      break;
                                  case 3 :
                                      pi.beidouCNav.setSatelliteType(BeidouSatelliteType.MEO);
                                      break;
                                  default:
                                      throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                                pi.lineNumber, pi.name, line);
                              }
                          }, Unit.NONE,
                          pi.beidouCNav::setTime,     Unit.SECOND,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          d -> pi.beidouCNav.setSisaiOe((int) FastMath.rint(d)),  Unit.NONE,
                          d -> pi.beidouCNav.setSisaiOcb((int) FastMath.rint(d)), Unit.NONE,
                          d -> pi.beidouCNav.setSisaiOc1((int) FastMath.rint(d)), Unit.NONE,
                          d -> pi.beidouCNav.setSisaiOc2((int) FastMath.rint(d)), Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
                if (pi.beidouCNav.getSignal() == Frequency.B1C) {
                    parseLine(line,
                              pi.beidouCNav::setIscB1CD, Unit.SECOND,
                              null,                      Unit.NONE,
                              pi.beidouCNav::setTgdB1Cp, Unit.SECOND,
                              pi.beidouCNav::setTgdB2ap, Unit.SECOND,
                              null);
                } else if (pi.beidouCNav.getSignal() == Frequency.B2A) {
                    parseLine(line,
                              null,                      Unit.NONE,
                              pi.beidouCNav::setIscB2AD, Unit.SECOND,
                              pi.beidouCNav::setTgdB1Cp, Unit.SECOND,
                              pi.beidouCNav::setTgdB2ap, Unit.SECOND,
                              null);
                } else {
                    parseSismaiHealthIntegrity(line, pi);
                }
            }

            /** {@inheritDoc} */
            @Override
            public void parseEighthBroadcastOrbit(final String line, final ParseInfo pi) {
                if (pi.beidouCNav.getSignal() == Frequency.B2B) {
                    parseLine(line,
                              pi.beidouCNav::setTransmissionTime, Unit.SECOND,
                              null,                               Unit.NONE,
                              null,                               Unit.NONE,
                              null,                               Unit.NONE,
                              () -> {
                                  pi.file.addBeidouCivilianNavigationMessage(pi.beidouCNav);
                                  pi.beidouCNav = null;
                              });
                } else {
                    parseSismaiHealthIntegrity(line, pi);
                }
            }

            /** {@inheritDoc} */
            @Override
            public void parseNinthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.beidouCNav::setTransmissionTime,                 Unit.SECOND,
                          null,                                               Unit.NONE,
                          null,                                               Unit.NONE,
                          d -> pi.beidouCNav.setIODE((int) FastMath.rint(d)), Unit.NONE,
                          () -> {
                              pi.file.addBeidouCivilianNavigationMessage(pi.beidouCNav);
                              pi.beidouCNav = null;
                          });
            }

            /**
             * Parse the SISMAI/Health/integroty line.
             * @param line line to read
             * @param pi holder for transient data
             */
            private void parseSismaiHealthIntegrity(final String line, final ParseInfo pi) {
                parseLine(line,
                          d -> pi.beidouCNav.setSismai((int) FastMath.rint(d)),         Unit.NONE,
                          d -> pi.beidouCNav.setHealth((int) FastMath.rint(d)),         Unit.NONE,
                          d -> pi.beidouCNav.setIntegrityFlags((int) FastMath.rint(d)), Unit.NONE,
                          d -> pi.beidouCNav.setIODC((int) FastMath.rint(d)),           Unit.NONE,
                          null);
            }

        },

        /** SBAS. */
        SBAS() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {

                // Time scale (UTC for Rinex 3.01 and GPS for other RINEX versions)
                final int version100 = (int) FastMath.rint(pi.file.getHeader().getFormatVersion() * 100);
                final TimeScale    timeScale = (version100 == 301) ? pi.timeScales.getUTC() : pi.timeScales.getGPS();

                parseSvEpochSvClockLine(line,
                                        pi.sbasNav::setPRN,
                                        pi.sbasNav::setEpochToc, timeScale,
                                        pi.sbasNav::setAGf0, Unit.SECOND,
                                        pi.sbasNav::setAGf1, S_PER_S,
                                        pi.sbasNav::setTime, Unit.SECOND);

                // Set the ephemeris epoch (same as time of clock epoch)
                pi.sbasNav.setDate(pi.sbasNav.getEpochToc());

            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.sbasNav::setX,       KM,
                          pi.sbasNav::setXDot,    KM_PER_S,
                          pi.sbasNav::setXDotDot, KM_PER_S2,
                          pi.sbasNav::setHealth,  Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.sbasNav::setY,       KM,
                          pi.sbasNav::setYDot,    KM_PER_S,
                          pi.sbasNav::setYDotDot, KM_PER_S2,
                          pi.sbasNav::setURA,     Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.sbasNav::setZ,       KM,
                          pi.sbasNav::setZDot,    KM_PER_S,
                          pi.sbasNav::setZDotDot, KM_PER_S2,
                          pi.sbasNav::setIODN,    Unit.NONE,
                          () -> {
                              pi.file.addSBASNavigationMessage(pi.sbasNav);
                              pi.sbasNav = null;
                          });
            }

        },

        /** IRNSS. */
        IRNSS() {

            /** {@inheritDoc} */
            @Override
            public void parseSvEpochSvClockLine(final String line, final ParseInfo pi) {
                parseSvEpochSvClockLine(line,
                                        pi.irnssNav::setPRN,
                                        pi.irnssNav::setEpochToc, pi.timeScales.getIRNSS(),
                                        pi.irnssNav::setAf0, Unit.SECOND,
                                        pi.irnssNav::setAf1, S_PER_S,
                                        pi.irnssNav::setAf2, S_PER_S2);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFirstBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.irnssNav::setIODEC,   Unit.SECOND,
                          pi.irnssNav::setCrs,     Unit.METRE,
                          pi.irnssNav::setDeltaN,  RAD_PER_S,
                          pi.irnssNav::setM0,      Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSecondBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.irnssNav::setCuc,   Unit.RADIAN,
                          pi.irnssNav::setE,     Unit.NONE,
                          pi.irnssNav::setCus,   Unit.RADIAN,
                          pi.irnssNav::setSqrtA, SQRT_M,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseThirdBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.irnssNav::setTime,   Unit.SECOND,
                          pi.irnssNav::setCic,    Unit.RADIAN,
                          pi.irnssNav::setOmega0, Unit.RADIAN,
                          pi.irnssNav::setCis,    Unit.RADIAN,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.irnssNav::setI0,       Unit.RADIAN,
                          pi.irnssNav::setCrc,      Unit.METRE,
                          pi.irnssNav::setPa,       Unit.RADIAN,
                          pi.irnssNav::setOmegaDot, RAD_PER_S,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
                // iDot
                pi.irnssNav.setIDot(RinexUtils.parseDouble(line, 4, 19));
                // IRNSS week (to go with Toe)
                pi.irnssNav.setWeek((int) RinexUtils.parseDouble(line, 42, 19));
                pi.irnssNav.setDate(new GNSSDate(pi.irnssNav.getWeek(),
                                                 pi.irnssNav.getTime(),
                                                 SatelliteSystem.GPS, // in Rinex files, week number is aligned to GPS week!
                                                 pi.timeScales).getDate());
            }

            /** {@inheritDoc} */
            @Override
            public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          pi.irnssNav::setURA,      Unit.METRE,
                          pi.irnssNav::setSvHealth, Unit.NONE,
                          pi.irnssNav::setTGD,      Unit.SECOND,
                          null,                     Unit.NONE,
                          null);
            }

            /** {@inheritDoc} */
            @Override
            public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
                parseLine(line,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          null, Unit.NONE,
                          () -> {
                              pi.file.addIRNSSNavigationMessage(pi.irnssNav);
                              pi.irnssNav = null;
                          });
            }

        };

        /** Get the parse for navigation message.
         * @param system satellite system
         * @param type message type (null for Rinex 3.x)
         * @param parseInfo container for transient data
         * @param line line being parsed
         * @return the satellite system
         */
        public static SatelliteSystemLineParser getParser(final SatelliteSystem system, final String type,
                                                          final ParseInfo parseInfo, final String line) {
            switch (system) {
                case GPS :
                    if (type == null || type.equals(LegacyNavigationMessage.LNAV)) {
                        parseInfo.gpsLNav = new GPSLegacyNavigationMessage();
                        return GPS_LNAV;
                    } else if (type.equals(CivilianNavigationMessage.CNAV)) {
                        parseInfo.gpsCNav = new GPSCivilianNavigationMessage(false);
                        return GPS_CNAV;
                    } else if (type.equals(CivilianNavigationMessage.CNV2)) {
                        parseInfo.gpsCNav = new GPSCivilianNavigationMessage(true);
                        return GPS_CNAV;
                    }
                    break;
                case GALILEO :
                    if (type == null || type.equals("INAV") || type.equals("FNAV")) {
                        parseInfo.galileoNav = new GalileoNavigationMessage();
                        return GALILEO;
                    }
                    break;
                case GLONASS :
                    if (type == null || type.equals("FDMA")) {
                        parseInfo.glonassNav = new GLONASSNavigationMessage();
                        return GLONASS;
                    }
                    break;
                case QZSS :
                    if (type == null || type.equals(LegacyNavigationMessage.LNAV)) {
                        parseInfo.qzssLNav = new QZSSLegacyNavigationMessage();
                        return QZSS_LNAV;
                    } else if (type.equals(CivilianNavigationMessage.CNAV)) {
                        parseInfo.qzssCNav = new QZSSCivilianNavigationMessage(false);
                        return QZSS_CNAV;
                    } else if (type.equals(CivilianNavigationMessage.CNV2)) {
                        parseInfo.qzssCNav = new QZSSCivilianNavigationMessage(true);
                        return QZSS_CNAV;
                    }
                    break;
                case BEIDOU :
                    if (type == null ||
                        type.equals(BeidouLegacyNavigationMessage.D1) ||
                        type.equals(BeidouLegacyNavigationMessage.D2)) {
                        parseInfo.beidouLNav = new BeidouLegacyNavigationMessage();
                        return BEIDOU_D1_D2;
                    } else if (type.equals(BeidouCivilianNavigationMessage.CNV1)) {
                        parseInfo.beidouCNav = new BeidouCivilianNavigationMessage(Frequency.B1C);
                        return BEIDOU_CNV_123;
                    } else if (type.equals(BeidouCivilianNavigationMessage.CNV2)) {
                        parseInfo.beidouCNav = new BeidouCivilianNavigationMessage(Frequency.B2A);
                        return BEIDOU_CNV_123;
                    } else if (type.equals(BeidouCivilianNavigationMessage.CNV3)) {
                        parseInfo.beidouCNav = new BeidouCivilianNavigationMessage(Frequency.B2B);
                        return BEIDOU_CNV_123;
                    }
                    break;
                case IRNSS :
                    if (type == null || type.equals("LNAV")) {
                        parseInfo.irnssNav = new IRNSSNavigationMessage();
                        return IRNSS;
                    }
                    break;
                case SBAS :
                    if (type == null || type.equals("SBAS")) {
                        parseInfo.sbasNav = new SBASNavigationMessage();
                        return SBAS;
                    }
                    break;
                default:
                    // do nothing, handle error after the switch
            }
            throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                      parseInfo.lineNumber, parseInfo.name, line);
        }

        /**
         * Parse the SV/Epoch/Sv clock of the navigation message.
         * @param line line to read
         * @param prnSetter setter for the PRN
         * @param tocSetter setter for the Tim:e Of Clock
         * @param timeScale time scale to use for parsing the Time Of Clock
         * @param setter1 setter for the first field
         * @param unit1 unit for the first field
         * @param setter2 setter for the second field
         * @param unit2 unit for the second field
         * @param setter3 setter for the third field
         * @param unit3 unit for the third field
         */
        protected void parseSvEpochSvClockLine(final String line,
                                               final IntConsumer prnSetter,
                                               final Consumer<AbsoluteDate> tocSetter, final TimeScale timeScale,
                                               final DoubleConsumer setter1, final Unit unit1,
                                               final DoubleConsumer setter2, final Unit unit2,
                                               final DoubleConsumer setter3, final Unit unit3) {
            // PRN
            prnSetter.accept(RinexUtils.parseInt(line, 1, 2));

            // Toc
            final int year  = RinexUtils.parseInt(line, 4, 4);
            final int month = RinexUtils.parseInt(line, 9, 2);
            final int day   = RinexUtils.parseInt(line, 12, 2);
            final int hours = RinexUtils.parseInt(line, 15, 2);
            final int min   = RinexUtils.parseInt(line, 18, 2);
            final int sec   = RinexUtils.parseInt(line, 21, 2);
            tocSetter.accept(new AbsoluteDate(year, month, day, hours, min, sec, timeScale));

            // clock
            setter1.accept(unit1.toSI(RinexUtils.parseDouble(line, 23, 19)));
            setter2.accept(unit2.toSI(RinexUtils.parseDouble(line, 42, 19)));
            setter3.accept(unit3.toSI(RinexUtils.parseDouble(line, 61, 19)));

        }

        /** Parse o broadcast orbit line.
         * <p>
         * All parameters (except line) may be null if a field should be ignored
         * </p>
         * @param line line to parse
         * @param setter1 setter for the first field
         * @param unit1 unit for the first field
         * @param setter2 setter for the second field
         * @param unit2 unit for the second field
         * @param setter3 setter for the third field
         * @param unit3 unit for the third field
         * @param setter4 setter for the fourth field
         * @param unit4 unit for the fourth field
         * @param finalizer finalizer, non-null only for last broadcast line
         */
        protected void parseLine(final String line,
                                 final DoubleConsumer setter1, final Unit unit1,
                                 final DoubleConsumer setter2, final Unit unit2,
                                 final DoubleConsumer setter3, final Unit unit3,
                                 final DoubleConsumer setter4, final Unit unit4,
                                 final Finalizer finalizer) {
            if (setter1 != null) {
                setter1.accept(unit1.toSI(RinexUtils.parseDouble(line, 4, 19)));
            }
            if (setter2 != null) {
                setter2.accept(unit2.toSI(RinexUtils.parseDouble(line, 23, 19)));
            }
            if (setter3 != null) {
                setter3.accept(unit3.toSI(RinexUtils.parseDouble(line, 42, 19)));
            }
            if (setter4 != null) {
                setter4.accept(unit4.toSI(RinexUtils.parseDouble(line, 61, 19)));
            }
            if (finalizer != null) {
                finalizer.finalize();
            }
        }

        /** Finalizer for last broadcast orbit line. */
        private interface Finalizer {
            /** Finalize broadcast orbit.
             */
            void finalize();
        }

        /**
         * Parse the SV/Epoch/Sv clock of the navigation message.
         * @param line line to read
         * @param pi holder for transient data
         */
        public abstract void parseSvEpochSvClockLine(String line, ParseInfo pi);

        /**
         * Parse the "BROADCASTORBIT - 1" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public abstract void parseFirstBroadcastOrbit(String line, ParseInfo pi);

        /**
         * Parse the "BROADCASTORBIT - 2" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public abstract void parseSecondBroadcastOrbit(String line, ParseInfo pi);

        /**
         * Parse the "BROADCASTORBIT - 3" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public abstract void parseThirdBroadcastOrbit(String line, ParseInfo pi);

        /**
         * Parse the "BROADCASTORBIT - 4" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public void parseFourthBroadcastOrbit(final String line, final ParseInfo pi) {
            // do nothing by default
        }

        /**
         * Parse the "BROADCASTORBIT - 5" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public void parseFifthBroadcastOrbit(final String line, final ParseInfo pi) {
            // do nothing by default
        }

        /**
         * Parse the "BROADCASTORBIT - 6" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public void parseSixthBroadcastOrbit(final String line, final ParseInfo pi) {
            // do nothing by default
        }

        /**
         * Parse the "BROADCASTORBIT - 7" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public void parseSeventhBroadcastOrbit(final String line, final ParseInfo pi) {
            // do nothing by default
        }

        /**
         * Parse the "BROADCASTORBIT - 8" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public void parseEighthBroadcastOrbit(final String line, final ParseInfo pi) {
            // do nothing by default
        }

        /**
         * Parse the "BROADCASTORBIT - 9" line.
         * @param line line to read
         * @param pi holder for transient data
         */
        public void parseNinthBroadcastOrbit(final String line, final ParseInfo pi) {
            // do nothing by default
        }

        /**
         * Calculates the floating-point remainder of a / b.
         * <p>
         * fmod = a - x * b
         * where x = (int) a / b
         * </p>
         * @param a numerator
         * @param b denominator
         * @return the floating-point remainder of a / b
         */
        private static double fmod(final double a, final double b) {
            final double x = (int) (a / b);
            return a - x * b;
        }

    }

    /** Parsing method. */
    @FunctionalInterface
    private interface ParsingMethod {
        /** Parse a line.
         * @param line line to parse
         * @param parseInfo holder for transient data
         */
        void parse(String line, ParseInfo parseInfo);
    }

}
