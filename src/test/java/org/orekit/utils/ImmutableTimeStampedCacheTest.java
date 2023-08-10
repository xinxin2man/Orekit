/* Contributed in the public domain.
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
package org.orekit.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.Utils;
import org.orekit.errors.OrekitMessages;
import org.orekit.errors.TimeStampedCacheException;
import org.orekit.time.AbsoluteDate;

/**
 * Unit tests for {@link ImmutableTimeStampedCache}.
 *
 * @author Evan Ward
 */
public class ImmutableTimeStampedCacheTest {

    /**
     * arbitrary date
     */
    private static final AbsoluteDate date = AbsoluteDate.CCSDS_EPOCH;

    /**
     * set Orekit data for useful debugging messages from dates.
     */
    @BeforeAll
    public static void setUpBefore() {
        Utils.setDataRoot("regular-data");
    }

    /**
     * data provided to {@link #cache}
     */
    private List<AbsoluteDate> data;

    /**
     * subject under test
     */
    private ImmutableTimeStampedCache<AbsoluteDate> cache;

    /**
     * create {@link #cache} and {@link #data} with neighborsSize = 3
     */
    @BeforeEach
    public void setUp() {
        data = Arrays.asList(date, date.shiftedBy(1), date.shiftedBy(2),
                             date.shiftedBy(3), date.shiftedBy(4),
                             date.shiftedBy(5));
        cache = new ImmutableTimeStampedCache<AbsoluteDate>(3, data);
    }

