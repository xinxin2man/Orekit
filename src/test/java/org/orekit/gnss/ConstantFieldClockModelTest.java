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
package org.orekit.gnss;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.Field;
import org.hipparchus.util.Binary64Field;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;


public class ConstantFieldClockModelTest {

    @Test
    public void testValueField() {
        doTestValueField(Binary64Field.getInstance());
    }

    @Test
    public void testRateField() {
        doTestRateField(Binary64Field.getInstance());
    }

    private <T extends CalculusFieldElement<T>> void doTestValueField(final Field<T> field) {
        final FieldClockModel<T> clock = new ConstantFieldClockModel<>(field.getZero().newInstance(1.25));
        final FieldAbsoluteDate<T> t0 = new FieldAbsoluteDate<T>(field, AbsoluteDate.GALILEO_EPOCH);
        Assertions.assertEquals(1.25, clock.getOffset(t0).getReal(),                1.0e-15);
        Assertions.assertEquals(1.25, clock.getOffset(t0.shiftedBy(1.0)).getReal(), 1.0e-15);
        Assertions.assertEquals(1.25, clock.getOffset(t0.shiftedBy(2.0)).getReal(), 1.0e-15);
    }

    private <T extends CalculusFieldElement<T>> void doTestRateField(final Field<T> field) {
        final FieldClockModel<T> clock = new ConstantFieldClockModel<>(field.getZero().newInstance(1.25));
        final FieldAbsoluteDate<T> t0 = new FieldAbsoluteDate<T>(field, AbsoluteDate.GALILEO_EPOCH);
        Assertions.assertEquals(0.0, clock.getRate(t0).getReal(),                1.0e-15);
        Assertions.assertEquals(0.0, clock.getRate(t0.shiftedBy(1.0)).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, clock.getRate(t0.shiftedBy(1.0)).getReal(), 1.0e-15);
    }

}
