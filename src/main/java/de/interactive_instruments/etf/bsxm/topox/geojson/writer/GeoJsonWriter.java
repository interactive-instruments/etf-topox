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
package de.interactive_instruments.etf.bsxm.topox.geojson.writer;

import java.io.*;

import de.interactive_instruments.IFile;
import de.interactive_instruments.Releasable;
import de.interactive_instruments.exceptions.ExcUtils;

/**
 * A writer for GeoJson
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class GeoJsonWriter implements Closeable, Releasable {

    private final BufferedWriter writer;
    private final static String initalStructure = "{\"type\":\"FeatureCollection\",\"features\":[";
    private final IFile file;
    private int previousHash;

    enum WritingMode {
        INIT, FEATURE_POI, FEATURE, POLYGON_COORDINATES, POLYGON_COORDINATES_ORIENTATION_SWITCH, POLYGON_COORDINATES_INTERIOR, CURVE_COORDINATES, DONE
    }

    private WritingMode mode = WritingMode.INIT;

    /**
     * Create a new GeoJson Writer
     *
     * @param file
     *            output file
     *
     * @throws IOException
     *             if output file could not be created
     */
    public GeoJsonWriter(final IFile file) throws IOException {
        this.file = file;
        file.expectFileIsWritable();
        this.writer = new BufferedWriter(new FileWriter(file));
        previousHash = 0;
    }

    public IFile getFile() {
        return this.file;
    }

    public void init() throws IOException {
        writer.write(initalStructure);
        mode = WritingMode.INIT;
    }

    /**
     * Write a Point of Interest as a single feature which possesses: - a point geometry and - a text property
     *
     * The feature is persisted after this call, no further calls are required. After calling this method, a new POI feature
     * can be created by calling this method again, or a feature with a Polygon or a LineString geometry can be created with
     * the {@link #startFeature(String)} method.
     *
     * @param id
     *            ID of the feature
     * @param text
     *            text property
     * @param xCoordinate
     *            X coordinate of the point geometry
     * @param yCoordinate
     *            Y coordinate of the point geometry
     * @throws IOException
     *             if writing to the file failed
     */
    public void writePOI(final String id, final String text, final String xCoordinate, final String yCoordinate)
            throws IOException {
        switch (mode) {
        case DONE:
        case FEATURE:
        case POLYGON_COORDINATES_ORIENTATION_SWITCH:
            throw new IllegalStateException("MODE: " + mode + " ID: " + id);
        case POLYGON_COORDINATES:
        case POLYGON_COORDINATES_INTERIOR:
            endPolygonGeometry();
        case CURVE_COORDINATES:
            endGeometryAndFeature();
        case FEATURE_POI:
            writer.write(',');
        case INIT:
        }
        mode = WritingMode.FEATURE_POI;
        writer.write("{\"type\":\"Feature\","
                + "\"properties\":{\"id\":\"");
        writer.write(id);
        writer.write("\",\"e\":\"");
        writer.write(text);
        writer.write("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[");
        writer.write(xCoordinate);
        writer.write(',');
        writer.write(yCoordinate);
        writer.write("]}}");

    }

    /**
     * Start writing a feature to add a polygon or a curve geometry.
     *
     * There is NO endPolygonFeature() method, the Feature definition is automatically completed by calling any other method
     * of this object. However, this means that at least the {@link #close()} method must be called, to complete the last
     * feature.
     *
     * Note: after this call {@link #addPolygonCoordinates(byte[])} or {@link #addPolygonCoordinates(byte[])} must be
     * called.
     *
     * @param id
     * @throws IOException
     */
    public void startFeature(final String id) throws IOException {
        switch (mode) {
        case DONE:
        case FEATURE:
        case POLYGON_COORDINATES_ORIENTATION_SWITCH:
            throw new IllegalStateException("MODE: " + mode + " ID: " + id);
        case POLYGON_COORDINATES:
        case POLYGON_COORDINATES_INTERIOR:
            endPolygonGeometry();
        case CURVE_COORDINATES:
            endGeometryAndFeature();
        case FEATURE_POI:
            writer.write(',');
        case INIT:
        }
        mode = WritingMode.FEATURE;
        previousHash = 0;
        writer.write("{\"type\":\"Feature\","
                + "\"properties\":{\"id\":\"");
        writer.write(id);
        writer.write("\"},\"geometry\":{\"type\":\"");
    }

    public void addPolygonCoordinates(final byte[] data) throws IOException {
        switch (mode) {
        case DONE:
        case INIT:
        case FEATURE_POI:
        case CURVE_COORDINATES:
            throw new IllegalStateException("MODE: " + mode);
        case FEATURE:
            writer.write("Polygon\",\"coordinates\":[[");
            mode = WritingMode.POLYGON_COORDINATES;
            break;
        case POLYGON_COORDINATES_INTERIOR:
        case POLYGON_COORDINATES:
            writer.write(",");
            break;
        case POLYGON_COORDINATES_ORIENTATION_SWITCH:
            mode = WritingMode.POLYGON_COORDINATES_INTERIOR;
            break;
        }
        writeCoordinates(data);
    }

    private void writeCoordinates(final byte[] data) throws IOException {
        writer.write('[');

        boolean tuple = false;
        boolean written = false;
        int hash = 13;
        int startPos = 0;
        final char[] buffer = new char[data.length];
        for (int i = 0; i < data.length; i++) {
            if (data[i] == ' ') {
                if (tuple) {
                    if (hash != previousHash) {
                        if (written) {
                            writer.write(',');
                            writer.write('[');
                        }
                        writer.write(buffer, startPos, i - startPos);
                        writer.write(']');
                        written = true;
                    }
                    previousHash = hash;
                    hash = 13;
                    startPos = i + 1;
                    tuple = false;
                } else {
                    tuple = true;
                }
                buffer[i] = ',';
            } else {
                hash ^= data[i];
                hash *= 31;
                buffer[i] = (char) data[i];
            }
        }
        if (tuple && hash != previousHash) {
            if (written) {
                writer.write(',');
                writer.write('[');
            }
            writer.write(buffer, startPos, data.length - startPos);
            writer.write(']');
        }
        previousHash = hash;
    }

    public void nextPolygonInterior() throws IOException {
        switch (mode) {
        case DONE:
        case INIT:
        case FEATURE:
        case FEATURE_POI:
        case POLYGON_COORDINATES_ORIENTATION_SWITCH:
            /**
             * No exterior boundary, in this case all boundaries shall be listed as interior boundaries.
             */
        case POLYGON_COORDINATES:
        case POLYGON_COORDINATES_INTERIOR:
            writer.write("],[");
        }
        mode = WritingMode.POLYGON_COORDINATES_ORIENTATION_SWITCH;
    }

    public void addCurveCoordinates(final byte[] data) throws IOException {
        switch (mode) {
        case DONE:
        case INIT:
        case FEATURE_POI:
        case POLYGON_COORDINATES_INTERIOR:
        case POLYGON_COORDINATES_ORIENTATION_SWITCH:
        case POLYGON_COORDINATES:
            throw new IllegalStateException("MODE: " + mode);
        case FEATURE:
            writer.write("LineString\",\"coordinates\":[");
            break;
        case CURVE_COORDINATES:
            writer.write(",");
        }
        mode = WritingMode.CURVE_COORDINATES;
        writeCoordinates(data);
    }

    private void endPolygonGeometry() throws IOException {
        writer.write(']');
    }

    private void endGeometryAndFeature() throws IOException {
        writer.write("]}}");
    }

    @Override
    public void release() {
        try {
            close();
        } catch (IOException ign) {
            ExcUtils.suppress(ign);
        }
    }

    @Override
    public void close() throws IOException {
        switch (mode) {
        case DONE:
            break;
        case POLYGON_COORDINATES:
        case POLYGON_COORDINATES_INTERIOR:
            endPolygonGeometry();
        case CURVE_COORDINATES:
            endGeometryAndFeature();
        case INIT:
        case FEATURE:
        case FEATURE_POI:
        case POLYGON_COORDINATES_ORIENTATION_SWITCH:
        default:
            writer.write("]}");
            writer.close();
            mode = WritingMode.DONE;
        }
    }
}
