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
package de.interactive_instruments.etf.bsxm.topox;

import static de.interactive_instruments.etf.bsxm.topox.TopologyErrorType.FREE_STANDING_SURFACE;
import static de.interactive_instruments.etf.bsxm.topox.TopologyErrorType.HOLE_EMPTY_INTERIOR;

/**
 * The Theme object bundles all objects that are used to
 * create topological information for one or multiple Features,
 * including error handling, parsing and
 * building topological data structure.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class Theme {

	public final String name;
	public final TopologyErrorCollector topologyErrorCollector;
	public final String errorFile;
	public final GeoJsonWriter geoJsonWriter;
	public final PosListParser parser;

	final Topology topology;
	private final TopologyBuilder topologyBuilder;

	public Theme(final String name, final TopologyErrorCollector topologyErrorCollector, final String errorFile,
			final GeoJsonWriter geoJsonWriter, final TopologyBuilder topologyBuilder) {
		this.name = name;
		this.topologyErrorCollector = topologyErrorCollector;
		this.errorFile = errorFile;
		this.geoJsonWriter = geoJsonWriter;
		this.topologyBuilder = topologyBuilder;
		this.topology = new TopologyStore(topologyBuilder);
		this.parser = new HashingPosListParser(topologyBuilder);
	}

	public void nextInterior() {
		this.topologyBuilder.nextInterior();
	}

	public int detectHoles() {
		int count=0;
		for (final Topology.Edge emptyInterior : topology.emptyInteriors()) {
			count++;
			topologyErrorCollector.collectError(HOLE_EMPTY_INTERIOR,
					"X",
					String.valueOf(emptyInterior.source().x()),
					"Y",
					String.valueOf(emptyInterior.source().y()),
					"IS",
					String.valueOf(emptyInterior.leftObject()));
		}
		return count;
	}

	public int detectFreeStandingSurfaces() {
		int count=0;
		for (final Topology.Edge freeStandingSurface : topology.freeStandingSurfaces()) {
			count++;
			topologyErrorCollector.collectError(FREE_STANDING_SURFACE,
					"X",
					String.valueOf(freeStandingSurface.source().x()),
					"Y",
					String.valueOf(freeStandingSurface.source().y()),
					"IS",
					String.valueOf(freeStandingSurface.leftObject()));
		}
		return count;
	}

	@Override
	public String toString() {
		return topologyBuilder.toString();
	}
}
