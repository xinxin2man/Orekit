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

package org.orekit.files.sinex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.orekit.Utils;
import org.orekit.gnss.ObservationType;
import org.orekit.gnss.SatelliteSystem;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.utils.Constants;
import org.junit.Test;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;


public class SinexLoaderDCBTest {
    
    private TimeScale utc;
    
    @Before
    public void setUp() {
        // Sets the root of data to read
        Utils.setDataRoot("gnss:sinex");
        utc = TimeScalesFactory.getUTC();
    }
    
    @Test
    public void testFirstLineDCB() {
        SinexLoader loader = new SinexLoader("DLR0MGXFIN_20212740000_03L_01D_DCB_default_description.BSX");
        AbsoluteDate creaDate = loader.getCreationDate();
        AbsoluteDate refCreaDate = new AbsoluteDate(new DateComponents(2022, 1, 1), utc).
                shiftedBy(Constants.JULIAN_DAY * (11 - 1)).
                shiftedBy(58414);
        Assert.assertEquals(creaDate, refCreaDate);
    }
    
    @Test
    public void testDCBDescription() {
        SinexLoader loader = new SinexLoader("DLR0MGXFIN_20212740000_03L_01D_DCB_trunc_sat.BSX");
        loader.getAvailableSystems();
        // DCB Description test
        SatelliteSystem timeSystem = loader.getDCBDescription().getTimeSystem();
        String biasMode =loader.getDCBDescription().getBiasMode();
        String determinationMethod =loader.getDCBDescription().getDeterminationMethod();
        int observationSampling =loader.getDCBDescription().getObservationSampling();
        int parameterSpacing =loader.getDCBDescription().getParameterSpacing();
        Assert.assertEquals(timeSystem, SatelliteSystem.GPS);
        Assert.assertEquals(biasMode, "RELATIVE");
        Assert.assertEquals(determinationMethod, "INTER-FREQUENCY_BIAS_ESTIMATION");
        Assert.assertEquals(parameterSpacing, 86400);
        Assert.assertEquals(observationSampling, 30);
    }
    
    @Test
    public void testDCBfile() {
        SinexLoader loader = new SinexLoader("DLR0MGXFIN_20212740000_03L_01D_DCB_trunc_sat.BSX");
        DCB DCBTest = loader.getDCB("G01");
        

        // Observation Pair test
        HashSet< HashSet<ObservationType> > ObsPairs = DCBTest.getAvailableObservationPairs();
        
        HashSet<ObservationType> OP1 = new HashSet<ObservationType>();
        HashSet<ObservationType> OP2 = new HashSet<ObservationType>();
        HashSet<ObservationType> OP3 = new HashSet<ObservationType>();
        HashSet<ObservationType> OP4 = new HashSet<ObservationType>();

        ObservationType Ob1 = ObservationType.valueOf("C1C");
        ObservationType Ob2 = ObservationType.valueOf("C1W");
        ObservationType Ob3 = ObservationType.valueOf("C2W");
        ObservationType Ob4 = ObservationType.valueOf("C5Q");
        ObservationType Ob5 = ObservationType.valueOf("C2W");
        ObservationType Ob6 = ObservationType.valueOf("C2L");
        
        OP1.add(Ob1);
        OP1.add(Ob2);
        OP2.add(Ob1);
        OP2.add(Ob3);
        OP3.add(Ob1);
        OP3.add(Ob4);
        OP4.add(Ob5);
        OP4.add(Ob6);
        
        HashSet< HashSet<ObservationType> > observationSetsRef = new HashSet< HashSet<ObservationType> >();
        observationSetsRef.add(OP1);
        observationSetsRef.add(OP2);
        observationSetsRef.add(OP3);
        observationSetsRef.add(OP4);
        
        Assert.assertEquals(null, ObsPairs, observationSetsRef);
        
        String Obs1 = "C1C";
        String Obs2 = "C1W";
        
        // Min Date test
        int year = 2021;
        int day = 274;
        int secInDay = 0;
        
        AbsoluteDate refFirstDate = new AbsoluteDate(new DateComponents(year, 1, 1), utc).
                shiftedBy(Constants.JULIAN_DAY * (day - 1)).
                shiftedBy(secInDay);
        AbsoluteDate firstDate =  DCBTest.getMinDateObservationPair(Obs1, Obs2);
        
        Assert.assertEquals(firstDate, refFirstDate);
        
        // Max Date Test
        year = 2021;
        day = 283;
        secInDay = 0;
        
        AbsoluteDate refLastDate = new AbsoluteDate(new DateComponents(year, 1, 1), utc).
                shiftedBy(Constants.JULIAN_DAY * (day - 1)).
                shiftedBy(secInDay);
        AbsoluteDate lastDate =  DCBTest.getMaxDateObservationPair(Obs1, Obs2);
        
        Assert.assertEquals(lastDate, refLastDate);
        
        // Value test for Satellites
        year = 2021;
        day = 280;
        secInDay = 43200;
        
        AbsoluteDate refDate = new AbsoluteDate(new DateComponents(year, 1, 1), utc).
                shiftedBy(Constants.JULIAN_DAY * (day - 1)).
                shiftedBy(secInDay);
        
        double valueDcb = DCBTest.getDCB(Obs1, Obs2, refDate);
        double valueDcbReal = -1.0697e-9;
        
        Assert.assertEquals(valueDcbReal, valueDcb, 1e-13);
        
        // Value Test for a Station
        DCB DCBTestStation = loader.getDCB("RALIC");
        HashSet<ObservationType> OPStation = new HashSet<ObservationType>();
        ObservationType Ob7 = ObservationType.valueOf("C1P");
        OPStation.add(Ob1);
        OPStation.add(Ob7);
        
        year = 2021;
        day = 300;
        secInDay = 43200;
        
        AbsoluteDate refDateStation = new AbsoluteDate(new DateComponents(year, 1, 1), utc).
                shiftedBy(Constants.JULIAN_DAY * (day - 1)).
                shiftedBy(secInDay);
        
        double valueDcbStation = DCBTestStation.getDCB("C1C", "C1P", refDateStation);
        double valueDcbRealStation = -0.6458e-9;
        
        Assert.assertEquals(valueDcbRealStation, valueDcbStation, 1e-13);
        
        // AVailable systems test
        List<String[]> availableSystemsTest = loader.getAvailableSystems("G11");
        List<String[]> availableSystemRef = new ArrayList<String[]>();
        String[] availableList = {"G", "G11", "G11"};
        availableSystemRef.add(availableList);
        
        Assert.assertArrayEquals(availableSystemRef.get(0), availableSystemsTest.get(0));
        
        // Test getSatelliteSystem
        Assert.assertEquals(DCBTest.getSatelliteSytem(), SatelliteSystem.GPS);
        
        // Test getPRN
        Assert.assertEquals("G01", DCBTest.getPRN());
        
        // Test getId : Satellite Case
        Assert.assertEquals("G01", DCBTest.getId());
    }
    
    @Test
    public void testDCBFileStation() {
        SinexLoader loader = new SinexLoader("DLR0MGXFIN_20212740000_03L_01D_DCB_trunc_sat.BSX");
        loader.getAvailableSystems();
        String stationIdRef = "AGGO";
        String stationSystemRef = "G";
        String idRef = stationSystemRef + stationIdRef;
        DCB DCBTest = loader.getDCB(idRef);
        
         // Test getId : Station Case
         Assert.assertEquals(idRef, DCBTest.getId());
         
         // Test getStationId : Station Case
         Assert.assertEquals(stationIdRef, DCBTest.getStationId());
    }
}
