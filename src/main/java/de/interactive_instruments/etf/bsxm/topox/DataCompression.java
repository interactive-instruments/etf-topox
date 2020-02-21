/**
 * Copyright 2010-2020 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.bsxm.topox;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class DataCompression {

    private DataCompression() {}

    /**
     * Get the last 32 bits
     *
     * @param compressedValue
     *            compressed long value
     * @return last 32 bits
     */
    public static int getLeft(final long compressedValue) {
        return (int) (compressedValue >> 32);
    }

    /**
     * Get the first 32 bits
     *
     * @param compressedValue
     *            compressed long value
     * @return first 32 bits
     */
    public static int getRight(final long compressedValue) {
        return (int) compressedValue;
    }

    /**
     * Compress two ints to one long
     *
     * @param left
     *            int to shift the last 32 bits
     * @param right
     *            int to shift the first 32 bits
     * @return a long with both ints
     */
    public static long compress(final int left, final int right) {
        return toLeft(left) | toRight(right);
    }

    /**
     * 32 bit shift to the left
     *
     * @param left
     *            int to shift
     * @return a long with 32 bit left shifted
     */
    public static long toLeft(final int left) {
        return (((long) left) << 32);
    }

    /**
     * 32 bit shift to the right
     *
     * @param right
     *            int to shift
     * @return a long with 32 bit right shifted
     */
    public static long toRight(final int right) {
        return (right & 0xFFFFFFFFL);
    }

    public static int makeCompressedNodeIndex(final byte dbIndex, final int objectGeoDiffIndex) {
        return (((dbIndex) << 24) | objectGeoDiffIndex & 0xFFFFFF);
    }

    /**
     * Extract the db index from a compressed long index
     *
     * @param compressedIndex
     *            compressed long index
     * @return db index as int
     */
    public static int dbIndex(final long compressedIndex) {
        return (int) (compressedIndex >>> 56);
    }

    /**
     * Extract the object index from a compressed long index
     *
     * @param compressedIndex
     *            compressed long index
     * @return object index as int
     */
    public static int objectIndex(final long compressedIndex) {
        return ((int) (compressedIndex >>> 32) & 0xFFFFFF);
    }

    /**
     * Extract the object index from a compressed long index
     *
     * @param compressedIndex
     *            compressed long index
     * @return BaseX integer pre value of the object
     */
    public static int preObject(final long compressedIndex) {
        return getRight(compressedIndex) -
                objectIndex(compressedIndex);
    }
}
