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

import static de.interactive_instruments.etf.bsxm.topox.DataCompression.getLeft;
import static de.interactive_instruments.etf.bsxm.topox.DataCompression.getRight;
import static de.interactive_instruments.etf.bsxm.topox.TopologyBuilder.*;
import static java.lang.Math.abs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;

import de.interactive_instruments.SUtils;
import gnu.trove.TIntArrayList;

/**
 * Query topology data
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class TopologyStore implements Topology, TopologyMXBean {

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
            return builder.getAngleByIndex(edgeIndex);
        }

        @Override
        public double targetAngle() {
            return builder.getAngleByIndex(-edgeIndex);
        }

        @Override
        public int leftInternalObjectId() {
            return builder.getLeftOrRightByIndex(edgeIndex, OBJ_OFFSET);
        }

        @Override
        public int rightInternalObjectId() {
            return builder.getLeftOrRightByIndex(-edgeIndex, OBJ_OFFSET);
        }

        @Override
        public long leftObject() {
            return builder.getTopologicalData(abs(edgeIndex) + LEFT_LOCATION_INDEX);
        }

        @Override
        public long rightObject() {
            final long right = builder.getTopologicalData(abs(edgeIndex) + RIGHT_LOCATION_INDEX);
            if (right == Integer.MIN_VALUE) {
                // check if this is a free-standing surface mark
                return 0;
            }
            return right;
        }

        @Override
        public Edge sourceCcwNext() {
            return new FlyweightEdge(builder.getLeftOrRightByIndex(edgeIndex, CCWI_OFFSET));
        }

        @Override
        public Edge targetCcwNext() {
            return new FlyweightEdge(builder.getLeftOrRightByIndex(-edgeIndex, CCWI_OFFSET));
        }

        @Override
        public Edge edge(Topology.Node node) {
            final int targetEdgeIndex = ((FlyweightNode) node).edgeIndex;
            return edgeByIndex(edgeIndex, targetEdgeIndex);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof FlyweightEdge) {
                return ((FlyweightEdge) obj).edgeIndex == this.edgeIndex;
            }
            return super.equals(obj);
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
        public double x() {
            return builder.getCoordinate(builder.getLeftOrRightByIndex(edgeIndex, COORDINATE_OFFSET));
        }

        @Override
        public double y() {
            return builder.getCoordinate(builder.getLeftOrRightByIndex(edgeIndex, COORDINATE_OFFSET) + 1);
        }

        @Override
        public Edge anEdge() {
            return new FlyweightEdge(edgeIndex);
        }

        @Override
        public Edge edge(Topology.Node node) {
            final int targetEdgeIndex = ((FlyweightNode) node).edgeIndex;
            return edgeByIndex(edgeIndex, targetEdgeIndex);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof FlyweightNode) {
                return ((FlyweightNode) obj).edgeIndex == this.edgeIndex;
            }
            return super.equals(obj);
        }

        @Override
        public String toString() {
            return Double.toString(x()) + " " + Double.toString(y());
        }
    }

    private TopologyBuilder builder;

    TopologyStore(final TopologyBuilder builder) {
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
        for (int i = 2; i < builder.internalCoordinateSize(); ++i) {
            bbox[0] = Math.min(builder.getCoordinate(i), bbox[0]);
            bbox[1] = Math.max(builder.getCoordinate(i), bbox[1]);
            bbox[2] = Math.min(builder.getCoordinate(++i), bbox[2]);
            bbox[3] = Math.max(builder.getCoordinate(i), bbox[3]);
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
            final int sourceCoordIndex = builder.getLeftOrRightByIndex(sourceEdgeIndex, COORDINATE_OFFSET);
            final int targetCoordIndex = builder.getLeftOrRightByIndex(targetEdgeIndex, COORDINATE_OFFSET);

            int ccwNext = sourceEdgeIndex;
            int ccwNextSourceCoordIndex = builder.getLeftOrRightByIndex(ccwNext, COORDINATE_OFFSET);
            int ccwNextTargetCoordIndex = builder.getLeftOrRightByIndex(-ccwNext, COORDINATE_OFFSET);

            int i = 0;

            while (++i < 360) {
                if (ccwNextSourceCoordIndex == sourceCoordIndex && ccwNextTargetCoordIndex == targetCoordIndex) {
                    return new FlyweightEdge(-ccwNext);
                } else if (ccwNextSourceCoordIndex == targetCoordIndex && ccwNextTargetCoordIndex == sourceCoordIndex) {
                    return new FlyweightEdge(ccwNext);
                }

                ccwNext = builder.getLeftOrRightByIndex(ccwNext, CCWI_OFFSET);
                ccwNextSourceCoordIndex = builder.getLeftOrRightByIndex(ccwNext, COORDINATE_OFFSET);
                ccwNextTargetCoordIndex = builder.getLeftOrRightByIndex(-ccwNext, COORDINATE_OFFSET);

            }
            return null;
        }
    }

    @Override
    public Node node(final double x, final double y) {
        final int edgeIndex = builder.getTargetEdge(x, y);
        if (edgeIndex != -1) {
            return new FlyweightNode(edgeIndex);
        } else {
            return null;
        }
    }

    @Override
    public Iterable<Edge> emptyInteriors() {
        return () -> new Iterator<Edge>() {
            int currentPos = builder.findNextEmptyInterior(TOPOLOGY_FIELDS_SIZE);

            @Override
            public boolean hasNext() {
                return currentPos + TOPOLOGY_FIELDS_SIZE < builder.internalTopologicalDataSize();
            }

            @Override
            public Edge next() {
                final Edge edge = new FlyweightEdge(currentPos);
                currentPos = builder.findNextEmptyInterior(currentPos + TOPOLOGY_FIELDS_SIZE);
                return edge;
            }
        };
    }

    @Override
    public Iterable<Edge> freeStandingSurfaces() {

        // The edge index of the found free-standing surfaces
        final TIntArrayList firstFoundFreeStandingSurfaceEdges = new TIntArrayList();

        // The size of length of the free-standing surfaces
        // It is naively assumed that the surface with the most edges is the larger one.
        final TIntArrayList freeStandingSurfaceEdgeSize = new TIntArrayList();

        builder.findFreeStandingSurfaces(firstFoundFreeStandingSurfaceEdges, freeStandingSurfaceEdgeSize);
        if (freeStandingSurfaceEdgeSize.size() > 0) {
            int maxPos = 0;
            int max = freeStandingSurfaceEdgeSize.get(maxPos);
            if (firstFoundFreeStandingSurfaceEdges.size() > 1) {
                // find max
                for (int i = 1; i < freeStandingSurfaceEdgeSize.size(); i++) {
                    final int cur = freeStandingSurfaceEdgeSize.get(i);
                    if (cur > max) {
                        max = cur;
                        maxPos = i;
                    }
                }
            }
            firstFoundFreeStandingSurfaceEdges.remove(maxPos);
        }

        final int maxPos = firstFoundFreeStandingSurfaceEdges.size();

        return () -> new Iterator<Edge>() {
            int currentPos = 0;

            @Override
            public boolean hasNext() {
                return currentPos < maxPos;
            }

            @Override
            public Edge next() {
                return new FlyweightEdge(firstFoundFreeStandingSurfaceEdges.get(currentPos++));
            }
        };
    }

    @Override
    public int getCurrentObjectId() {
        return builder.internalGetCurrentObjectId();
    }

    @Override
    public int getObjectsProcessed() {
        return builder.internalGetObjectsProcessed();
    }

    @Override
    public int getEdgeSize() {
        return builder.size();
    }

    @Override
    public int getCoordinatesSize() {
        return (builder.internalCoordinateSize() - 2) / 2;
    }

    @Override
    public int getLookupCollisions() {
        return builder.internalGetLookupCollisions();
    }

    @Override
    public int getLookupErrors() {
        return builder.internalGetLookupErrors();
    }

    private static void addEdgeInformation(final StringBuilder builder, final Edge edge) {
        builder.append(edge.toString());
        builder.append(", sourceAngle = ");
        builder.append(Math.toDegrees(edge.sourceAngle()));
        builder.append(", targetAngle = ");
        builder.append(Math.toDegrees(edge.targetAngle()));
        builder.append(", leftObject = ");
        builder.append(edge.leftObject());
        builder.append(", rightObject = ");
        builder.append(edge.rightObject());
    }

    @Override
    public String getEdgesAtPoint(final double x, final double y) {
        final Node node = node(x, y);
        final Edge edge = node.anEdge();
        final StringBuilder outputInfo = new StringBuilder();
        addEdgeInformation(outputInfo, edge);
        outputInfo.append(SUtils.ENDL);
        for (Edge next = edge.sourceCcwNext(); next != edge; next = next.sourceCcwNext()) {
            addEdgeInformation(outputInfo, next);
            outputInfo.append(SUtils.ENDL);
        }
        return outputInfo.toString();
    }

    @Override
    public String getEdge(final double x1, final double y1, final double x2, final double y2) {
        final Edge edge = edge(x1, y1, x2, y2);
        final StringBuilder outputInfo = new StringBuilder();
        addEdgeInformation(outputInfo, edge);
        outputInfo.append(SUtils.ENDL);
        outputInfo.append("Origin:");
        for (Edge next = edge.sourceCcwNext(); next != edge; next = next.sourceCcwNext()) {
            addEdgeInformation(outputInfo, next);
            outputInfo.append(SUtils.ENDL);
        }
        outputInfo.append(SUtils.ENDL);
        outputInfo.append("Target:");
        for (Edge next = edge.targetCcwNext(); next != edge; next = next.targetCcwNext()) {
            addEdgeInformation(outputInfo, next);
            outputInfo.append(SUtils.ENDL);
        }
        return outputInfo.toString();
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("TopologyBuilder{ ");
        sb.append("edges=");
        sb.append(size());
        sb.append(", coordinates=");
        sb.append((builder.internalCoordinateSize() - 2) / 2);
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
        for (int i = TopologyBuilder.TOPOLOGY_FIELDS_SIZE; i < builder.internalTopologicalDataSize(); i++) {
            // Edge ID
            writer.append(Integer.toString(i));
            writer.append(seperator);

            // Source X Coordinate
            final int sourceCoordIndex = getLeft(builder.getTopologicalData(i));
            writer.append(Double.toString(builder.getCoordinate(sourceCoordIndex)));
            writer.append(' ');

            // Source Y Coordinate
            writer.append(Double.toString(builder.getCoordinate(sourceCoordIndex + 1)));
            writer.append(seperator);

            // Target X Coordinate
            final int targetCoordIndex = getRight(builder.getTopologicalData(i));
            writer.append(Double.toString(builder.getCoordinate(targetCoordIndex)));
            writer.append(' ');

            // Target Y Coordinate
            writer.append(Double.toString(builder.getCoordinate(targetCoordIndex + 1)));
            writer.append(seperator);
            ++i;

            // Start angle
            writer.append(Double.toString(Math.toDegrees(Double.longBitsToDouble(builder.getTopologicalData(i++)))));
            writer.append(seperator);

            // Target angle
            writer.append(Double.toString(Math.toDegrees(Double.longBitsToDouble(builder.getTopologicalData(i++)))));
            writer.append(seperator);

            // CCW next from start
            writer.append(Integer.toString(getLeft(builder.getTopologicalData(i))));
            writer.append(seperator);

            // CCW next from end
            writer.append(Integer.toString(getRight(builder.getTopologicalData(i++))));
            writer.append(seperator);

            // Left object
            final int leftObjectIndex = getLeft(builder.getTopologicalData(i));
            writer.append(Integer.toString(leftObjectIndex));
            writer.append(seperator);

            // Right object
            final int rightObjectIndex = getRight(builder.getTopologicalData(i));
            writer.append(Integer.toString(rightObjectIndex));
            writer.append(seperator);

            // Left object XPath
            writer.append(Long.toString(builder.getTopologicalData(++i)));
            writer.append(seperator);

            // Right object XPath
            writer.append(Long.toString(builder.getTopologicalData(++i)));
            writer.append(endlSeperator);
        }
        writer.flush();
    }
}
