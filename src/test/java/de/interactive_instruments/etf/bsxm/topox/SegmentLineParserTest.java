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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.interactive_instruments.SUtils;
import de.interactive_instruments.container.Pair;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class SegmentLineParserTest {

    private class TestHashingSegmentHandler implements HashingSegmentHandler {

        private List<Pair<Double, Double>> coordinates = new ArrayList<>();

        @Override
        public void coordinate2d(final double x, final double y, final long hash, final long location, final int type) {
            this.coordinates.add(new Pair<>(x, y));
        }

        @Override
        public void coordinates2d(final double[] coordinates, final long[] hashesAndLocations, final int type) {

        }

        @Override
        public void nextGeometricObject() {

        }

        double getLastX() {
            return coordinates.get(coordinates.size() - 1).getLeft();
        }

        double getLastY() {
            return coordinates.get(coordinates.size() - 1).getRight();
        }

        List<Pair<Double, Double>> getCoordinates() {
            return this.coordinates;
        }
    }

    @Test
    public void testParsing1() {

        final TestHashingSegmentHandler testLineSegmentHandler = new TestHashingSegmentHandler();
        final HashingPosListParser parser = new HashingPosListParser(testLineSegmentHandler);

        final String x = "311432.345";
        final String y = "218549.999";

        final String str = x + " " + y;

        parser.parseDirectPositions(str, false, 1, 2);

        assertEquals(Double.valueOf(x), testLineSegmentHandler.getLastX(), Double.MIN_VALUE);
        assertEquals(Double.valueOf(y), testLineSegmentHandler.getLastY(), Double.MIN_VALUE);
    }

    @Test
    public void testParsing2() {

        final TestHashingSegmentHandler testLineSegmentHandler = new TestHashingSegmentHandler();
        final HashingPosListParser parser = new HashingPosListParser(testLineSegmentHandler);

        final String x = "1";
        final String y = "2";

        final String str = x + " " + y;

        parser.parseDirectPositions(str, false, 1, 2);

        assertEquals(Double.valueOf(x), testLineSegmentHandler.getLastX(), Double.MIN_VALUE);
        assertEquals(Double.valueOf(y), testLineSegmentHandler.getLastY(), Double.MIN_VALUE);
    }

    @Test
    public void testParsingWithBytes() {

        final TestHashingSegmentHandler testLineSegmentHandler = new TestHashingSegmentHandler();
        final HashingPosListParser parser = new HashingPosListParser(testLineSegmentHandler);

        parser.parseDirectPositions("3.3 3.4567".getBytes(), false, 1, 2);

        final List<Pair<Double, Double>> coord = testLineSegmentHandler.getCoordinates();

        assertEquals(Double.valueOf("3.3"), testLineSegmentHandler.getLastX(), Double.MIN_VALUE);
        assertEquals(Double.valueOf("3.4567"), testLineSegmentHandler.getLastY(), Double.MIN_VALUE);
    }

    @Test
    public void testParsingWithWihtespaces() {

        final TestHashingSegmentHandler testLineSegmentHandler = new TestHashingSegmentHandler();
        final HashingPosListParser parser = new HashingPosListParser(testLineSegmentHandler);

        final String x = "    311432.345  ";
        final String y = "218549.999   ";

        final String str = x + " " + y;

        parser.parseDirectPositions(str, false, 1, 2);

        assertEquals(Double.valueOf(x), testLineSegmentHandler.getLastX(), Double.MIN_VALUE);
        assertEquals(Double.valueOf(y), testLineSegmentHandler.getLastY(), Double.MIN_VALUE);
    }

    @Test
    public void testMultipleCoordinates1() {

        final TestHashingSegmentHandler testLineSegmentHandler = new TestHashingSegmentHandler();
        final HashingPosListParser parser = new HashingPosListParser(testLineSegmentHandler);

        final String coordinates[] = {
                "311432.345  ",
                "    218549.999  ",

                "467528.399",
                "5762548.333",

                "5755463.299",
                "467521.866",

                "467527.903",
                "5762548.333  "

        };

        final String str = SUtils.concatStr(" ", coordinates);
        parser.parseDirectPositions(str, false, 1, 2);
        assertEquals(4, testLineSegmentHandler.getCoordinates().size());
        for (int i = 0; i < coordinates.length; i += 2) {
            assertEquals(Double.valueOf(coordinates[i]), testLineSegmentHandler.getCoordinates().get(i / 2).getLeft());
            assertEquals(Double.valueOf(coordinates[i + 1]), testLineSegmentHandler.getCoordinates().get(i / 2).getRight());
        }
    }

    @Test
    public void testMultipleCoordinatesSkipDupl() {

        final TestHashingSegmentHandler testLineSegmentHandler = new TestHashingSegmentHandler();
        final HashingPosListParser parser = new HashingPosListParser(testLineSegmentHandler);

        final String coordinates[] = {
                "311432.345  ",
                "    218549.999  ",

                "311432.345",
                "218549.999 ",

                "467528.399",
                "5762548.333",

                "5755463.299",
                "467521.866",

                "5755463.299",
                "467521.866",

                "467527.903",
                "5762548.333  "

        };

        final String str = SUtils.concatStr(" ", coordinates);
        parser.parseDirectPositions(str, false, 1, 2);
        assertEquals(4, testLineSegmentHandler.getCoordinates().size());
        assertEquals(Double.valueOf(coordinates[0]), testLineSegmentHandler.getCoordinates().get(0).getLeft());
        assertEquals(Double.valueOf(coordinates[1]), testLineSegmentHandler.getCoordinates().get(0).getRight());

        assertEquals(Double.valueOf(coordinates[4]), testLineSegmentHandler.getCoordinates().get(1).getLeft());
        assertEquals(Double.valueOf(coordinates[5]), testLineSegmentHandler.getCoordinates().get(1).getRight());

        assertEquals(Double.valueOf(coordinates[6]), testLineSegmentHandler.getCoordinates().get(2).getLeft());
        assertEquals(Double.valueOf(coordinates[7]), testLineSegmentHandler.getCoordinates().get(2).getRight());

        assertEquals(Double.valueOf(coordinates[10]), testLineSegmentHandler.getCoordinates().get(3).getLeft());
        assertEquals(Double.valueOf(coordinates[11]), testLineSegmentHandler.getCoordinates().get(3).getRight());
    }

}
