/* Copyright 2002-2024 CS GROUP
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

import java.util.List;

import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeStamped;

/** Generator to use for creating entries in {@link GenericTimeStampedCache time stamped caches}.
 * <p>
 * As long as a generator is referenced by one {@link GenericTimeStampedCache cache} only, it is
 * guaranteed to be called in a thread-safe way, even if the cache is used in a multi-threaded
 * environment. The cache takes care of scheduling the calls to all the methods defined in
 * this interface so only one thread uses them at any time. There is no need for the
 * implementing classes to handle synchronization or locks by themselves.
 * </p>
 * <p>
 * The generator is provided by the user of the {@link GenericTimeStampedCache cache} and should
 * be consistent with the way he will use the cached data.
 * </p>
 * <p>
 * If entries must have regular time gaps (for example one entry every 3600 seconds), then
 * the generator must ensure by itself all generated entries are exactly located on the
 * expected regular grid, even if they are generated in random order. The reason for that
 * is that the cache may ask for entries in different ranges and merge these ranges afterwards.
 * A typical example would be a cache first calling the generator for 6 points around
 * 2012-02-19T17:48:00 and when these points are exhausted calling the generator again for 6
 * new points around 2012-02-19T23:20:00. If all points must be exactly 3600 seconds apart,
 * the generator should generate the first 6 points at 2012-02-19T15:00:00, 2012-02-19T16:00:00,
 * 2012-02-19T17:00:00, 2012-02-19T18:00:00, 2012-02-19T19:00:00 and 2012-02-19T20:00:00, and
 * the next 6 points at 2012-02-19T21:00:00, 2012-02-19T22:00:00, 2012-02-19T23:00:00,
 * 2012-02-20T00:00:00, 2012-02-20T01:00:00 and 2012-02-20T02:00:00. If the separation between
 * the points is irrelevant, the first points could be generated at 17:48:00 instead of
 * 17:00:00 or 18:00:00. The cache <em>will</em> merge arrays returned from different calls in
 * the same global time slot.
 * </p>
 * @param <T> Type of the cached data.
 * @author Luc Maisonobe
 */
public interface TimeStampedGenerator<T extends TimeStamped> {

    /** Generate a chronologically sorted list of entries to be cached.
     * <p>
     * If {@code existingDate} is earlier than {@code date}, the range covered by
     * generated entries must cover at least from {@code existingDate} (excluded)
     * to {@code date} (included). If {@code existingDate} is later than {@code date},
     * the range covered by generated entries must cover at least from {@code date}
     * (included) to {@code existingDate} (excluded).
     * </p>
     * <p>
     * The generated entries may cover a range larger than the minimum specified above
     * if the generator prefers to generate large chunks of data at once. It may
     * generate again entries already generated by an earlier call (typically at {@code
     * existingDate}), these extra entries will be silently ignored by the cache.
     * </p>
     * <p>
     * Non-coverage of the minimum range may lead to a loss of data, as the gap will
     * not be filled by the {@link GenericTimeStampedCache} in subsequent calls.
     * </p>
     * <p>
     * The generated entries <em>must</em> be chronologically sorted.
     * </p>
     * @param existingDate date of the closest already existing entry (may be null)
     * @param date date that must be covered by the range of the generated array
     * @return chronologically sorted list of generated entries
     */
    List<T> generate(AbsoluteDate existingDate, AbsoluteDate date);

}
