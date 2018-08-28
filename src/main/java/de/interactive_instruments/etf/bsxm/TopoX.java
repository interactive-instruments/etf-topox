/*
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

import de.interactive_instruments.IFile;
import de.interactive_instruments.etf.bsxm.topox.*;
import de.interactive_instruments.exceptions.ExcUtils;
import org.basex.core.BaseXException;
import org.basex.query.QueryModule.Deterministic;
import org.basex.query.QueryModule.Permission;
import org.basex.query.QueryModule.Requires;
import org.basex.query.value.node.DBNode;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import static de.interactive_instruments.etf.bsxm.topox.TopologyBuilder.compress;
import static de.interactive_instruments.etf.bsxm.topox.TopologyBuilder.getRight;

/**
 * TopoX facade
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class TopoX {

	private final TopologyErrorCollector[] topologyErrorCollector = new TopologyErrorCollector[5];
	private final String[] errorFiles = new String[5];
	private final GeoJsonWriter[] geoJsonWriters = new GeoJsonWriter[5];
	private final TopologyBuilder[] topologyBuilders = new TopologyBuilder[5];
	private final PosListParser[] parsers = new HashingPosListParser[5];
	private int tc = 0;
	private int dbNameLength=0;
	private String dbnamePrefix;
	private int currentObjectPre;

	/**
	 * Sets the database name prefix internally in the TopoX module.
	 *
	 * Database names must be suffixed with a three digits index, i.e. DB-000 .
	 *
	 * @param name full database name ( i.e. DB-000 )
	 * @param dbCount number of databases that will be used
	 * @return full database name
	 * @throws BaseXException if database name is not suffixed with a three digits index
	 */
	@Requires(Permission.READ)
	public String initDb(final String name, final short dbCount) throws BaseXException {
		if(name==null || name.length()<4) {
			throw new BaseXException("Invalid database name: '"+name+"'. "
					+ "Database names must be suffixed with a three digits index, i.e. DB-000");
		}
		final int length = name.length();
		for(int i = length - 1; i >= length-3; i--) {
			if(name.charAt(i)< '0'|| name.charAt(i) > '9') {
				throw new BaseXException("Invalid database name: '"+name+"'. "
						+ "Database names must be suffixed with a three digits index, i.e. DB-000");
			}
		}
		this.dbnamePrefix = name.substring(0, length-3);
		this.dbNameLength=length;
		return name;
	}

	/**
	 * Can be used for dev purposes.
	 * Does not override the error output file and writes all errors to System.out.
	 */
	@Requires(Permission.CREATE)
	public int devTopologyBuilder(final String name, final String path,
			final String identifier, final String geometry, final int initialEdgeCapacity, final String outputDir) throws BaseXException {
		if(tc<2) {
			try {
				final Theme theme = new Theme(name, path, identifier, geometry);
				final File errorOutputDir = new File(outputDir);

				final IFile geoJsonOutputFile = new IFile(errorOutputDir, theme.getName()+".js");
				final GeoJsonWriter writer = new GeoJsonWriter(geoJsonOutputFile);
				writer.init();
				this.geoJsonWriters[tc] = writer;

				final File errorOutputFile = new File(errorOutputDir, theme.getName()+".xml");
				this.errorFiles[tc] = errorOutputFile.toString();

				topologyBuilders[tc] = new TopologyBuilder(theme, topologyErrorCollector[tc], 16);
				final XMLOutputFactory xof = XMLOutputFactory.newInstance();
				topologyErrorCollector[tc] = new TopologyErrorXmlWriter(theme, xof.createXMLStreamWriter(System.out));

				return tc++;
			} catch (final IOException | XMLStreamException e) {
				throw new BaseXException(e);
			}
		}
		return -1;
	}

	/**
	 * Creates a new Topology builder.
	 */
	@Requires(Permission.CREATE)
	public int newTopologyBuilder(final String name, final String path,
			final String identifier, final String geometry, final int initialEdgeCapacity, final String tmpOutputDir) throws BaseXException {
		if(tc<5) {
			try {
				final Theme theme = new Theme(name, path, identifier, geometry);
				final XMLOutputFactory xof = XMLOutputFactory.newInstance();
				final File errorOutputDir = new File(tmpOutputDir);

				final IFile geoJsonOutputFile = new IFile(errorOutputDir, theme.getName()+".js");
				final GeoJsonWriter writer = new GeoJsonWriter(geoJsonOutputFile);
				writer.init();
				this.geoJsonWriters[tc] = writer;

				final File errorOutputFile = new File(errorOutputDir, theme.getName()+".xml");
				this.errorFiles[tc] = errorOutputFile.toString();

				final XMLStreamWriter streamWriter = xof.createXMLStreamWriter(new FileWriter(errorOutputFile));
				topologyErrorCollector[tc] = new TopologyErrorXmlWriter(theme, streamWriter);

				topologyBuilders[tc] = new TopologyBuilder(theme, topologyErrorCollector[tc], initialEdgeCapacity);
				parsers[tc] = new HashingPosListParser(topologyBuilders[tc]);
				topologyErrorCollector[tc].init();
			return tc++;
			} catch (final IOException | XMLStreamException e) {
				throw new BaseXException(e);
			}
		}
		return -1;
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
		return getRight(compressedIndex)-
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
		final String dbIndexStr=Integer.toString(dbIndex(compressedIndex));
		final StringBuilder sb = new StringBuilder(this.dbnamePrefix.length()+3);
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
		getRight(compressedIndex)-
				objectIndex(compressedIndex));
	}



	/**
	 * Switch the Topology Builder to the next interior
	 *
	 * @param id ID of Topology Builder
	 */
	@Requires(Permission.NONE)
	public void nextInterior(final int id) {
		topologyBuilders[id].nextInterior();
	}

	/**
	 * Switch the Topology Builder to the next geometric object
	 *
	 * @param id ID of Topology Builder
	 * @return input object
	 */
	@Requires(Permission.NONE)
	public void nextGeometricObject(final int id) {
		parsers[id].nextGeometricObject();
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
		currentObjectPre=object.pre();
		return object;
	}

	@Requires(Permission.READ)
	public void parseSegment(final int id, final DBNode posList, final int type) {
		parsers[id].parseDirectPositions(posList.data().text(posList.pre(),true),false, genIndex(posList), type);
	}

	@Requires(Permission.NONE)
	public String errorFile(final int id) {
		topologyErrorCollector[id].release();
		System.out.println(topologyBuilders[id].toString());
		return this.errorFiles[id];
	}

	@Requires(Permission.NONE)
	public void writeGeoJsonPointFeature(final int id, final String errorId, final String error, final String x, final String y) throws IOException {
		geoJsonWriters[id].writePointFeature(errorId, error, x,y);
	}

	@Requires(Permission.NONE)
	public void startGeoJsonFeature(final int id, final String featureId) throws IOException {
		geoJsonWriters[id].startFeature(featureId);
	}

	@Requires(Permission.NONE)
	public void addGeoJsonCoordinates(final int id, final DBNode coordinateNode) throws IOException {
		geoJsonWriters[id].addCoordinates(coordinateNode.data().text(coordinateNode.pre(),true));
	}

	@Requires(Permission.NONE)
	public void nextGeoJsonInterior(final int id) throws IOException {
		geoJsonWriters[id].nextInterior();
	}

	@Requires(Permission.NONE)
	public void addGeoJsonInteriorCoordinates(final int id, final DBNode coordinateNode) throws IOException {
		geoJsonWriters[id].addCoordinatesInterior(coordinateNode.data().text(coordinateNode.pre(),true));
	}

	@Requires(Permission.CREATE)
	public void attachIssueMap(final int id, final String attachmentId) throws IOException {

		try {
			geoJsonWriters[id].close();
		} catch (final IOException ignore) {
			ExcUtils.suppress(ignore);
		}

		final File dir = geoJsonWriters[id].getFile().getParentFile();
		final IFile attachmentMapFile = new IFile(dir, attachmentId+".html");

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
				result.replaceAll("'out.js'", "'"+geoJsonWriters[id].getFile().getName()+"'")
		));
	}

	private static int makeCompressedNodeIndex(final byte dbIndex, final int objectGeoDiffIndex) {
		return (((dbIndex) << 24) | objectGeoDiffIndex & 0xFFFFFF);
	}

	static int dbIndex(final long compressedIndex) {
		return (int)(compressedIndex >>> 56);
	}

	static int objectIndex(final long compressedIndex) {
		return ((int)(compressedIndex >>> 32) & 0xFFFFFF);
	}

	private long genIndex(final DBNode node) {
		final String name = node.data().meta.name;
		final byte dbIndex = (byte) ((name.charAt(dbNameLength-1)-'0')+
				(name.charAt(dbNameLength-2)-'0')*10+
				(name.charAt(dbNameLength-3)-'0')*100);
		return compress(
				makeCompressedNodeIndex(dbIndex, node.pre()-this.currentObjectPre),
				node.pre()
		);
	}

}
