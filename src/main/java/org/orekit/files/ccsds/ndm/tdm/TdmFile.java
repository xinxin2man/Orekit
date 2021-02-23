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

package org.orekit.files.ccsds.ndm.tdm;

import java.util.List;

import org.orekit.data.DataContext;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.files.ccsds.definitions.TimeSystem;
import org.orekit.files.ccsds.ndm.NdmFile;
import org.orekit.files.ccsds.section.Header;
import org.orekit.files.ccsds.section.Segment;
import org.orekit.utils.IERSConventions;

/** This class stores all the information of the CCSDS Tracking Data Message file parsed by TDMParser or TDMXMLParser. <p>
 * It contains the header and a list of Observations Blocks each containing
 * TDM metadata and a list of observation data lines. <p>
 * At this level the observations are not Orekit objects but custom object containing a keyword (type of observation),
 * a timetag (date of the observation) and a measurement (value of the observation). <p>
 * It is up to the user to convert these observations to Orekit tracking object (Range, Angular, TurnAroundRange etc...).<p>
 * References:<p>
 *  <a href="https://public.ccsds.org/Pubs/503x0b1c1.pdf">CCSDS 503.0-B-1 recommended standard</a> ("Tracking Data Message", Blue Book, Version 1.0, November 2007).
 * @author Maxime Journot
 * @since 9.0
 */
public class TdmFile extends NdmFile<Header, Segment<TdmMetadata, ObservationsBlock>> {

    /** Key for format version. */
    public static final String FORMAT_VERSION_KEY = "CCSDS_TDM_VERS";

    /** Simple constructor.
     * @param header file header
     * @param segments file segments
     * @param conventions IERS conventions
     * @param dataContext used for creating frames, time scales, etc.
     */
    public TdmFile(final Header header, final List<Segment<TdmMetadata, ObservationsBlock>> segments,
                   final IERSConventions conventions, final DataContext dataContext) {
        super(header, segments, conventions, dataContext);
    }

    /** Check that, according to the CCSDS standard, every ObservationsBlock has the same time system.
     */
    public void checkTimeSystems() {
        TimeSystem referenceTimeSystem = null;
        for (final Segment<TdmMetadata, ObservationsBlock> segment : getSegments()) {
            final TimeSystem timeSystem = segment.getMetadata().getTimeSystem();
            if (referenceTimeSystem == null) {
                referenceTimeSystem = timeSystem;
            } else if (!referenceTimeSystem.equals(timeSystem)) {
                throw new OrekitException(OrekitMessages.CCSDS_INCONSISTENT_TIME_SYSTEMS,
                                          referenceTimeSystem, timeSystem);
            }
        }
    }

}
