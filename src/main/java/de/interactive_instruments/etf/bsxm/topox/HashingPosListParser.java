/**
 * Copyright 2010-2019 interactive instruments GmbH
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
 * A parser for direct positions of geometric objects that generates hashes for the input data
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class HashingPosListParser implements PosListParser {

    private double previousOrdinate;
    private boolean threeDCoordinates = false;

    private static final long FNV_64_INIT = 0xcbf29ce484222325L;
    private static final long FNV_64_PRIME = 0x100000001b3L;

    private final HashingSegmentHandler[] geoTypeHandlerStrategies;

    // more than enough for lat/lon
    private final static int PRECALC_POW_SIZE = 12;
    private final static double[] PRECALC_POS_EXPS = new double[PRECALC_POW_SIZE];
    private final static double[] PRECALC_NEG_EXPS = new double[PRECALC_POW_SIZE];
    static {
        for (int i = 0; i < PRECALC_POW_SIZE; i++) {
            PRECALC_POS_EXPS[i] = Math.pow(10., i);
            PRECALC_NEG_EXPS[i] = Math.pow(10., -i);
        }
    }

    private static double pow10(final int exp) {
        if (exp > -PRECALC_POW_SIZE) {
            if (exp <= 0) {
                return PRECALC_NEG_EXPS[-exp];
            } else if (exp < PRECALC_POW_SIZE) {
                return PRECALC_POS_EXPS[exp];
            }
        }
        return Math.pow(10., exp);
    }

    private static class BufferedGeoArcHandlerStrategy implements HashingSegmentHandler {

        private final HashingSegmentHandler handler;
        private final double coordinateBuffer[];
        private final long hashesAndLocationsBuffer[];
        private int i;

        private BufferedGeoArcHandlerStrategy(final HashingSegmentHandler handler) {
            this.handler = handler;
            coordinateBuffer = new double[6];
            hashesAndLocationsBuffer = new long[6];
        }

        @Override
        public void coordinate2d(final double x, final double y, final long hash, final long location, final int type) {
            coordinateBuffer[i] = x;
            coordinateBuffer[i + 1] = y;
            hashesAndLocationsBuffer[i] = hash;
            hashesAndLocationsBuffer[++i] = location;
            if (++i > 4) {
                i = 0;
                handler.coordinates2d(coordinateBuffer, hashesAndLocationsBuffer, type);
            }
        }

        @Override
        public void coordinates2d(final double[] coordinates, final long[] hashesAndLocations, final int type) {
            throw new IllegalAccessError("Invalid call");
        }

        @Override
        public void nextGeometricObject() {
            i = 0;
        }
    }

    private static class HashingPassThroughHandlerStrategy implements HashingSegmentHandler {

        private final HashingSegmentHandler handler;
        private long previousCoordinateHash;

        private HashingPassThroughHandlerStrategy(final HashingSegmentHandler handler) {
            this.handler = handler;
        }

        @Override
        public void coordinate2d(final double x, final double y, final long hash, final long location, final int type) {
            if (hash != previousCoordinateHash) {
                handler.coordinate2d(x, y, hash, location, type);
                previousCoordinateHash = hash;
            }
        }

        @Override
        public void coordinates2d(final double[] coordinates, final long[] hashesAndLocations, final int type) {
            throw new IllegalAccessError("Invalid call");
        }

        @Override
        public void nextGeometricObject() {
            previousCoordinateHash = Long.MAX_VALUE;
        }
    }

    public HashingPosListParser(final HashingSegmentHandler hashingSegmentHandler) {
        geoTypeHandlerStrategies = new HashingSegmentHandler[3];
        geoTypeHandlerStrategies[0] = hashingSegmentHandler;
        // geoTypeHandlerStrategies[1] = new BufferedGeoArcHandlerStrategy(hashingSegmentHandler);
        geoTypeHandlerStrategies[1] = new HashingPassThroughHandlerStrategy(hashingSegmentHandler);
        geoTypeHandlerStrategies[2] = new HashingPassThroughHandlerStrategy(hashingSegmentHandler);
    }

    @Override
    public void parseDirectPositions(final byte[] byteSequence, final long location, final int geoType) {
        parseDirectPositions(byteSequence, this.threeDCoordinates, location, geoType);
    }

    @Override
    public void parseDirectPositions(final CharSequence sequence, final long location, final int geoType) {
        parseDirectPositions(sequence, this.threeDCoordinates, location, geoType);
    }

    @Override
    public void parseDirectPositions(final byte[] bytes, final boolean threeDCoordinates, final long location,
            final int geoType) {

        final HashingSegmentHandler segmentHandler = this.geoTypeHandlerStrategies[geoType];

        int pos = 0;
        int length = bytes.length - pos;

        // skip leading whitespaces
        byte b = bytes[pos];
        while (length > 0 && Character.isWhitespace(b)) {
            b = bytes[++pos];
            length--;
        }
        int ordinateCounter = 0;
        long hash = FNV_64_INIT;

        while (length > 0) {
            boolean positiveSign = true;
            if (b == '+') {
                pos++;
                length--;
            } else if (b == '-') {
                positiveSign = false;
                pos++;
                length--;
            }
            hash ^= b;
            hash *= FNV_64_PRIME;

            boolean err = true;
            int startOffset = pos;
            double d;
            for (d = 0d; (length > 0) && ((b = bytes[pos]) >= '0') && (b <= '9');) {
                d *= 10d;
                d += b - '0';
                hash ^= b;
                hash *= FNV_64_PRIME;
                pos++;
                length--;
            }

            if (pos - startOffset > 0) {
                err = false;
            }

            double number = d;
            if ((length > 0) && (bytes[pos] == '.')) {

                hash ^= '.';
                hash *= FNV_64_PRIME;
                startOffset = ++pos;
                length--;

                for (d = 0d; (length > 0) && ((b = bytes[pos]) >= '0') && (b <= '9');) {
                    d *= 10d;
                    d += b - '0';
                    hash ^= b;
                    hash *= FNV_64_PRIME;
                    pos++;
                    length--;
                }
                final int fracLength = pos - startOffset;
                if (fracLength > 0) {
                    number += pow10(-fracLength) * d;
                    err = false;
                }
            }

            if (err) {
                // continue;

                // TODO error collector
                throw new NumberFormatException("Invalid Double : " + new String(bytes));
            }

            while (--length > 0) {
                b = bytes[++pos];
                if (!Character.isWhitespace(b)) {
                    break;
                }
            }

            if (++ordinateCounter % 2 == 0) {
                segmentHandler.coordinate2d(previousOrdinate, positiveSign ? number : -number, hash, location, geoType);
                hash = FNV_64_INIT;
            } else {
                previousOrdinate = positiveSign ? number : -number;
                hash *= FNV_64_PRIME;
            }
        }
    }

    @Override
    public void parseDirectPositions(final CharSequence csq, final boolean threeDCoordinates, final long location,
            final int geoType) {

        final HashingSegmentHandler segmentHandler = this.geoTypeHandlerStrategies[geoType];
        int pos = 0;
        int length = csq.length() - pos;

        // skip leading whitespaces
        char ch = csq.charAt(pos);
        while (length > 0 && Character.isWhitespace(ch)) {
            ch = csq.charAt(++pos);
            length--;
        }
        int ordinateCounter = 0;
        long hash = FNV_64_INIT;

        while (length > 0) {
            boolean positiveSign = true;
            if (ch == '+') {
                pos++;
                length--;
            } else if (ch == '-') {
                positiveSign = false;
                pos++;
                length--;
            }
            hash ^= ch;
            hash *= FNV_64_PRIME;

            boolean err = true;
            int startOffset = pos;
            double d;
            for (d = 0d; (length > 0) && ((ch = csq.charAt(pos)) >= '0') && (ch <= '9');) {
                d *= 10d;
                d += ch - '0';
                hash ^= ch;
                hash *= FNV_64_PRIME;
                pos++;
                length--;
            }

            if (pos - startOffset > 0) {
                err = false;
            }

            double number = d;
            if ((length > 0) && (csq.charAt(pos) == '.')) {

                hash ^= '.';
                hash *= FNV_64_PRIME;
                startOffset = ++pos;
                length--;

                for (d = 0d; (length > 0) && ((ch = csq.charAt(pos)) >= '0') && (ch <= '9');) {
                    d *= 10d;
                    d += ch - '0';
                    hash ^= ch;
                    hash *= FNV_64_PRIME;
                    pos++;
                    length--;
                }
                final int fracLength = pos - startOffset;
                if (fracLength > 0) {
                    number += pow10(-fracLength) * d;
                    err = false;
                }
            }

            if (err) {
                // continue;
                // TODO error collector
                throw new NumberFormatException("Invalid Double : " + csq);
            }

            while (--length > 0) {
                ch = csq.charAt(++pos);
                if (!Character.isWhitespace(ch)) {
                    break;
                }
            }

            if (++ordinateCounter % 2 == 0) {
                segmentHandler.coordinate2d(previousOrdinate, positiveSign ? number : -number, hash, location, geoType);
                hash = FNV_64_INIT;
            } else {
                previousOrdinate = positiveSign ? number : -number;
                hash *= FNV_64_PRIME;
            }
        }
    }

    @Override
    public void dimension(final boolean threeDCoordinates) {
        this.threeDCoordinates = threeDCoordinates;
    }

    @Override
    public void nextGeometricObject() {
        previousOrdinate = Double.NaN;
        geoTypeHandlerStrategies[0].nextGeometricObject();
        geoTypeHandlerStrategies[1].nextGeometricObject();
        geoTypeHandlerStrategies[2].nextGeometricObject();
    }
}
