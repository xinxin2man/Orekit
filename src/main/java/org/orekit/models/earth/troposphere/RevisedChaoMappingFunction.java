/* Copyright 2002-2024 Thales Alenia Space
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

/** Chao mapping function for radio wavelengths.
 *
 * @see "J. A. Estefan, O. J. Sovers, A Comparative Survey of Current and Proposed Tropospheric
 * Refraction-Delay Models for DSN Radio Metric Data Calibration", 1994
 * @author Luc Maisonobe
 * @since 12.1
 */
public class RevisedChaoMappingFunction extends AbstractChaoMappingFunction {

    /** First coefficient for hydrostatic (dry) component. */
    private static final double AD = 0.00147;

    /** Second coefficient for hydrostatic (dry) component. */
    private static final double BD = 0.0400;

    /** First coefficient for wet component. */
    private static final double AW = 0.00035;

    /** Second coefficient for wet component. */
    private static final double BW = 0.017;

    /** Builds a new instance.
     */
    public RevisedChaoMappingFunction() {
        super(AD, BD, AW, BW);
    }

}
