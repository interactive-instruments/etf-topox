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

package de.interactive_instruments.etf.bsxm.topox;

import de.interactive_instruments.SUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static de.interactive_instruments.etf.bsxm.topox.TopologyBuilder.*;

/**
 * Immutable access to topology data
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class TopologyStore implements Topology {

	private int getLeftOrRightByIndex(final int index, final int propertyOffset) {
		if(index>0) {
			return getLeft(builder.topology.getQuick(index + propertyOffset));
		}else{
			return getRight(builder.topology.getQuick((-index) + propertyOffset));
		}
	}

	private final class FlyweightEdge implements Topology.Edge {
		private final int i;
		private FlyweightEdge(final int i) {
			this.i = i;
		}

		@Override public Node source() {
			return new FlyweightNode(i);
		}

		@Override public Node target() {
			return new FlyweightNode(-i);
		}

		@Override public double sourceAngle() {
			if(i>0) {
				return TopologyBuilder.getSourceAngle(builder.topology,i);
			}else{
				return TopologyBuilder.getTargetAngle(builder.topology,-i);
			}
		}

		@Override public double targetAngle() {
			if(i>0) {
				return TopologyBuilder.getTargetAngle(builder.topology,i);
			}else{
				return TopologyBuilder.getSourceAngle(builder.topology,-i);
			}
		}

		@Override public int leftObject() {
			return getLeftOrRightByIndex(i,OBJ_OFFSET);
		}

		@Override public int rightObject() {
			return getLeftOrRightByIndex(-i,OBJ_OFFSET);
		}

		@Override public int object() {
			return 0;
		}

		@Override public Edge sourceCcwNext() {
			return new FlyweightEdge(getLeftOrRightByIndex(i,CCWI_OFFSET));
		}

		@Override public Edge targetCcwNext() {
			return new FlyweightEdge(getLeftOrRightByIndex(-i,CCWI_OFFSET));
		}

		@Override public String toString() {
			return source().toString()+" -> "+target().toString()+" @ "+i;
		}
	}

	private final class FlyweightNode implements Topology.Node {
		private final int i;
		private FlyweightNode(final int i) {
			this.i = i;
		}

		@Override
		public Edge anEdge() {
			return new FlyweightEdge(i);
		}

		@Override
		public double x() {
			return builder.coordinates.getQuick(getLeftOrRightByIndex(i,COORDINATE_OFFSET));
		}

		@Override
		public double y() {
			return builder.coordinates.getQuick(getLeftOrRightByIndex(i,COORDINATE_OFFSET)+1);
		}

		@Override
		public String toString() {
			return Double.toString(x())+" "+Double.toString(y());
		}
	}

	private TopologyBuilder builder;

	public TopologyStore(final TopologyBuilder builder) {
		this.builder = builder;
	}


	int size() {
		return (builder.topology.size()-TopologyBuilder.TOPOLOGY_FIELDS_SIZE)/TopologyBuilder.TOPOLOGY_FIELDS_SIZE;
	}

	@Override
	public String getName() {
		return builder.theme.getName();
	}

	public double[] bbox() {
		final double[] bbox = new double[4];
		// xmin
		bbox[0] = Double.MAX_VALUE;
		// xmax
		bbox[1] = Double.MIN_VALUE;
		// ymin
		bbox[2] = Double.MAX_VALUE;
		// ymax
		bbox[3] = Double.MIN_VALUE;
		for (int i = 2; i < builder.coordinates.size(); ++i) {
			bbox[0] = Math.min(builder.coordinates.getQuick(i), bbox[0]);
			bbox[1] = Math.max(builder.coordinates.getQuick(i), bbox[1]);
			bbox[2] = Math.min(builder.coordinates.getQuick(++i), bbox[2]);
			bbox[3] = Math.max(builder.coordinates.getQuick(i), bbox[3]);
		}
		return bbox;
	}

	@Override public Edge edge(final double xSource, final double ySource, final double xTarget, final double yTarget) {
		throw new IllegalStateException("Not implemented yet");
	}

	@Override public Node node(final double x, final double y) {
		return new FlyweightNode(builder.getTargetEdge(x,y));
	}

	@Override public Iterable<Edge> borders() {
		return null;
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("TopologyBuilder{ ");
		sb.append("edges=");
		sb.append(size());
		sb.append(", coordinates=");
		sb.append((builder.coordinates.size()-2)/2);
		sb.append('}');
		return sb.toString();
	}

	void exportCsv(final File destination) throws IOException {
		final FileWriter writer = new FileWriter(destination);
		final String endlSeperator = ""+SUtils.ENDL;
		final String seperator = ";";
		writer.append("Edge");
		writer.append(seperator);
		writer.append("Start coordinates");
		writer.append(seperator);
		writer.append("Target coordinates");
		writer.append(seperator);
		writer.append("Start node angle");
		writer.append(seperator);
		writer.append("Target node angle");
		writer.append(seperator);
		writer.append("Source node CCW next");
		writer.append(seperator);
		writer.append("Target node CCW next");
		writer.append(seperator);
		writer.append("Left object");
		writer.append(seperator);
		writer.append("Right object");
		writer.append(seperator);
		writer.append("Left Object XPath");
		writer.append(seperator);
		writer.append("Right Object XPath");
		writer.append(endlSeperator);

		writer.flush();
		for (int i = TopologyBuilder.TOPOLOGY_FIELDS_SIZE; i < builder.topology.size(); i++) {
			// Edge ID
			writer.append(Integer.toString(i));
			writer.append(seperator);

			// Source X Coordinate
			final int sourceCoordIndex = getLeft(builder.topology.getQuick(i));
			writer.append(Double.toString(builder.coordinates.getQuick(sourceCoordIndex)));
			writer.append(' ');

			// Source Y Coordinate
			writer.append(Double.toString(builder.coordinates.getQuick(sourceCoordIndex+1)));
			writer.append(seperator);

			// Target X Coordinate
			final int targetCoordIndex = getRight(builder.topology.getQuick(i));
			writer.append(Double.toString(builder.coordinates.getQuick(targetCoordIndex)));
			writer.append(' ');

			// Target Y Coordinate
			writer.append(Double.toString(builder.coordinates.getQuick(targetCoordIndex+1)));
			writer.append(seperator);
			++i;

			// Start angle
			writer.append(Double.toString(Math.toDegrees(Double.longBitsToDouble(builder.topology.getQuick(i++)))));
			writer.append(seperator);

			// Target angle
			writer.append(Double.toString(Math.toDegrees(Double.longBitsToDouble(builder.topology.getQuick(i++)))));
			writer.append(seperator);

			// CCW next from start
			writer.append(Integer.toString(getLeft(builder.topology.getQuick(i))));
			writer.append(seperator);

			// CCW next from end
			writer.append(Integer.toString(getRight(builder.topology.getQuick(i++))));
			writer.append(seperator);

			// Left object
			final int leftObjectIndex = getLeft(builder.topology.getQuick(i));
			writer.append(Integer.toString(leftObjectIndex));
			writer.append(seperator);

			// Right object
			final int rightObjectIndex = getRight(builder.topology.getQuick(i));
			writer.append(Integer.toString(rightObjectIndex));
			writer.append(seperator);

			// Left object XPath
			writer.append(Long.toString(builder.topology.getQuick(++i)));
			writer.append(seperator);

			// Right object XPath
			writer.append(Long.toString(builder.topology.getQuick(++i)));
			writer.append(endlSeperator);
		}
		writer.flush();
	}
}
