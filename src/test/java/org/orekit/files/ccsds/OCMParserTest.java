/* Copyright 2002-2019 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
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
package org.orekit.files.ccsds;

import java.net.URISyntaxException;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

public class OCMParserTest {

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data");
    }

    @Test
    public void testNonExistentFile() throws URISyntaxException {
        final String realName = getClass().getResource("/ccsds/OCMExample1.txt").toURI().getPath();
        final String wrongName = realName + "xxxxx";
        try {
            new OCMParser().parse(wrongName);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.UNABLE_TO_FIND_FILE, oe.getSpecifier());
            Assert.assertEquals(wrongName, oe.getParts()[0]);
        }
    }

    @Test
    public void testMissingT0() throws URISyntaxException {
        final String name = getClass().getResource("/ccsds/OCM-missing-t0.txt").toURI().getPath();
        try {
            new OCMParser().parse(name);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.CCSDS_MISSING_KEYWORD, oe.getSpecifier());
            Assert.assertEquals(Keyword.DEF_EPOCH_TZERO, oe.getParts()[0]);
        }
    }

    @Test
    public void testMissingTimeSystem() throws URISyntaxException {
        final String name = getClass().getResource("/ccsds/OCM-missing-time-system.txt").toURI().getPath();
        try {
            new OCMParser().parse(name);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.CCSDS_MISSING_KEYWORD, oe.getSpecifier());
            Assert.assertEquals(Keyword.DEF_TIME_SYSTEM, oe.getParts()[0]);
        }
    }

    @Test
    public void testWrongDate() throws URISyntaxException {
        final String name = getClass().getResource("/ccsds/OCM-wrong-date.txt").toURI().getPath();
        try {
            new OCMParser().parse(name);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE, oe.getSpecifier());
            Assert.assertEquals(11, ((Integer) oe.getParts()[0]).intValue());
            Assert.assertEquals("EARLIEST_TIME             = WRONG=123", oe.getParts()[2]);
        }
    }

    @Test
    public void testSpuriousMetaDataSection() throws URISyntaxException {
        final String name = getClass().getResource("/ccsds/OCM-spurious-metadata-section.txt").toURI().getPath();
        try {
            new OCMParser().parse(name);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.CCSDS_UNEXPECTED_KEYWORD, oe.getSpecifier());
            Assert.assertEquals(15, ((Integer) oe.getParts()[0]).intValue());
            Assert.assertEquals("META_START", oe.getParts()[2]);
        }
    }

    @Test
    public void testParseOCM1() {
        final String   ex  = "/ccsds/OCMExample1.txt";
        final OCMFile file = new OCMParser().
                             withConventions(IERSConventions.IERS_2010).
                             parse(getClass().getResourceAsStream(ex),
                                                   ex.substring(ex.lastIndexOf('/') + 1));

        // check the default values that are not set in this simple file
        Assert.assertEquals("CSPOC",              file.getMetaData().getCatalogName());
        Assert.assertEquals(1.0,                  file.getMetaData().getSecClockPerSISecond(), 1.0e-15);
        Assert.assertEquals(Constants.JULIAN_DAY, file.getMetaData().getSecPerDay(),           1.0e-15);
        Assert.assertEquals("LINEAR",             file.getMetaData().getInterpMethodEOP());

        // Check Header Block;
        Assert.assertEquals(3.0, file.getFormatVersion(), 1.0e-10);
        Assert.assertEquals(new AbsoluteDate(1998, 11, 06, 9, 23, 57, TimeScalesFactory.getUTC()),
                            file.getCreationDate());

        // OCM is the only message for which OBJECT_NAME, OBJECT_ID,
        // CENTER_NAME and REF_FRAME are not mandatory, they are not present in this minimal file
        Assert.assertNull(file.getMetaData().getObjectName());
        Assert.assertNull(file.getMetaData().getObjectID());
        Assert.assertNull(file.getMetaData().getCenterName());
        Assert.assertNull(file.getMetaData().getRefFrame());

        Assert.assertEquals("JPL", file.getOriginator());

        final AbsoluteDate t0 = new AbsoluteDate(1998, 12, 18, 14, 28, 15.1172, TimeScalesFactory.getUTC());
        Assert.assertEquals(t0, file.getMetaData().getDefEpochT0());
        Assert.assertEquals(TimeScalesFactory.getUTC(), file.getMetaData().getDefTimeSystem().getTimeScale(null));

        // orbit data
        Assert.assertEquals(1, file.getOrbitStateTimeHistories().size());
        OCMFile.OrbitStateHistory history = file.getOrbitStateTimeHistories().get(0);
        Assert.assertEquals("intervening data records omitted between DT=20.0 and DT=500.0", history.getComment().get(0));
        Assert.assertEquals("OSCULATING", history.getOrbAveraging());
        Assert.assertEquals("EARTH", history.getCenterName());
        Assert.assertEquals(CCSDSFrame.ITRF2000, history.getOrbRefFrame());
        Assert.assertEquals(CCSDSElementsType.CARTPV, history.getOrbType());
        Assert.assertEquals(0.0, history.getOrbEpochT0().durationFrom(t0), 1.0e-15);
        Assert.assertEquals(file.getMetaData().getDefTimeSystem(), history.getOrbTimeSystem());
        List<OCMFile.OrbitalState> states = history.getOrbitalStates();
        Assert.assertEquals(4, states.size());

        Assert.assertEquals(0.0, states.get(0).getDate().durationFrom(t0), 1.0e-15);
        Assert.assertEquals(6, states.get(0).getElements().length);
        Assert.assertEquals( 2789600.0, states.get(0).getElements()[0], 1.0e-15);
        Assert.assertEquals( -280000.0, states.get(0).getElements()[1], 1.0e-15);
        Assert.assertEquals(-1746800.0, states.get(0).getElements()[2], 1.0e-15);
        Assert.assertEquals(    4730.0, states.get(0).getElements()[3], 1.0e-15);
        Assert.assertEquals(   -2500.0, states.get(0).getElements()[4], 1.0e-15);
        Assert.assertEquals(   -1040.0, states.get(0).getElements()[5], 1.0e-15);

        Assert.assertEquals(10.0, states.get(1).getDate().durationFrom(t0), 1.0e-15);
        Assert.assertEquals(6, states.get(1).getElements().length);
        Assert.assertEquals( 2783400.0, states.get(1).getElements()[0], 1.0e-15);
        Assert.assertEquals( -308100.0, states.get(1).getElements()[1], 1.0e-15);
        Assert.assertEquals(-1877100.0, states.get(1).getElements()[2], 1.0e-15);
        Assert.assertEquals(    5190.0, states.get(1).getElements()[3], 1.0e-15);
        Assert.assertEquals(   -2420.0, states.get(1).getElements()[4], 1.0e-15);
        Assert.assertEquals(   -2000.0, states.get(1).getElements()[5], 1.0e-15);

        Assert.assertEquals(20.0, states.get(2).getDate().durationFrom(t0), 1.0e-15);
        Assert.assertEquals(6, states.get(2).getElements().length);
        Assert.assertEquals( 2776000.0, states.get(2).getElements()[0], 1.0e-15);
        Assert.assertEquals( -336900.0, states.get(2).getElements()[1], 1.0e-15);
        Assert.assertEquals(-2008700.0, states.get(2).getElements()[2], 1.0e-15);
        Assert.assertEquals(    5640.0, states.get(2).getElements()[3], 1.0e-15);
        Assert.assertEquals(   -2340.0, states.get(2).getElements()[4], 1.0e-15);
        Assert.assertEquals(   -1950.0, states.get(2).getElements()[5], 1.0e-15);

        Assert.assertEquals(500.0, states.get(3).getDate().durationFrom(t0), 1.0e-15);
        Assert.assertEquals(6, states.get(3).getElements().length);
        Assert.assertEquals( 2164375.0,  states.get(3).getElements()[0], 1.0e-15);
        Assert.assertEquals( 1115811.0,  states.get(3).getElements()[1], 1.0e-15);
        Assert.assertEquals( -688131.0,  states.get(3).getElements()[2], 1.0e-15);
        Assert.assertEquals(   -3533.28, states.get(3).getElements()[3], 1.0e-15);
        Assert.assertEquals(   -2884.52, states.get(3).getElements()[4], 1.0e-15);
        Assert.assertEquals(     885.35, states.get(3).getElements()[5], 1.0e-15);

    }

    // temporarily ignore the test as reference frame EFG is not available
    @Ignore
    @Test
    public void testParseOCM2() {
        final String   ex  = "/ccsds/OCMExample2.txt";
        final OCMFile file = new OCMParser().parse(getClass().getResourceAsStream(ex),
                                                   ex.substring(ex.lastIndexOf('/') + 1));

        // Check Header Block;
        Assert.assertEquals(3.0, file.getFormatVersion(), 1.0e-10);
        Assert.assertEquals("COMMENT This OCM reflects the latest conditions post-maneuver A67Z\n" + 
                            "COMMENT This example shows the specification of multiple comment lines",
                            file.getMetaDataComment());
        Assert.assertEquals(new AbsoluteDate(1998, 11, 06, 9, 23, 57, TimeScalesFactory.getUTC()),
                            file.getCreationDate());

        Assert.assertEquals("JAXA",                                file.getOriginator());
        Assert.assertEquals("R. Rabbit",                           file.getMetaData().getOriginatorPOC());
        Assert.assertEquals("Flight Dynamics Mission Design Lead", file.getMetaData().getOriginatorPosition());
        Assert.assertEquals("(719)555-1234",                       file.getMetaData().getOriginatorPhone());
        Assert.assertEquals("Mr. Rodgers",                         file.getMetaData().getTechPOC());
        Assert.assertEquals("(719)555-1234",                       file.getMetaData().getTechPhone());
        Assert.assertEquals("email@email.XXX ",                    file.getMetaData().getTechAddress());
        Assert.assertEquals("GODZILLA 5",                          file.getMetaData().getObjectName());
        Assert.assertEquals("1998-999A",                           file.getMetaData().getInternationalDesignator());
        Assert.assertEquals(new AbsoluteDate(1998, 12, 18, TimeScalesFactory.getUTC()),
                            file.getMetaData().getDefEpochT0());
        Assert.assertEquals("UT1", file.getMetaData().getDefTimeSystem().getTimeScale(IERSConventions.IERS_2010).getName());
        Assert.assertEquals(36.0,                                  file.getMetaData().getTaimutcT0(), 1.0e-15);
        Assert.assertEquals(0.357,                                 file.getMetaData().getUt1mutcT0(), 1.0e-15);

        // TODO test orbit data

        // TODO test physical data

        // TODO test perturbation data

        // TODO test user data

    }

    @Test
    public void testParseOCM3() {
        final String   ex  = "/ccsds/OCMExample3.txt";
        final OCMFile file = new OCMParser().
                             withConventions(IERSConventions.IERS_2010).
                             parse(getClass().getResourceAsStream(ex),
                                                   ex.substring(ex.lastIndexOf('/') + 1));

        // Check Header Block;
        Assert.assertEquals(3.0, file.getFormatVersion(), 1.0e-10);
        Assert.assertEquals(0, file.getMetaDataComment().size());
        Assert.assertEquals(new AbsoluteDate(1998, 11, 06, 9, 23, 57, TimeScalesFactory.getUTC()),
                            file.getCreationDate());

        Assert.assertEquals(new AbsoluteDate(1998, 12, 18, 14, 28, 15.1172, TimeScalesFactory.getUTC()),
                            file.getMetaData().getDefEpochT0());
        Assert.assertEquals("UTC", file.getMetaData().getDefTimeSystem().getTimeScale(IERSConventions.IERS_2010).getName());

        // TODO test orbit data

        // TODO test physical data

        // TODO test maneuvers data

        // TODO test perturbation data

        // TODO test orbit determination data

    }

    @Test
    public void testParseOCM4() {
        final String   ex  = "/ccsds/OCMExample4.txt";
        final OCMFile file = new OCMParser().
                        withConventions(IERSConventions.IERS_2010).
                        parse(getClass().getResourceAsStream(ex), ex.substring(ex.lastIndexOf('/') + 1));

        // Check Header Block;
        Assert.assertEquals(3.0, file.getFormatVersion(), 1.0e-10);
        Assert.assertEquals(2, file.getHeaderComment().size());
        Assert.assertEquals("This file is a dummy example with inconsistent data", file.getHeaderComment().get(0));
        Assert.assertEquals("it is used to exercise all possible keys in Key-Value Notation", file.getHeaderComment().get(1));

        // Check metadata
        Assert.assertEquals(new AbsoluteDate(2019, 7, 23, 10, 29, 31.576, TimeScalesFactory.getUTC()),
                            file.getCreationDate());

        Assert.assertEquals(1,                                     file.getMetaData().getComment().size());
        Assert.assertEquals("Metadata comment",                    file.getMetaData().getComment().get(0));
        Assert.assertEquals("JPL",                                 file.getOriginator());
        Assert.assertEquals("MR. RODGERS",                         file.getMetaData().getOriginatorPOC());
        Assert.assertEquals("FLIGHT DYNAMICS MISSION DESIGN LEAD", file.getMetaData().getOriginatorPosition());
        Assert.assertEquals("+49615130312",                        file.getMetaData().getOriginatorPhone());
        Assert.assertEquals("JOHN.DOE@EXAMPLE.ORG",                file.getMetaData().getOriginatorAddress());
        Assert.assertEquals("NASA",                                file.getMetaData().getTechOrg());
        Assert.assertEquals("MAXWELL SMART",                       file.getMetaData().getTechPOC());
        Assert.assertEquals("+49615130312",                        file.getMetaData().getTechPhone());
        Assert.assertEquals("MAX@EXAMPLE.ORG",                     file.getMetaData().getTechAddress());
        Assert.assertEquals("ABC-12-34",                           file.getMetaData().getMessageID());
        Assert.assertEquals("ABC-12-33",                           file.getMetaData().getPrevMessageID());
        Assert.assertEquals(new AbsoluteDate(2001, 11, 6, 11, 17, 33, TimeScalesFactory.getUTC()),
                            file.getMetaData().getPrevMessageEpoch());
        Assert.assertEquals("ABC-12-35",                           file.getMetaData().getNextMessageID());
        Assert.assertEquals(new AbsoluteDate(2001, 11, 7, 11, 17, 33, TimeScalesFactory.getUTC()),
                            file.getMetaData().getNextMessageEpoch());
        Assert.assertEquals("FOUO",                                file.getMetaData().getMessageClassification());
        Assert.assertEquals(1,                                     file.getMetaData().getAttMessageLink().size());
        Assert.assertEquals("ADM-MSG-35132.TXT",                   file.getMetaData().getAttMessageLink().get(0));
        Assert.assertEquals(2,                                     file.getMetaData().getCdmMessageLink().size());
        Assert.assertEquals("CDM-MSG-35132.TXT",                   file.getMetaData().getCdmMessageLink().get(0));
        Assert.assertEquals("CDM-MSG-35133.TXT",                   file.getMetaData().getCdmMessageLink().get(1));
        Assert.assertEquals(2,                                     file.getMetaData().getPrmMessageLink().size());
        Assert.assertEquals("PRM-MSG-35132.TXT",                   file.getMetaData().getPrmMessageLink().get(0));
        Assert.assertEquals("PRM-MSG-35133.TXT",                   file.getMetaData().getPrmMessageLink().get(1));
        Assert.assertEquals(2,                                     file.getMetaData().getRdmMessageLink().size());
        Assert.assertEquals("RDM-MSG-35132.TXT",                   file.getMetaData().getRdmMessageLink().get(0));
        Assert.assertEquals("RDM-MSG-35133.TXT",                   file.getMetaData().getRdmMessageLink().get(1));
        Assert.assertEquals(3,                                     file.getMetaData().getTdmMessageLink().size());
        Assert.assertEquals("TDM-MSG-35132.TXT",                   file.getMetaData().getTdmMessageLink().get(0));
        Assert.assertEquals("TDM-MSG-35133.TXT",                   file.getMetaData().getTdmMessageLink().get(1));
        Assert.assertEquals("TDM-MSG-35134.TXT",                   file.getMetaData().getTdmMessageLink().get(2));
        Assert.assertEquals("UNKNOWN",                             file.getMetaData().getObjectName());
        Assert.assertEquals("9999-999Z",                           file.getMetaData().getInternationalDesignator());
        Assert.assertEquals("22444",                               file.getMetaData().getObjectID());
        Assert.assertEquals("INTELSAT",                            file.getMetaData().getOperator());
        Assert.assertEquals("SIRIUS",                              file.getMetaData().getOwner());
        Assert.assertEquals("EOS",                                 file.getMetaData().getMission());
        Assert.assertEquals("SPIRE",                               file.getMetaData().getConstellation());
        Assert.assertEquals(new AbsoluteDate(2011, 11, 8, 11, 17, 33, TimeScalesFactory.getUTC()),
                            file.getMetaData().getLaunchEpoch());
        Assert.assertEquals("FRANCE",                              file.getMetaData().getLaunchCountry());
        Assert.assertEquals("FRENCH GUIANA",                       file.getMetaData().getLaunchSite());
        Assert.assertEquals("ARIANESPACE",                         file.getMetaData().getLaunchProvider());
        Assert.assertEquals("ULA",                                 file.getMetaData().getLaunchIntegrator());
        Assert.assertEquals("LC-41",                               file.getMetaData().getLaunchPad());
        Assert.assertEquals("GROUND",                              file.getMetaData().getLaunchPlatform());
        Assert.assertEquals(new AbsoluteDate(2021, 11, 8, 11, 17, 33, TimeScalesFactory.getUTC()),
                            file.getMetaData().getReleaseEpoch());
        Assert.assertEquals(new AbsoluteDate(2031, 11, 8, 11, 17, 33, TimeScalesFactory.getUTC()),
                            file.getMetaData().getMissionStartEpoch());
        Assert.assertEquals(new AbsoluteDate(2041, 11, 8, 11, 17, 33, TimeScalesFactory.getUTC()),
                            file.getMetaData().getMissionEndEpoch());
        Assert.assertEquals(new AbsoluteDate(2051, 11, 8, 11, 17, 33, TimeScalesFactory.getUTC()),
                            file.getMetaData().getReentryEpoch());
        Assert.assertEquals(22.0 * Constants.JULIAN_DAY,           file.getMetaData().getLifetime(), 1.0e-15);
        Assert.assertEquals("COMSPOC",                             file.getMetaData().getCatalogName());
        Assert.assertEquals("Payload",                             file.getMetaData().getObjectType().toString());
        Assert.assertEquals("Operational",                         file.getMetaData().getOpsStatus().toString());
        Assert.assertEquals("Extended Geostationary Orbit",        file.getMetaData().getOrbitType().toString());
        Assert.assertEquals(8,                                     file.getMetaData().getOcmDataElements().size());
        Assert.assertEquals("ORB",                                 file.getMetaData().getOcmDataElements().get(0));
        Assert.assertEquals("PHYSCHAR",                            file.getMetaData().getOcmDataElements().get(1));
        Assert.assertEquals("MNVR",                                file.getMetaData().getOcmDataElements().get(2));
        Assert.assertEquals("COV",                                 file.getMetaData().getOcmDataElements().get(3));
        Assert.assertEquals("OD",                                  file.getMetaData().getOcmDataElements().get(4));
        Assert.assertEquals("PERTS",                               file.getMetaData().getOcmDataElements().get(5));
        Assert.assertEquals("STM",                                 file.getMetaData().getOcmDataElements().get(6));
        Assert.assertEquals("USER",                                file.getMetaData().getOcmDataElements().get(7));
        Assert.assertEquals(new AbsoluteDate(2001,11, 10, 11, 17, 33.0, TimeScalesFactory.getUTC()),
                            file.getMetaData().getDefEpochT0());
        Assert.assertEquals("UTC", file.getMetaData().getDefTimeSystem().getTimeScale(null).getName());
        Assert.assertEquals(2.5,                                   file.getMetaData().getSecClockPerSISecond(), 1.0e-15);
        Assert.assertEquals(88740.0,                               file.getMetaData().getSecPerDay(), 1.0e-15);
        Assert.assertEquals(12.0,
                            file.getMetaData().getEarliestTime().durationFrom(file.getMetaData().getDefEpochT0()),
                            1.0e-15);
        Assert.assertEquals(new AbsoluteDate(2001,11, 13, TimeScalesFactory.getUTC()),
                            file.getMetaData().getLatestTime());
        Assert.assertEquals(20.0 * Constants.JULIAN_DAY,           file.getMetaData().getTimeSpan(), 1.0e-15);
        Assert.assertEquals(36.0,                                  file.getMetaData().getTaimutcT0(), 1.0e-15);
        Assert.assertEquals(0.357,                                 file.getMetaData().getUt1mutcT0(), 1.0e-15);
        Assert.assertEquals("CELESTRAK EOP FILE DOWNLOADED FROM HTTP://CELESTRAK.COM/SPACEDATA/EOP-LAST5YEARS.TXT AT 2001-11-08T00:00:00",
                            file.getMetaData().getEopSource());
        Assert.assertEquals("LAGRANGE ORDER 5",                    file.getMetaData().getInterpMethodEOP());

        // TODO test orbit data

        // TODO test physical data

        // TODO test perturbation data

        // TODO test user data

    }

}
