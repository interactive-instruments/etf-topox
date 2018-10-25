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

import static de.interactive_instruments.etf.bsxm.topox.TopologyBuilder.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import de.interactive_instruments.SUtils;

/**
 * Immutable access to topology data
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class TopologyStore implements Topology {

	private int getLeftOrRightByIndex(final int index, final int propertyOffset) {
		if (index > 0) {
			return getLeft(builder.topology.getQuick(index + propertyOffset));
		} else {
			return getRight(builder.topology.getQuick((-index) + propertyOffset));
		}
	}

	private final class FlyweightEdge implements Topology.Edge {
		private final int edgeIndex;

		private FlyweightEdge(final int edgeIndex) {
			this.edgeIndex = edgeIndex;
		}

		@Override
		public Node source() {
			return new FlyweightNode(edgeIndex);
		}

		@Override
		public Node target() {
			return new FlyweightNode(-edgeIndex);
		}

		@Override
		public double sourceAngle() {
			if (edgeIndex > 0) {
				return TopologyBuilder.getSourceAngle(builder.topology, edgeIndex);
			} else {
				return TopologyBuilder.getTargetAngle(builder.topology, -edgeIndex);
			}
		}

		@Override
		public double targetAngle() {
			if (edgeIndex > 0) {
				return TopologyBuilder.getTargetAngle(builder.topology, edgeIndex);
			} else {
				return TopologyBuilder.getSourceAngle(builder.topology, -edgeIndex);
			}
		}

		@Override
		public int leftObject() {
			return getLeftOrRightByIndex(edgeIndex, OBJ_OFFSET);
		}

		@Override
		public int rightObject() {
			return getLeftOrRightByIndex(-edgeIndex, OBJ_OFFSET);
		}

		@Override
		public Edge sourceCcwNext() {
			return new FlyweightEdge(getLeftOrRightByIndex(edgeIndex, CCWI_OFFSET));
		}

		@Override
		public Edge targetCcwNext() {
			return new FlyweightEdge(getLeftOrRightByIndex(-edgeIndex, CCWI_OFFSET));
		}

		@Override
		public Edge edge(Topology.Node node) {
			final int targetEdgeIndex = ((FlyweightNode) node).edgeIndex;
			return edgeByIndex(edgeIndex, targetEdgeIndex);
		}

		@Override
		public String toString() {
			return source().toString() + " -> " + target().toString() + " @ " + edgeIndex;
		}
	}

	private final class FlyweightNode implements Topology.Node {
		private final int edgeIndex;

		private FlyweightNode(final int edgeIndex) {
			this.edgeIndex = edgeIndex;
		}

		@Override
		public Edge anEdge() {
			return new FlyweightEdge(edgeIndex);
		}

		@Override
		public double x() {
			return builder.coordinates.getQuick(getLeftOrRightByIndex(edgeIndex, COORDINATE_OFFSET));
		}

		@Override
		public double y() {
			return builder.coordinates.getQuick(getLeftOrRightByIndex(edgeIndex, COORDINATE_OFFSET) + 1);
		}

		@Override
		public String toString() {
			return Double.toString(x()) + " " + Double.toString(y());
		}
	}

	private TopologyBuilder builder;

	public TopologyStore(final TopologyBuilder builder) {
		this.builder = builder;
	}

	int size() {
		return builder.size();
	}

	@Override
	public String getName() {
		return builder.themeName;
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

	@Override
	public Edge edge(final double xSource, final double ySource, final double xTarget, final double yTarget) {
		final int sourceEdgeIndex = builder.getTargetEdge(xSource, ySource);
		final int targetEdgeIndex = builder.getTargetEdge(xTarget, yTarget);
		return edgeByIndex(sourceEdgeIndex, targetEdgeIndex);
	}

	private Edge edgeByIndex(final int sourceEdgeIndex, final int targetEdgeIndex) {
		if (sourceEdgeIndex == -targetEdgeIndex) {
			return new FlyweightEdge(targetEdgeIndex);
		} else {
			// Use the coordinate indices for comparison
			final int sourceCoordIndex = getLeftOrRightByIndex(sourceEdgeIndex, COORDINATE_OFFSET);
			final int targetCoordIndex = getLeftOrRightByIndex(targetEdgeIndex, COORDINATE_OFFSET);

			int ccwNext = sourceEdgeIndex;
			int ccwNextSourceCoordIndex = getLeftOrRightByIndex(ccwNext, COORDINATE_OFFSET);
			int ccwNextTargetCoordIndex = getLeftOrRightByIndex(-ccwNext, COORDINATE_OFFSET);

			int i = 0;

			while (++i < 360) {
				if (ccwNextSourceCoordIndex == sourceCoordIndex && ccwNextTargetCoordIndex == targetCoordIndex) {
					return new FlyweightEdge(-ccwNext);
				} else if (ccwNextSourceCoordIndex == targetCoordIndex && ccwNextTargetCoordIndex == sourceCoordIndex) {
					return new FlyweightEdge(ccwNext);
				}

				ccwNext = getLeftOrRightByIndex(ccwNext, CCWI_OFFSET);
				ccwNextSourceCoordIndex = getLeftOrRightByIndex(ccwNext, COORDINATE_OFFSET);
				ccwNextTargetCoordIndex = getLeftOrRightByIndex(-ccwNext, COORDINATE_OFFSET);

			}
			return null;
		}
	}

	@Override
	public Node node(final double x, final double y) {
		return new FlyweightNode(builder.getTargetEdge(x, y));
	}

	@Override
	public Iterable<Edge> borders() {
		return null;
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("TopologyBuilder{ ");
		sb.append("edges=");
		sb.append(size());
		sb.append(", coordinates=");
		sb.append((builder.coordinates.size() - 2) / 2);
		sb.append('}');
		return sb.toString();
	}

	void exportCsv(final File destination) throws IOException {
		final FileWriter writer = new FileWriter(destination);
		final String endlSeperator = "" + SUtils.ENDL;
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
			writer.append(Double.toString(builder.coordinates.getQuick(sourceCoordIndex + 1)));
			writer.append(seperator);

			// Target X Coordinate
			final int targetCoordIndex = getRight(builder.topology.getQuick(i));
			writer.append(Double.toString(builder.coordinates.getQuick(targetCoordIndex)));
			writer.append(' ');

			// Target Y Coordinate
			writer.append(Double.toString(builder.coordinates.getQuick(targetCoordIndex + 1)));
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
