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
package org.orekit.time;

import org.hipparchus.CalculusFieldElement;

/** Offset clock model.
 * @author Luc Maisonobe
 * @since 12.1
 */
public interface ClockModel {

    /** Get the clock offset at date.
     * @param date date at which offset is requested
     * @return clock offset at specified date
     */
    double getOffset(AbsoluteDate date);

    /** Get the clock offset at date.
     * @param <T> type of the field elements
     * @param date date at which offset is requested
     * @return clock offset at specified date
     */
    <T extends CalculusFieldElement<T>> T getOffset(FieldAbsoluteDate<T> date);

}