    /**
     * check
     * {@link ImmutableTimeStampedCache#ImmutableTimeStampedCache(int, java.util.Collection)}
     */
    @Test
    public void testImmutableTimeStampedCache() {
        // exception for neighborsSize > data.size()
        try {
            new ImmutableTimeStampedCache<AbsoluteDate>(data.size() + 1, data);
            Assertions.fail("Expected Exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // exception for non-positive neighborsSize
        try {
            new ImmutableTimeStampedCache<AbsoluteDate>(0, data);
            Assertions.fail("Expected Exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // exception for null data
        try {
            new ImmutableTimeStampedCache<AbsoluteDate>(1, null);
            Assertions.fail("Expected Exception");
        } catch (NullPointerException e) {
            // expected
        }

        // exception for zero data
        try {
            new ImmutableTimeStampedCache<AbsoluteDate>(
                                                        1,
                                                        Collections
                                                            .<AbsoluteDate> emptyList());
            Assertions.fail("Expected Exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * check {@link ImmutableTimeStampedCache#getNeighbors(AbsoluteDate)} at a
     * series of different dates designed to test all logic paths.
     *
     * @throws TimeStampedCacheException
     */
    @Test
    public void testGetNeighbors()
        throws TimeStampedCacheException {
        // setup
        int size = data.size();

        // actions + verify

        // before fist data
        try {
            cache.getNeighbors(data.get(0).shiftedBy(-1));
            Assertions.fail("Expected Exception");
        } catch (TimeStampedCacheException e) {
            // expected
            Assertions.assertEquals(OrekitMessages.UNABLE_TO_GENERATE_NEW_DATA_BEFORE, e.getSpecifier());
        }

        // on fist date
        Assertions.assertArrayEquals(cache.getNeighbors(data.get(0)).toArray(), data
            .subList(0, 3).toArray());
        // between fist and second date
        Assertions.assertArrayEquals(cache.getNeighbors(data.get(0).shiftedBy(0.5))
            .toArray(), data.subList(0, 3).toArray());
        // in the middle on a date
        Assertions.assertArrayEquals(cache.getNeighbors(data.get(2)).toArray(), data
            .subList(1, 4).toArray());
        // in the middle between dates
        Assertions.assertArrayEquals(cache.getNeighbors(data.get(2).shiftedBy(0.5))
            .toArray(), data.subList(1, 4).toArray());
        // just before last date
        Assertions.assertArrayEquals(cache
            .getNeighbors(data.get(size - 1).shiftedBy(-0.5)).toArray(), data
            .subList(size - 3, size).toArray());
        // on last date
        Assertions.assertArrayEquals(cache.getNeighbors(data.get(size - 1)).toArray(),
                          data.subList(size - 3, size).toArray());

        // after last date
        AbsoluteDate central = data.get(size - 1).shiftedBy(1);
        try {
            cache.getNeighbors(central);
            Assertions.fail("Expected Exception");
        } catch (TimeStampedCacheException e) {
            // expected
           Assertions.assertEquals(OrekitMessages.UNABLE_TO_GENERATE_NEW_DATA_AFTER, e.getSpecifier());
        }
    }

    /**
     * check {@link ImmutableTimeStampedCache#getNeighborsSize()}
     */
    @Test
    public void testGetNeighborsSize() {
        Assertions.assertEquals(cache.getNeighborsSize(), 3);
    }

    /**
     * check {@link ImmutableTimeStampedCache#getEarliest()}
     */
    @Test
    public void testGetEarliest() {
        Assertions.assertEquals(cache.getEarliest(), data.get(0));
    }

    /**
     * check {@link ImmutableTimeStampedCache#getLatest()}
     */
    @Test
    public void testGetLatest() {
        Assertions.assertEquals(cache.getLatest(), data.get(data.size() - 1));
    }

    /**
     * check {@link ImmutableTimeStampedCache#getAll()}
     */
    @Test
    public void testGetAll() {
        Assertions.assertArrayEquals(cache.getAll().toArray(), data.toArray());
    }

    /**
     * check that the cache is immutable by changing the data passed in the
     * constructor and returned from methods.
     *
     * @throws TimeStampedCacheException
     */
    @Test
    public void testImmutable()
        throws TimeStampedCacheException {
        // setup
        List<AbsoluteDate> actuals;
        List<AbsoluteDate> expecteds = new ArrayList<AbsoluteDate>(data);
        AbsoluteDate different = date.shiftedBy(-50);

        // actions + verify

        // check constructor
        data.set(0, different);
        Assertions.assertArrayEquals(cache.getAll().toArray(), expecteds.toArray());

        // check getAll
        actuals = cache.getAll();
        try {
            actuals.set(0, different);
        } catch (UnsupportedOperationException e) {
            // exception ok
        }
        Assertions.assertArrayEquals(cache.getAll().toArray(), expecteds.toArray());

        // check getNeighbors
        actuals = cache.getNeighbors(date).collect(Collectors.toList());
        Assertions.assertArrayEquals(cache.getAll().toArray(), expecteds.toArray());
    }

    /**
     * check {@link ImmutableTimeStampedCache#emptyCache()}.
     */
    @Test
    public void testEmptyCache() {
        // setup
        cache = ImmutableTimeStampedCache.emptyCache();

        // actions + verify
        try {
            cache.getNeighbors(date);
            Assertions.fail("Expected Exception");
        } catch (TimeStampedCacheException e) {
            // expected
        }
        try {
            cache.getEarliest();
            Assertions.fail("Expected Exception");
        } catch (IllegalStateException e) {
            // expected
        }
        try {
            cache.getLatest();
            Assertions.fail("Expected Exception");
        } catch (IllegalStateException e) {
            // expected
        }
        Assertions.assertEquals(cache.getAll().size(), 0);
        Assertions.assertEquals(cache.getNeighborsSize(), 0);
    }

    @Test
    public void testNonLinear() {
        final ImmutableTimeStampedCache<AbsoluteDate> nonLinearCache = new ImmutableTimeStampedCache<>(2,
                        Arrays.asList(date.shiftedBy(10),
                                      date.shiftedBy(14),
                                      date.shiftedBy(18),
                                      date.shiftedBy(23),
                                      date.shiftedBy(30),
                                      date.shiftedBy(36),
                                      date.shiftedBy(45),
                                      date.shiftedBy(55),
                                      date.shiftedBy(67),
                                      date.shiftedBy(90),
                                      date.shiftedBy(118)));
        for (double dt = 10; dt < 118; dt += 0.01) {
            checkNeighbors(nonLinearCache, dt);
        }
    }

    private void checkNeighbors(final ImmutableTimeStampedCache<AbsoluteDate> nonLinearCache,
                                final double offset) {
        List<AbsoluteDate> s = nonLinearCache.getNeighbors(date.shiftedBy(offset)).collect(Collectors.toList());
        Assertions.assertEquals(2, s.size());
        Assertions.assertTrue(s.get(0).durationFrom(date) <= offset);
        Assertions.assertTrue(s.get(1).durationFrom(date) >  offset);
    }

}
