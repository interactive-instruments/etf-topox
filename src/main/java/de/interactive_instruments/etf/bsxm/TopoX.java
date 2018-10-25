/**
 * Copyright 2010-2018 interactive instruments GmbH
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
import java.util.*;

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

/**
 * TopoX facade.
 *
 * Not thread safe.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class TopoX {

	/**
	 * The Theme object bundles all objects that are used to
	 * create topological information for one or multiple Features,
	 * including error handling, parsing and
	 * building topological data structure.
	 */
	private final static class Theme {
		final String theme;
		final TopologyErrorCollector topologyErrorCollector;
		final String errorFile;
		final GeoJsonWriter geoJsonWriter;
		final TopologyBuilder topologyBuilder;
		final PosListParser parser;

		private Theme(final String theme, final TopologyErrorCollector topologyErrorCollector, final String errorFile,
				final GeoJsonWriter geoJsonWriter, final TopologyBuilder topologyBuilder, final PosListParser parser) {
			this.theme = theme;
			this.topologyErrorCollector = topologyErrorCollector;
			this.errorFile = errorFile;
			this.geoJsonWriter = geoJsonWriter;
			this.topologyBuilder = topologyBuilder;
			this.parser = parser;
		}
	}

	private final List<Theme> themes = new ArrayList();

	// Used to check if a theme already exists and to avoid file name conflicts.
	private final Set<String> themeNames = new HashSet();

	// For example "DB-"
	private String dbnamePrefix;

	// Length of the database name. 6 for "DB-000"
	private int dbNameLength = 0;

	// Current BaseX pre value in a context
	private int currentObjectPre;

	/**
	 * Sets the database name prefix internally in the TopoX module.
	 *
	 * Database names must be suffixed with a three digits index, i.e. DB-000 .
	 *
	 * @param name full database name ( i.e. DB-000 )
	 * @param dbCount number of databases that will be used
	 *                   TODO: not used yet, use it to pre calculate initialEdgeCapacity
	 * @return full database name
	 * @throws BaseXException if database name is not suffixed with a three digits index
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

	/**
	 * Can be used for dev purposes.
	 * Does not override the error output file and writes all errors to System.out.
	 */
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

			final File errorOutputFile = new File(errorOutputDir, themeName + ".xml");
			final XMLOutputFactory xof = XMLOutputFactory.newInstance();
			final TopologyErrorXmlWriter topologyErrorCollector = new TopologyErrorXmlWriter(themeName,
					xof.createXMLStreamWriter(System.out));
			final TopologyBuilder topologyBuilder = new TopologyBuilder(themeName, topologyErrorCollector, 16);

			themes.add(new Theme(themeName, topologyErrorCollector, errorOutputFile.toString(), writer, topologyBuilder, null));
			return themes.size()-1;
		} catch (final IOException | XMLStreamException e) {
			throw new BaseXException(e);
		}
	}

	/**
	 * Creates a new Topology builder.
	 */
	@Requires(Permission.CREATE)
	public int newTopologyBuilder(final String themeName, final int initialEdgeCapacity, final String outputDir)
			throws BaseXException {
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
			final HashingPosListParser parser = new HashingPosListParser(topologyBuilder);
			topologyErrorCollector.init();

			themes.add(
					new Theme(themeName, topologyErrorCollector, errorOutputFile.toString(), writer, topologyBuilder, parser));
			return themes.size()-1;
		} catch (final IOException | XMLStreamException e) {
			throw new BaseXException(e);
		}
	}

	/**
	 * Get the pre ID from a compressed long TopoX index
	 *
	 * @param compressedIndex TopoX index
	 * @return BaseX integer pre value of the geometric object
	 */
	@Deterministic
	@Requires(Permission.NONE)
	public int pre(final long compressedIndex) {
		return getRight(compressedIndex);
	}

	/**
	 * Get the pre ID from a compressed long TopoX index
	 *
	 * @param compressedIndex TopoX index
	 * @return BaseX integer pre value of the object
	 */
	@Deterministic
	@Requires(Permission.NONE)
	public int preObject(final long compressedIndex) {
		return getRight(compressedIndex) -
				objectIndex(compressedIndex);
	}

	/**
	 * Get the full database name from a compressed long TopoX index
	 *
	 * Note: initDb has to be called first
	 *
	 * @param compressedIndex TopoX index
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
	 * @param compressedIndex TopoX index
	 * @return BaseX integer pre value
	 */
	@Deterministic
	@Requires(Permission.NONE)
	public long objPreAsGeoPre(final long compressedIndex) {
		return compress(
				dbIndex(compressedIndex) << 24,
				getRight(compressedIndex) -
						objectIndex(compressedIndex));
	}

	/**
	 * Switch the Topology Builder to the next interior
	 *
	 * @param id ID of Topology Builder
	 */
	@Requires(Permission.NONE)
	public void nextInterior(final int id) {
		themes.get(id).topologyBuilder.nextInterior();
	}

	/**
	 * Switch the Topology Builder to the next geometric object
	 *
	 * @param id ID of Topology Builder
	 */
	@Requires(Permission.NONE)
	public void nextGeometricObject(final int id) {
		themes.get(id).parser.nextGeometricObject();
	}

	/**
	 * Switch the Topology Builder to the next Feature
	 *
	 * @param id ID of Topology Builder
	 * @param object geometric object
	 * @return input object
	 */
	@Requires(Permission.NONE)
	public DBNode nextFeature(final int id, final DBNode object) {
		currentObjectPre = object.pre();
		return object;
	}

	@Requires(Permission.READ)
	public void parseSegment(final int id, final DBNode posList, final int type) {
		themes.get(id).parser.parseDirectPositions(posList.data().text(posList.pre(), true), false, genIndex(posList), type);
	}

	@Requires(Permission.NONE)
	public void onEdge(final int id, final DBNode coordinateNode) throws IOException {

		// todo

	}

	@Requires(Permission.NONE)
	public String errorFile(final int id) {
		themes.get(id).topologyErrorCollector.release();
		// TODO propagate as MBean
		System.out.println(themes.get(id).topologyErrorCollector.toString());
		return themes.get(id).errorFile;
	}

	@Requires(Permission.NONE)
	public void writeGeoJsonPointFeature(final int id, final String errorId, final String error, final String x, final String y)
			throws IOException {
		themes.get(id).geoJsonWriter.writePointFeature(errorId, error, x, y);
	}

	@Requires(Permission.NONE)
	public void startGeoJsonFeature(final int id, final String featureId) throws IOException {
		themes.get(id).geoJsonWriter.startFeature(featureId);
	}

	@Requires(Permission.NONE)
	public void addGeoJsonCoordinates(final int id, final DBNode coordinateNode) throws IOException {
		themes.get(id).geoJsonWriter.addCoordinates(coordinateNode.data().text(coordinateNode.pre(), true));
	}

	@Requires(Permission.NONE)
	public void nextGeoJsonInterior(final int id) throws IOException {
		themes.get(id).geoJsonWriter.nextInterior();
	}

	@Requires(Permission.NONE)
	public void addGeoJsonInteriorCoordinates(final int id, final DBNode coordinateNode) throws IOException {
		themes.get(id).geoJsonWriter.addCoordinatesInterior(coordinateNode.data().text(coordinateNode.pre(), true));
	}

	@Requires(Permission.CREATE)
	public void attachIssueMap(final int id, final String attachmentId) throws IOException {

		final GeoJsonWriter geoJsonWriter = themes.get(id).geoJsonWriter;
		try {
			geoJsonWriter.close();
		} catch (final IOException ignore) {
			ExcUtils.suppress(ignore);
		}

		final File dir = geoJsonWriter.getFile().getParentFile();
		final IFile attachmentMapFile = new IFile(dir, attachmentId + ".html");

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
				result.replaceAll("'out.js'", "'" + geoJsonWriter.getFile().getName() + "'")));
	}

	private static int makeCompressedNodeIndex(final byte dbIndex, final int objectGeoDiffIndex) {
		return (((dbIndex) << 24) | objectGeoDiffIndex & 0xFFFFFF);
	}

	static int dbIndex(final long compressedIndex) {
		return (int) (compressedIndex >>> 56);
	}

	static int objectIndex(final long compressedIndex) {
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
