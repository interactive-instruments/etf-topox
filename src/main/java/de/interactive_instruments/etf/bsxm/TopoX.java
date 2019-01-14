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
package de.interactive_instruments.etf.bsxm;

import static de.interactive_instruments.etf.bsxm.topox.TopologyBuilder.compress;
import static de.interactive_instruments.etf.bsxm.topox.TopologyBuilder.getRight;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.Manifest;

import javax.management.*;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.basex.core.BaseXException;
import org.basex.query.QueryModule.Deterministic;
import org.basex.query.QueryModule.Permission;
import org.basex.query.QueryModule.Requires;
import org.basex.query.value.node.DBNode;

import de.interactive_instruments.IFile;
import de.interactive_instruments.etf.bsxm.topox.*;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.properties.PropertyUtils;

/**
 * TopoX facade.
 *
 * Not thread safe.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class TopoX {

    // Used to check if a name already exists and to avoid file name conflicts.
    private final Set<String> themeNames = new HashSet();
    private final List<Theme> themes = new ArrayList();

    // Used to avoid conflicts between the Theme and BoundaryBuilder IDs
    private final static int BOUNDARY_ID_OFFSET = 4096;
    private final List<BoundaryBuilder> boundaries = new ArrayList();

    // For example "DB-"
    private String dbnamePrefix;

    // Length of the database name. 6 for "DB-000"
    private int dbNameLength = 0;

    // Current BaseX pre value in a context
    private int currentObjectPre;

    private final MBeanServer mBeanServer;

    public TopoX() {
        if (PropertyUtils.getenvOrProperty("ETF_AM_TOPOX_MB", "false").equals("true")) {
            mBeanServer = ManagementFactory.getPlatformMBeanServer();
        } else {
            mBeanServer = null;
        }
    }

    /**
     * Sets the database name prefix internally in the TopoX module.
     *
     * Database names must be suffixed with a three digits index, i.e. DB-000 .
     *
     * @param name
     *            full database name ( i.e. DB-000 )
     * @param dbCount
     *            number of databases that will be used TODO: not used yet, use it to pre calculate initialEdgeCapacity
     * @return full database name
     * @throws BaseXException
     *             if database name is not suffixed with a three digits index
     */
    @Requires(Permission.READ)
    public String initDb(final String name, final short dbCount) throws BaseXException {
        if (name == null || name.length() < 4) {
            throw new BaseXException("Invalid database name: '" + name + "'. "
                    + "Database names must be suffixed with a three digits index, i.e. DB-000");
        }
        final int length = name.length();
        for (int i = length - 1; i >= length - 3; i--) {
            if (name.charAt(i) < '0' || name.charAt(i) > '9') {
                throw new BaseXException("Invalid database name: '" + name + "'. "
                        + "Database names must be suffixed with a three digits index, i.e. DB-000");
            }
        }
        this.dbnamePrefix = name.substring(0, length - 3);
        this.dbNameLength = length;
        return name;
    }

    // Builder creation
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Creates a new topology builder
     *
     * @param themeName
     *            name of the topological name
     * @param initialEdgeCapacity
     *            xpected number of edges. This value should be about 1995000 * number of databases (experience value from tests). The number is used to allocate the data structures accordingly and to increase the performance.
     * @param outputDir
     *            directory for storing error information
     * @return ID of the topology name
     * @throws BaseXException
     *             if the $tempOutputDir directory cannot be used to write files or if the name name already exists.
     */
    @Requires(Permission.CREATE)
    public int newTopologyBuilder(final String themeName, final int initialEdgeCapacity, final String outputDir)
            throws BaseXException {
        if (!themeNames.add(themeName)) {
            throw new BaseXException("Invalid theme name: already exists.");
        }
        try {
            final XMLOutputFactory xof = XMLOutputFactory.newInstance();
            final File errorOutputDir = new File(outputDir);

            final IFile geoJsonOutputFile = new IFile(errorOutputDir, themeName + ".js");
            final GeoJsonWriter writer = new GeoJsonWriter(geoJsonOutputFile);
            writer.init();

            final File errorOutputFile = new File(errorOutputDir, themeName + ".xml");
            final XMLStreamWriter streamWriter = xof.createXMLStreamWriter(new FileOutputStream(errorOutputFile), "UTF-8");
            final TopologyErrorXmlWriter topologyErrorCollector = new TopologyErrorXmlWriter(themeName, streamWriter);
            final TopologyBuilder topologyBuilder = new TopologyBuilder(themeName, topologyErrorCollector, initialEdgeCapacity);
            topologyErrorCollector.init();

            final Theme theme = new Theme(themeName, topologyErrorCollector, errorOutputFile.toString(), writer,
                    topologyBuilder);
            themes.add(theme);
            if (mBeanServer != null) {
                try {
                    final ObjectName name = new ObjectName("topox:topology=" + themeName);
                    if (mBeanServer.isRegistered(name)) {
                        mBeanServer.unregisterMBean(name);
                    }
                    mBeanServer.registerMBean(theme.getMBean(), name);
                } catch (MalformedObjectNameException | InstanceNotFoundException | InstanceAlreadyExistsException
                        | MBeanRegistrationException | NotCompliantMBeanException ign) {
                    ExcUtils.suppress(ign);
                }
            }
            return themes.size() - 1;
        } catch (final IOException | XMLStreamException e) {
            throw new BaseXException(e);
        }
    }

    /**
     * Can be used for dev purposes. Does not override the error output file and writes all errors to System.out.
     *
     * @param themeName
     *            name of the topological name
     * @param initialEdgeCapacity
     *            xpected number of edges. This value should be about 1995000 * number of databases (experience value from tests). The number is used to allocate the data structures accordingly and to increase the performance.
     * @param outputDir
     *            directory for storing error information
     * @return ID of the topology name
     * @throws BaseXException
     *             if the $tempOutputDir directory cannot be used to write files or if the name name already exists.
     */
    @Deprecated
    @Requires(Permission.CREATE)
    public int devTopologyBuilder(final String themeName, final int initialEdgeCapacity, final String outputDir)
            throws BaseXException {
        if (!themeNames.add(themeName)) {
            throw new BaseXException("Invalid theme name: already exists.");
        }

        try {
            final File errorOutputDir = new File(outputDir);
            final IFile geoJsonOutputFile = new IFile(errorOutputDir, themeName + ".js");
            final GeoJsonWriter writer = new GeoJsonWriter(geoJsonOutputFile);
            writer.init();

            final File errorOutputFile = new File(errorOutputDir, themeName + ".noxml");
            final XMLOutputFactory xof = XMLOutputFactory.newInstance();
            final TopologyErrorXmlWriter topologyErrorCollector = new TopologyErrorXmlWriter(themeName,
                    xof.createXMLStreamWriter(System.out));
            final TopologyBuilder topologyBuilder = new TopologyBuilder(themeName, topologyErrorCollector, 16);

            themes.add(new Theme(themeName, topologyErrorCollector, errorOutputFile.toString(), writer, topologyBuilder));
            return themes.size() - 1;
        } catch (final IOException | XMLStreamException e) {
            throw new BaseXException(e);
        }
    }

    /**
     * Creates a new object for checking boundaries and their overlapping.
     *
     * A boundary is exactly on an edge. There must be no overlap with another boundary otherwise an error is reported. In order to lay several boundaries over one edge, several independent boundary checking objects must be created.
     *
     * Requires an initialized topology object that has already captured topological information.
     *
     * @param topologyId
     *            ID of the topology
     *
     * @return ID of the boundary check object
     * @throws BaseXException
     *             if the $topologyId is unknown
     */
    @Requires(Permission.CREATE)
    public int newBoundaryBuilder(final int topologyId)
            throws BaseXException {
        if (topologyId < 0 || topologyId >= themes.size()) {
            throw new BaseXException("Unknown topology ID: " + String.valueOf(topologyId));
        }
        this.boundaries.add(new BoundaryBuilder(themes.get(topologyId)));
        return this.boundaries.size() - 1 + BOUNDARY_ID_OFFSET;
    }

    // Parsing
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Switch the Topology Builder to the next Feature
     *
     * This means that the object pre value is temporary saved for other parsing operations in this features context (and avoids passing the value as argument).
     *
     * @param id
     *            ID of Topology Builder
     * @param object
     *            geometric object
     * @return input object
     */
    @Requires(Permission.NONE)
    public DBNode nextFeature(final int id, final DBNode object) {
        currentObjectPre = object.pre();
        return object;
    }

    // Topological data parsing
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Switch the Topology Builder to the next geometric object
     *
     * @param id
     *            ID of Topology Builder
     */
    @Requires(Permission.NONE)
    public void nextGeometricObject(final int id) {
        themes.get(id).parser.nextGeometricObject();
    }

    /**
     * Parse the segment of a geometric object
     *
     * Requires that the current object was previously set by calling {@link #nextFeature(int, DBNode)}
     *
     * @param id
     *            ID of Topology Builder
     * @param posList
     *            gml posList
     * @param type
     *            gml type: 1 for arc 2 for all others
     */
    @Requires(Permission.READ)
    public void parseSegment(final int id, final DBNode posList, final int type) {
        themes.get(id).parser.parseDirectPositions(posList.data().text(posList.pre(), true), false, genIndex(posList), type);
    }

    /**
     * Switch the Topology Builder to the next interior
     *
     * @param id
     *            ID of Topology Builder
     */
    @Requires(Permission.NONE)
    public void nextInterior(final int id) {
        themes.get(id).nextInterior();
    }

    /**
     * Detect holes
     *
     * @param id
     *            ID of Topology Builder
     */
    @Deterministic
    @Requires(Permission.NONE)
    public int detectHoles(final int id) {
        return themes.get(id).detectHoles();
    }

    /**
     * Detect free-standing surfaces
     *
     * @param id
     *            ID of Topology Builder
     */
    @Deterministic
    @Requires(Permission.NONE)
    public int detectFreeStandingSurfaces(final int id) {
        return themes.get(id).detectFreeStandingSurfaces();
    }

    // Border parsing
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Switch the Boundary Builder to the next geometric object
     *
     * @param id
     *            ID of Boundary Builder
     */
    @Requires(Permission.NONE)
    public void nextBoundaryObject(final int id) {
        boundaries.get(id - BOUNDARY_ID_OFFSET).parser.nextGeometricObject();
    }

    /**
     * Parse the geometry of a boundary object
     *
     * Requires that the current object was previously set by calling {@link #nextFeature(int, DBNode)}
     *
     * @param id
     *            ID of Boundary Builder
     * @param geo
     *            gml geometry
     */
    @Requires(Permission.READ)
    public void parseBoundary(final int id, final DBNode geo) {
        // geotype 2: use pass through handler
        boundaries.get(id - BOUNDARY_ID_OFFSET).parser.parseDirectPositions(geo.data().text(geo.pre(), true), false,
                genIndex(geo), 2);
    }

    // Error output
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the error file as string
     *
     * Note: no other function may be called after calling this function.
     *
     * @param id
     *            ID of Topology Builder
     * @return path to error file
     */
    @Deterministic
    @Requires(Permission.NONE)
    public String errorFile(final int id) {
        themes.get(id).topologyErrorCollector.release();
        return themes.get(id).errorFile;
    }

    /**
     * Writing a new feature with a point geometry and an error message property.
     *
     * @param id
     *            ID of Topology Builder
     * @param errorId
     *            error message ID
     * @param error
     *            error message
     * @param x
     *            X coordinate of the point geometry
     * @param y
     *            Y coordinate of the point geometry
     *
     * @throws IOException
     *             if the geometry cannot be written
     * @throws IllegalStateException
     *             if this method has been called in the wrong context
     */
    @Requires(Permission.NONE)
    public void writeGeoJsonPOI(final int id, final String errorId, final String error, final String x, final String y)
            throws IOException {
        themes.get(id).geoJsonWriter.writePOI(errorId, error, x, y);
    }

    /**
     * Start writing a new Feature with either a Polygon or a Curve geometry.
     *
     * @param id
     *            ID of Topology Builder
     * @param featureId
     *            ID of the feature
     *
     * @throws IOException
     *             if the geometry cannot be written
     * @throws IllegalStateException
     *             if this method has been called in the wrong context
     */
    @Requires(Permission.NONE)
    public void startGeoJsonFeature(final int id, final String featureId) throws IOException {
        themes.get(id).geoJsonWriter.startFeature(featureId);
    }

    /**
     * Write coordinates for a polygon geometry.
     *
     * Must be called after the {@link #startGeoJsonFeature(int, String)} or the {@link #nextGeoJsonPolygonInterior(int)} method. Depending on the previous calls, exterior or interior coordinates are written.
     *
     * @param id
     *            ID of Topology Builder
     * @param coordinateNode
     *            the database node
     *
     * @throws IOException
     *             if the geometry cannot be written
     * @throws IllegalStateException
     *             if this method has been called in the wrong context
     */
    @Requires(Permission.NONE)
    public void addGeoJsonPolygonCoordinates(final int id, final DBNode coordinateNode) throws IOException {
        themes.get(id).geoJsonWriter.addPolygonCoordinates(coordinateNode.data().text(coordinateNode.pre(), true));
    }

    /**
     * Write the next coordinates as interior coordinates for a Polygon.
     *
     * Must be called after the {@link #addGeoJsonPolygonCoordinates(int, DBNode)} or this method.
     *
     * @param id
     *            ID of Topology Builder
     *
     * @throws IOException
     *             if the geometry cannot be written
     * @throws IllegalStateException
     *             if this method has been called in the wrong context
     */
    @Requires(Permission.NONE)
    public void nextGeoJsonPolygonInterior(final int id) throws IOException {
        themes.get(id).geoJsonWriter.nextPolygonInterior();
    }

    /**
     * Write the coordinates for a Curve geometry.
     *
     * Must be called after the {@link #startGeoJsonFeature(int, String)} or this method.
     *
     * @param id
     *            ID of Topology Builder
     * @param coordinateNode
     *            the database node
     *
     * @throws IOException
     *             if the geometry cannot be written
     * @throws IllegalStateException
     *             if this method has been called in the wrong context
     */
    @Requires(Permission.NONE)
    public void addGeoJsonCurveCoordinates(final int id, final DBNode coordinateNode) throws IOException {
        themes.get(id).geoJsonWriter.addCurveCoordinates(coordinateNode.data().text(coordinateNode.pre(), true));
    }

    /**
     * Write the GeoJson map as attachment
     *
     * @param id
     *            ID of Topology Builder
     * @param geoJsonAttachmentId
     *            attachment ID
     *
     * @throws IOException
     *             if the map cannot be written
     */
    @Requires(Permission.CREATE)
    public void attachIssueMap(final int id, final String geoJsonAttachmentId) throws IOException {
        final GeoJsonWriter geoJsonWriter = themes.get(id).geoJsonWriter;
        try {
            geoJsonWriter.close();
        } catch (final IOException ignore) {
            ExcUtils.suppress(ignore);
        }
        final String htmlFileName = themes.get(id).name + "_Map.html";
        final File dir = geoJsonWriter.getFile().getParentFile();
        final IFile attachmentMapFile = new IFile(dir, htmlFileName);

        geoJsonWriter.getFile().moveTo(dir.getPath() + File.separator + Objects.requireNonNull(geoJsonAttachmentId));

        final InputStream cStream = this.getClass().getResourceAsStream("/html/IssueMap.html");
        final InputStream stream;
        if (cStream == null) {
            stream = this.getClass().getClassLoader().getResourceAsStream("/html/IssueMap.html");
        } else {
            stream = cStream;
        }

        // could be optimized
        final Scanner s = new Scanner(stream).useDelimiter("\\A");
        final String result = s.hasNext() ? s.next() : "";
        attachmentMapFile.writeContent(new StringBuffer(
                result.replaceAll("'out.js'", "'" + geoJsonAttachmentId + "'")));
    }

    // Info output
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Deterministic
    @Requires(Permission.READ)
    public String version() {
        try {
            return this.getClass().getPackage().getImplementationVersion();
        } catch (final Exception e) {
            return "unknown";
        }
    }

    @Deterministic
    @Requires(Permission.READ)
    public String detailedVersion() {
        try {
            final URLClassLoader cl = (URLClassLoader) getClass().getClassLoader();
            final URL url = cl.findResource("META-INF/MANIFEST.MF");
            final Manifest manifest = new Manifest(url.openStream());
            final String version = manifest.getMainAttributes().getValue("Implementation-Version");
            final String buildTime = manifest.getMainAttributes().getValue("Build-Date").substring(2);
            return version + "-b" + buildTime;
        } catch (Exception E) {
            return "unknown";
        }
    }

    @Requires(Permission.ADMIN)
    public String diag(final int id) {
        return themes.get(id).toString();
    }

    // pre value, database and object encoding
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Get the pre ID from a compressed long TopoX index
     *
     * @param compressedIndex
     *            TopoX index
     * @return BaseX integer pre value of the geometric object
     */
    @Deterministic
    @Requires(Permission.NONE)
    public static int pre(final long compressedIndex) {
        return getRight(compressedIndex);
    }

    /**
     * Get the pre ID from a compressed long TopoX index
     *
     * @param compressedIndex
     *            TopoX index
     * @return BaseX integer pre value of the object
     */
    @Deterministic
    @Requires(Permission.NONE)
    public static int preObject(final long compressedIndex) {
        return getRight(compressedIndex) -
                objectIndex(compressedIndex);
    }

    /**
     * Get the full database name from a compressed long TopoX index
     *
     * Note: initDb has to be called first
     *
     * @param compressedIndex
     *            TopoX index
     * @return full database name
     */
    @Deterministic
    @Requires(Permission.NONE)
    public String dbname(final long compressedIndex) {
        final String dbIndexStr = Integer.toString(dbIndex(compressedIndex));
        final StringBuilder sb = new StringBuilder(this.dbnamePrefix.length() + 3);
        sb.append(this.dbnamePrefix);
        final int pads = 3 - dbIndexStr.length();
        if (pads > 0) {
            for (int i = pads - 1; i >= 0; i--) {
                sb.append('0');
            }
        }
        return sb.append(dbIndexStr).toString();
    }

    /**
     * Regenerate the object pre as
     *
     * Can be used as key in XQuery maps
     *
     * @param compressedIndex
     *            TopoX index
     * @return BaseX integer pre value
     */
    @Deterministic
    @Requires(Permission.NONE)
    public static long objPreAsGeoPre(final long compressedIndex) {
        return compress(
                dbIndex(compressedIndex) << 24,
                getRight(compressedIndex) -
                        objectIndex(compressedIndex));
    }

    private static int makeCompressedNodeIndex(final byte dbIndex, final int objectGeoDiffIndex) {
        return (((dbIndex) << 24) | objectGeoDiffIndex & 0xFFFFFF);
    }

    private static int dbIndex(final long compressedIndex) {
        return (int) (compressedIndex >>> 56);
    }

    private static int objectIndex(final long compressedIndex) {
        return ((int) (compressedIndex >>> 32) & 0xFFFFFF);
    }

    private long genIndex(final DBNode node) {
        final String name = node.data().meta.name;
        final byte dbIndex = (byte) ((name.charAt(dbNameLength - 1) - '0') +
                (name.charAt(dbNameLength - 2) - '0') * 10 +
                (name.charAt(dbNameLength - 3) - '0') * 100);
        return compress(
                makeCompressedNodeIndex(dbIndex, node.pre() - this.currentObjectPre),
                node.pre());
    }

}
