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
package de.interactive_instruments.etf.bsxm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.interactive_instruments.IFile;
import de.interactive_instruments.etf.bsxm.topox.geojson.writer.GeoJsonWriter;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class GeoJsonWriterTest {

    @Test
    public void testWriteFeatureFile() throws IOException {
        final IFile tmpFile = IFile.createTempFile("etf", "junit");
        final GeoJsonWriter writer = new GeoJsonWriter(tmpFile);
        writer.init();

        writer.startFeature("ID.1");
        writer.addPolygonCoordinates("9 9 2 2 1 1".getBytes());
        writer.addPolygonCoordinates("1 1 123 4123".getBytes());
        writer.addPolygonCoordinates("1 1 2 2".getBytes());
        writer.addPolygonCoordinates("2 2 9 9".getBytes());

        writer.close();

        assertEquals(
                "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":"
                        + "\"Feature\",\"properties\":{\"id\":\"ID.1\"},\"geometry\":"
                        + "{\"type\":\"Polygon\",\"coordinates\":[[[9,9],[2,2],[1,1],[123,4123],[1,1],[2,2],[9,9]]]}}]}",
                tmpFile.readContent().toString());
    }

    @Test
    public void testWritePointFile() throws IOException {
        final IFile tmpFile = IFile.createTempFile("etf", "junit");
        final GeoJsonWriter writer = new GeoJsonWriter(tmpFile);
        writer.init();

        writer.writePOI("ID.1", "error", "1", "2");

        writer.close();

        assertEquals(
                "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\","
                        + "\"properties\":{\"id\":\"ID.1\",\"e\":\"error\"},"
                        + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[1,2]}}]}",
                tmpFile.readContent().toString());
    }

    @Test
    public void testWriteFileMultiple() throws IOException {
        final IFile tmpFile = IFile.createTempFile("etf", "junit");
        final GeoJsonWriter writer = new GeoJsonWriter(tmpFile);
        writer.init();

        writer.writePOI("ID.0", "error", "1", "2");

        writer.startFeature("ID.1");
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());

        writer.startFeature("ID.2");
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());

        writer.startFeature("ID.3");
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());
        writer.nextPolygonInterior();
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());

        writer.startFeature("ID.4");
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());
        writer.nextPolygonInterior();
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());
        writer.nextPolygonInterior();
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());
        writer.addPolygonCoordinates("11414 2134214 431 4123".getBytes());

        writer.close();

        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[{"
                + "\"type\":\"Feature\",\"properties\":{\"id\":\"ID.0\",\"e\":\"error\"},"
                + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[1,2]}},"
                + "{\"type\":\"Feature\",\"properties\":{\"id\":\"ID.1\"},"
                + "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
                + "[[[11414,2134214],[431,4123]]]}},{\"type\":\"Feature\","
                + "\"properties\":{\"id\":\"ID.2\"},\"geometry\":{\"type\":"
                + "\"Polygon\",\"coordinates\":[[[11414,2134214],[431,4123],"
                + "[11414,2134214],[431,4123]]]}},{\"type\":\"Feature\","
                + "\"properties\":{\"id\":\"ID.3\"},\"geometry\":{\"type\":\"Polygon\","
                + "\"coordinates\":[[[11414,2134214],[431,4123],[11414,2134214],[431,4123]],"
                + "[[11414,2134214],[431,4123]]]}},{\"type\":\"Feature\",\"properties\":{"
                + "\"id\":\"ID.4\"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
                + "[[[11414,2134214],[431,4123],[11414,2134214],[431,4123]],[[11414,2134214],"
                + "[431,4123],[11414,2134214],[431,4123]],[[11414,2134214],[431,4123],"
                + "[11414,2134214],[431,4123]]]}}]}",
                tmpFile.readContent().toString());
    }
}
