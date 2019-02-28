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

import static de.interactive_instruments.etf.bsxm.topox.TopologyErrorType.*;
import static java.lang.Math.abs;
import static java.lang.Math.atan2;

import java.util.HashMap;
import java.util.Map;

import de.interactive_instruments.container.Pair;
import de.interactive_instruments.etf.bsxm.TopoX;
import gnu.trove.*;

/**
 * A memory optimized store that holds the topology information.
 *
 * Non thread safe.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class TopologyBuilder implements HashingSegmentHandler {

    /**
     * Coordinates
     *
     * Array containing coordinates referenced from the topology edges. First index is the X, second one always the Y coordinate.
     */
    private final TDoubleArrayList coordinates;

    /**
     * ID of the current geometry
     */
    private int objectId = 0;

    /**
     * Number of processed objects
     */
    private int objectsProcessed = 0;

    /**
     * Maps a coordinate hash to an edge index. The 0 edge index indicates that the edge does not exist. The coordinate hash is always mapped to edges where the coordinates are the origin.
     *
     * The data store is very fast but it can not handle collisions due to the use of a primitive data type.
     */
    private final TLongIntHashMap coordinateHashToEdgeMap;

    /**
     * Secondary map for handling collisions. Uses more heap space and is slower due to auto boxing of the Double type.
     *
     * Todo consider using a rehash mechanism when collisions are detected
     */
    private final Map<Coordinate, Integer> coordinateCollisionHashToEdgeMap = new HashMap<>();
    private int errors = 0;

    /**
     * Check if an edge is already defined.
     *
     * For the lookup, the hash of the two string coordinate hashes are used.
     */
    private final TLongHashSet edgeExistence;

    /**
     * Topological Data Structure
     *
     * Array containing topology edges. Each edge contains 10 values which are compressed into 7 longs: - Origin coordinate index: X0 coordinate | - Target coordinate index: Y0 coordinate - Origin angle: the atan2(Y0-Y1, X0-X1) as long bits - Target angle - Ccw next index from origin | - Ccw next index from end - Face left object | - Face right object - Left object geometry location - Right object geometry location
     *
     * Edge index concept: To reduce memory, the direction of edges are encoded into the sign of the edge index. The edge index is used in the coordinate to edge mapping {@link #coordinateHashToEdgeMap} and the ccw next index from origin / end {@link #CCWI_OFFSET}. Requesting the edge index for a coordinate, a positive index means that the coordinate is the origin of the edge and a negative index means that the coordinate is the end point of the edge. The same applies to the ccw next indexes {@link #CCWI_OFFSET} that are persisted for the start and the end point of an edge. A positive index means that the corresponding start point of the other edge is connected to the edge, a negative index means that the edge is connected to the end point of another edge. Zero means that the start or the end point is not connected to any other edge.
     *
     * Exterior/interior objects: An edge that defines an object from exterior has a positive object index on the corresponding side and a negative for an interior boundary.
     *
     * Object geometry location: The logic for encoding and decoding the geometry + object location and the db index is implemented in the TopoX facade {@link TopoX#genIndex(org.basex.query.value.node.DBNode)}
     *
     * The order of the array values are optimized for edge creation
     *
     */
    private final TLongArrayList topology;

    // Offset for the index reference of the X coordinate in the coordinates array.
    // Y is at position + 1
    final static int COORDINATE_OFFSET = 0;

    // Offset for the origin angle: the atan2(Y0-Y1, X0-X1) as long bits
    final static int SOURCE_ANGLE_OFFSET = COORDINATE_OFFSET + 1;

    // Offset for the target angle
    final static int TARGET_ANGLE_OFFSET = SOURCE_ANGLE_OFFSET + 1;

    // Offset for the source and target CCW-next
    final static int CCWI_OFFSET = TARGET_ANGLE_OFFSET + 1;

    // Offset for the object ids
    final static int OBJ_OFFSET = CCWI_OFFSET + 1;

    // Offset for the compressed source location
    final static int LEFT_LOCATION_INDEX = OBJ_OFFSET + 1;

    // Offset for the compressed target location
    final static int RIGHT_LOCATION_INDEX = LEFT_LOCATION_INDEX + 1;

    // Number of topology properties
    final static int TOPOLOGY_FIELDS_SIZE = RIGHT_LOCATION_INDEX + 1;

    // the whole cake
    private final static double PI_2 = Math.PI * 2;

    // The previously added edge index which is used
    // to set the target-ccw-next and target-angle at that edge
    private int previousEdgeIndex;

    // Points to the previously added target coordinates of the previous edge
    private int previousTargetCoordinateIndex;

    // Collector object for errors
    private final TopologyErrorCollector errorCollector;

    // Name of the toplogy name
    final String themeName;

    // surface boundary
    private boolean exterior;

    // Previous X ordinate
    private double previousX;
    private double previousY;
    private long previousHash;

    public TopologyBuilder(final String themeName,
            final TopologyErrorCollector errorCollector,
            final int initialEdgeCapacity) {
        this(themeName, errorCollector, initialEdgeCapacity, 0.95);
    }

    public TopologyBuilder(final String themeName,
            final TopologyErrorCollector errorCollector,
            final int initialEdgeCapacity,
            final double uniqueCoordinatesPerEdge) {
        this.exterior = true;
        this.errorCollector = errorCollector;
        this.themeName = themeName;

        this.edgeExistence = new TLongHashSet(initialEdgeCapacity);
        this.topology = new TLongArrayList(initialEdgeCapacity * TOPOLOGY_FIELDS_SIZE);
        for (int i = 0; i < TOPOLOGY_FIELDS_SIZE; i++) {
            this.topology.add(0);
        }

        final double coordinateArrSize = initialEdgeCapacity * uniqueCoordinatesPerEdge * 2;
        this.coordinates = new TDoubleArrayList((int) coordinateArrSize);
        this.coordinates.add(Double.NaN);
        this.coordinates.add(Double.NaN);

        this.coordinateHashToEdgeMap = new TLongIntHashMap((int) (coordinateArrSize / 2));
    }

    private final static class Coordinate extends Pair<Double, Double> {

        public Coordinate(final Double left, final Double right) {
            super(left, right);
        }

        /**
         * Faster equality check, avoids using autoboxing functions
         */
        @Override
        public boolean equals(final Object o) {
            if ((!(o instanceof Coordinate))) {
                return false;
            }
            final Coordinate c = (Coordinate) o;
            return c.getLeft().doubleValue() == this.getLeft().doubleValue()
                    && c.getRight().doubleValue() == this.getRight().doubleValue();
        }

        @Override
        public String toString() {
            return getLeft() + ", " + getRight();
        }
    }

    private void addCoordinates() {
        coordinates.add(this.previousX);
        coordinates.add(previousY);
        this.previousEdgeIndex = 0;
    }

    /**
     * Find a node by the hash of the previous coordinates. If found the previousEdgeIndex will be set. Otherwise the coordinates are added and the previousEdgeIndex is set to 0.
     */
    private void findOrCreateFirstNode() {
        final long hashCode = calcCoordHashCode(this.previousX, this.previousY);
        final int sourceEdgeIndex = coordinateHashToEdgeMap.get(hashCode);
        if (sourceEdgeIndex == 0) {
            // There is no coordinate to edge mapping. Add the coordinates.
            addCoordinates();
            coordinateHashToEdgeMap.put(hashCode, this.topology.size());
        } else if (coordinatesNotEqual(sourceEdgeIndex, this.previousX, this.previousY)) {
            // collision detection
            final Object ret = coordinateCollisionHashToEdgeMap.putIfAbsent(
                    new Coordinate(this.previousX, previousY), this.topology.size());
            if (ret == null) {
                addCoordinates();
            }
        } else {
            this.previousEdgeIndex = sourceEdgeIndex;
        }
    }

    /**
     * Find the target edge. Add coordinate if it does not exist yet.
     */
    private int getTargetEdgeEnsureCoordinates(final double x, final double y) {
        final long hashCode = calcCoordHashCode(x, y);
        final int targetEdgeIndex = coordinateHashToEdgeMap.get(hashCode);
        // Check if the target edge exists
        if (targetEdgeIndex == 0) {
            // No, so add the coordinates
            coordinates.add(x);
            coordinates.add(y);
            // And use this edge to set a reverse-reference (negative index reference)
            // to the edge that is created here
            coordinateHashToEdgeMap.put(hashCode, -this.topology.size());
            return -this.topology.size();
        } else if (coordinatesNotEqual(targetEdgeIndex, x, y)) {
            // collision detection
            final Object ret = coordinateCollisionHashToEdgeMap.putIfAbsent(new Coordinate(x, y), -this.topology.size());
            if (ret == null) {
                coordinates.add(x);
                coordinates.add(y);
                return -this.topology.size();
            } else {
                ++errors;
                return (int) ret;
            }
        }
        return targetEdgeIndex;
    }

    private boolean coordinatesNotEqual(final int edgeIndex, final double x, final double y) {
        final int coordIndex = getLeftOrRightByIndex(edgeIndex, COORDINATE_OFFSET);
        return coordinates.getQuick(coordIndex) != x || coordinates.getQuick(coordIndex + 1) != y;
    }

    /**
     * Get target edge index or return -1 if not found
     *
     * @param x
     *            X coordiante
     * @param y
     *            Y coordiante
     * @return edge index or -1 if not found
     */
    int getTargetEdge(final double x, final double y) {
        final long hashCode = calcCoordHashCode(x, y);
        final int edgeIndex = coordinateHashToEdgeMap.get(hashCode);
        if (coordinatesNotEqual(edgeIndex, x, y)) {
            // collision detection
            ++errors;
            final Object ret = coordinateCollisionHashToEdgeMap.get(new Coordinate(x, y));
            if (ret == null) {
                return -1;
            } else {
                return (int) ret;
            }
        }
        return edgeIndex;
    }

    private void connectCurrentEdge(final int targetEdgeIndex, final long compressedLocation) {
        // adjust ccws
        final int current = topology.size() - CCWI_OFFSET;
        if (-current == targetEdgeIndex) {
            // connect the source of the current edge with an existing edge
            // Set previous as source ccw next and determine the target ccw next
            topology.add(toLeft(adjustCcwNexts(this.previousEdgeIndex, current, compressedLocation)));
        } else {
            // connect the target of the current edge with an existing edge
            // Set previous as source ccw next and determine the target ccw next
            final int r = adjustCcwNexts(targetEdgeIndex, -current, compressedLocation);
            // Set in previous edge this edge as target ccw next
            final int l = adjustCcwNexts(this.previousEdgeIndex, current, compressedLocation);
            topology.add(compress(l, r));
        }
    }

    // Calculates the clockwise delta angle in the range PI to 2 x PI.
    private static double getCwDelta(final double referenceAngle, final double angle) {
        final double diff = referenceAngle - angle;
        return diff < 0 ? PI_2 + diff : diff;
    }

    private int adjustCcwNexts(final int sourceEdgeIndex, final int newTargetEdgeIndex, final long compressedLocation) {
        // getTopologicalData target ccw of source edge
        final int currentTargetEdgeIndex = setLeftRightCcwNextIfNullOrGet(sourceEdgeIndex, newTargetEdgeIndex);
        if (currentTargetEdgeIndex == 0) {
            // simple case: there is none
            setLeftOrRightCcwNextByIndex(newTargetEdgeIndex, sourceEdgeIndex);
            return sourceEdgeIndex;
        } else {
            // compare angles
            final double referenceAngle = getAngleByIndex(sourceEdgeIndex);
            final double newTargetEdgeAngleDelta = getCwDelta(referenceAngle, getAngleByIndex(newTargetEdgeIndex));

            int cwNext = sourceEdgeIndex;
            int ccwNext = currentTargetEdgeIndex;
            double ccwNextAngle = getAngleByIndex(ccwNext);
            ;
            // Go counter-clockwise through all edges and compare the reference angles
            int maxStepsI = 0;
            while (newTargetEdgeAngleDelta <= getCwDelta(referenceAngle, ccwNextAngle) && ++maxStepsI < 360) {
                cwNext = ccwNext;
                ccwNext = getLeftOrRightByIndex(ccwNext, CCWI_OFFSET);
                ccwNextAngle = getAngleByIndex(ccwNext);
            }

            if (maxStepsI >= 360) {
                // Skip after 360 tries, the determined angles are invalid. This most likely
                // will happen if previous edges of the geometric object are invalid.
                // Save the error information to help reproducing the entire problem.
                final int sourceEdgeCoordIndex = getEdgeCoordIndex(sourceEdgeIndex);
                final int targetEdgeCoordIndex = getEdgeCoordIndex(-newTargetEdgeIndex);
                errorCollector.collectError(
                        INVALID_ANGLE,
                        this.coordinates.get(sourceEdgeCoordIndex), this.coordinates.get(sourceEdgeCoordIndex + 1),
                        "OBJ", String.valueOf(this.objectId),
                        "TX", String.valueOf(this.coordinates.get(targetEdgeCoordIndex)),
                        "TY", String.valueOf(this.coordinates.get(targetEdgeCoordIndex + 1)));
                return -1;
            }

            // Left hand side of target edge
            final int cwObjectFromTargetEdge = getLeftOrRightByIndex(cwNext, OBJ_OFFSET);
            if (exterior) {
                if (cwObjectFromTargetEdge != 0 && cwObjectFromTargetEdge != objectId) {
                    final int sourceEdgeCoordIndex = getEdgeCoordIndex(sourceEdgeIndex);
                    errorCollector.collectError(
                            RING_INTERSECTION,
                            this.coordinates.get(sourceEdgeCoordIndex), this.coordinates.get(sourceEdgeCoordIndex + 1),
                            "IS", String.valueOf(compressedLocation),
                            "CW", getLocationAsStr(-ccwNext),
                            "CCW", getLocationAsStr(cwNext));
                }
            } else {
                // Right side of target edge. Must be negated to getTopologicalData the right hand side.
                final int ccwObjectFromTargetEdge = getLeftOrRightByIndex(-ccwNext, OBJ_OFFSET);
                if (ccwObjectFromTargetEdge != 0 && abs(ccwObjectFromTargetEdge) != objectId) {
                    final int sourceEdgeCoordIndex = getEdgeCoordIndex(sourceEdgeIndex);
                    errorCollector.collectError(
                            RING_INTERSECTION,
                            this.coordinates.get(sourceEdgeCoordIndex), this.coordinates.get(sourceEdgeCoordIndex + 1),
                            "IS", String.valueOf(compressedLocation),
                            "CW", getLocationAsStr(-ccwNext),
                            "CCW", getLocationAsStr(cwNext));
                } else if (cwObjectFromTargetEdge != 0 && abs(cwObjectFromTargetEdge) != objectId) {
                    final int sourceEdgeCoordIndex = getEdgeCoordIndex(sourceEdgeIndex);
                    errorCollector.collectError(
                            RING_INTERSECTION,
                            this.coordinates.get(sourceEdgeCoordIndex), this.coordinates.get(sourceEdgeCoordIndex + 1),
                            "IS", String.valueOf(compressedLocation),
                            "CW", getLocationAsStr(-ccwNext),
                            "CCW", getLocationAsStr(cwNext));
                }
            }
            return setLeftOrRightCcwNextAndGetPrevious(cwNext, newTargetEdgeIndex);
        }
    }

    private String getLocationAsStr(final int loc) {
        if (loc > 0) {
            return String.valueOf(topology.getQuick(loc + LEFT_LOCATION_INDEX));
        } else {
            return String.valueOf(topology.getQuick((-loc) + RIGHT_LOCATION_INDEX));
        }
    }

    private boolean findEdge(final double targetX, final double targetY) {
        final int targetEdgeIndex = getTargetEdge(targetX, targetY);
        final int sourceEdgeIndex = getTargetEdge(this.previousX, this.previousY);

        if (sourceEdgeIndex == -targetEdgeIndex) {
            this.previousEdgeIndex = targetEdgeIndex;
            this.previousTargetCoordinateIndex = getLeftOrRightByIndex(targetEdgeIndex, COORDINATE_OFFSET);
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
                    this.previousEdgeIndex = -ccwNext;
                    this.previousTargetCoordinateIndex = ccwNextTargetCoordIndex;
                    return true;
                } else if (ccwNextSourceCoordIndex == targetCoordIndex && ccwNextTargetCoordIndex == sourceCoordIndex) {
                    this.previousEdgeIndex = ccwNext;
                    this.previousTargetCoordinateIndex = ccwNextSourceCoordIndex;
                    return true;
                }

                ccwNext = getLeftOrRightByIndex(ccwNext, CCWI_OFFSET);
                ccwNextSourceCoordIndex = getLeftOrRightByIndex(ccwNext, COORDINATE_OFFSET);
                ccwNextTargetCoordIndex = getLeftOrRightByIndex(-ccwNext, COORDINATE_OFFSET);
            }
            return false;
        }
        return true;
    }

    @Override
    public void coordinates2d(final double[] coordinates, final long hashesAndLocations[], final int type) {
        coordinate2d(coordinates[0], coordinates[1], hashesAndLocations[0], hashesAndLocations[1], type);
        createEdgeOrSetArcObject(coordinates, hashesAndLocations);
    }

    private void createEdgeOrSetArcObject(final double[] coordinates, final long hashesAndLocations[]) {
        final double sx = coordinates[0];
        final double sy = coordinates[1];
        final double mx = coordinates[2];
        final double my = coordinates[3];
        final double ex = coordinates[4];
        final double ey = coordinates[5];
        final double xys = mx * mx + my * my;
        final double bc = (sx * sx + sy * sy - xys) / 2.0;
        final double cd = (xys - ex * ex - ey * ey) / 2.0;
        final double smx = sx - mx;
        final double mex = mx - ex;
        final double mey = my - ey;
        final double smy = sy - my;
        final double determinant = 1.0 / (smx * mey - mex * smy);
        final double centerX = (bc * mey - cd * smy) * determinant;
        final double centerY = (smx * cd - mex * bc) * determinant;

        for (int i = 2; i < coordinates.length; i += 2) {
            ++objectsProcessed;
            final double x = coordinates[i];
            final double y = coordinates[i + 1];
            final long hash = hashesAndLocations[i];
            final long compressedLocation = hashesAndLocations[i + 1];

            // TODO refactoring: extract method
            if (checkForExistingEdgeOrAdd(hash)) {
                // Edge already exists, find it
                if (!findEdge(x, y)) {
                    // The edge can not be found, most likely due to previous errors in the geometric object.
                    errorCollector.collectError(EDGE_NOT_FOUND,
                            this.previousX, this.previousY,
                            "OBJ", String.valueOf(this.objectId),
                            "TX", String.valueOf(x), "TY", String.valueOf(y));
                    this.previousEdgeIndex = 0;
                    previousX = x;
                    previousY = y;
                    previousHash = 0;
                    return;
                }
                setObject(compressedLocation);
                // previous edge index and previous coordinate index already set
            } else {
                // The current source-node-to-edge-index is the
                // target-node-to-edge-index of the last edge
                if (this.previousEdgeIndex == 0) {
                    // The previous edge index is not set which means
                    // that this is the first segment of the current object.
                    // We need to find the edge or create it
                    findOrCreateFirstNode();

                    if (this.previousEdgeIndex == 0) {
                        // Neither a node nor an edge exist with the current source coordinates.
                        // This also means that there is no connection to this edge yet.
                        this.previousEdgeIndex = -topology.size();

                        // Use the coordinates that just have been added.
                        previousTargetCoordinateIndex = this.coordinates.size() - 2;

                        // Ensure the coordinates for the target node have been created or find
                        // an existing edge (targetEdgeIndex!=this.previousEdgeIndex)
                        final int targetEdgeIndex = getTargetEdgeEnsureCoordinates(x, y);

                        // Get coordinate index of the target edge
                        final int newIndexCoordIndex = getEdgeCoordIndex(targetEdgeIndex);

                        // Add indices for source and target coordinates
                        this.topology.add(compress(previousTargetCoordinateIndex, newIndexCoordIndex));

                        addAnglesArc(x, y, centerX, centerY);

                        if (targetEdgeIndex != this.previousEdgeIndex) {
                            // the edge is connected to an existing edge
                            final int targetCcwNext = adjustCcwNexts(targetEdgeIndex, this.previousEdgeIndex,
                                    compressedLocation);
                            topology.add(toRight(targetCcwNext));
                        } else {
                            // First edge, set empty source-ccw-next and target-ccw-next
                            topology.add(0);
                        }

                        // Just set the object ID here, no checks required
                        addObject(compressedLocation);

                        // simply advance
                        this.previousEdgeIndex = -(this.topology.size() - TOPOLOGY_FIELDS_SIZE);
                        this.previousTargetCoordinateIndex = newIndexCoordIndex;
                    } else {
                        // Create a new edge and connect it with two existing nodes.
                        // Ensure the coordinates for the target node have been created
                        final int current = topology.size();
                        final int targetEdgeIndex = getTargetEdgeEnsureCoordinates(x, y);

                        final int targetEdgeCoordIndex = getEdgeCoordIndex(targetEdgeIndex);
                        this.previousTargetCoordinateIndex = this.getEdgeCoordIndex(this.previousEdgeIndex);
                        // Add coordinates
                        this.topology.add(compress(previousTargetCoordinateIndex, targetEdgeCoordIndex));

                        addAnglesArc(x, y, centerX, centerY);

                        // Adjust the ccw-nexts
                        final int sourceCcwNext = adjustCcwNexts(previousEdgeIndex, current, compressedLocation);
                        if (-current != targetEdgeIndex) {
                            final int targetCcwNext = adjustCcwNexts(targetEdgeIndex, -current, compressedLocation);
                            topology.add(compress(sourceCcwNext, targetCcwNext));
                        } else {
                            topology.add(toLeft(sourceCcwNext));
                        }

                        // Just set the object ID here, no checks required
                        addObject(compressedLocation);

                        this.previousEdgeIndex = -(this.topology.size() - TOPOLOGY_FIELDS_SIZE);
                        previousTargetCoordinateIndex = targetEdgeCoordIndex;
                    }
                } else {
                    // Appending to an existing edge
                    // Ensure that the coordinates for the target node are created
                    final int targetEdgeIndex = getTargetEdgeEnsureCoordinates(x, y);

                    // Add source coordinates which were the target in the previous edge
                    this.topology.add(compress(previousTargetCoordinateIndex, getEdgeCoordIndex(targetEdgeIndex)));

                    addAnglesArc(x, y, centerX, centerY);

                    // Adjust the ccw-next of the previous edge node target
                    connectCurrentEdge(targetEdgeIndex, compressedLocation);

                    addObject(compressedLocation);

                    this.previousEdgeIndex = -(this.topology.size() - TOPOLOGY_FIELDS_SIZE);
                    if (targetEdgeIndex > 0) {
                        this.previousTargetCoordinateIndex = getLeft(this.topology.getQuick(targetEdgeIndex));
                    } else {
                        this.previousTargetCoordinateIndex = getRight(this.topology.getQuick(-targetEdgeIndex));
                    }
                }
            }
            previousX = x;
            previousY = y;
            previousHash = hash;
        }

    }

    @Override
    public void coordinate2d(final double x, final double y, final long hash, final long compressedLocation, final int ignore) {
        if (previousHash != 0 && previousHash != hash) {
            createEdgeOrSetObject(x, y, hash, compressedLocation);
        }
        previousX = x;
        previousY = y;
        previousHash = hash;
    }

    private void createEdgeOrSetObject(final double x, final double y, final long hash, final long compressedLocation) {
        ++objectsProcessed;

        if (checkForExistingEdgeOrAdd(hash)) {
            // Edge already exists, find it
            if (!findEdge(x, y)) {
                // The edge can not be found, most likely due to previous errors in the geometric object.
                errorCollector.collectError(EDGE_NOT_FOUND,
                        this.previousX, this.previousY,
                        "OBJ", String.valueOf(this.objectId),
                        "TX", String.valueOf(x), "TY", String.valueOf(y));
                this.previousEdgeIndex = 0;
                this.previousHash = 0;
                return;
            }
            setObject(compressedLocation);
            // previous edge index and previous coordinate index already set
        } else {
            // The current source-node-to-edge-index is the
            // target-node-to-edge-index of the last edge
            if (this.previousEdgeIndex == 0) {
                // The previous edge index is not set which means
                // that this is the first segment of the current object.
                // We need to find the edge or create it
                findOrCreateFirstNode();

                if (this.previousEdgeIndex == 0) {
                    // Neither a node nor an edge exist with the current source coordinates.
                    // This also means that there is no connection to this edge yet.
                    this.previousEdgeIndex = -topology.size();

                    // Use the coordinates that just have been added.
                    previousTargetCoordinateIndex = coordinates.size() - 2;

                    // Ensure the coordinates for the target node have been created or find
                    // an existing edge (targetEdgeIndex!=this.previousEdgeIndex)
                    final int targetEdgeIndex = getTargetEdgeEnsureCoordinates(x, y);

                    // Get coordinate index of the target edge
                    final int newIndexCoordIndex = getEdgeCoordIndex(targetEdgeIndex);

                    // Add indices for source and target coordinates
                    this.topology.add(compress(previousTargetCoordinateIndex, newIndexCoordIndex));

                    addAnglesForLineSegment(x, y);

                    if (targetEdgeIndex != this.previousEdgeIndex) {
                        // the edge is connected to an existing edge
                        final int targetCcwNext = adjustCcwNexts(targetEdgeIndex, this.previousEdgeIndex, compressedLocation);
                        topology.add(toRight(targetCcwNext));
                    } else {
                        // First edge, set empty source-ccw-next and target-ccw-next
                        topology.add(0);
                    }

                    // Just set the object ID here, no checks required
                    addObject(compressedLocation);

                    // simply advance
                    this.previousEdgeIndex = -(this.topology.size() - TOPOLOGY_FIELDS_SIZE);
                    this.previousTargetCoordinateIndex = newIndexCoordIndex;
                } else {
                    // Create a new edge and connect it with two existing nodes.
                    // Ensure the coordinates for the target node have been created
                    final int current = topology.size();
                    final int targetEdgeIndex = getTargetEdgeEnsureCoordinates(x, y);

                    final int targetEdgeCoordIndex = getEdgeCoordIndex(targetEdgeIndex);
                    this.previousTargetCoordinateIndex = this.getEdgeCoordIndex(this.previousEdgeIndex);
                    // Add coordinates
                    this.topology.add(compress(previousTargetCoordinateIndex, targetEdgeCoordIndex));

                    addAnglesForLineSegment(x, y);

                    // Adjust the ccw-nexts
                    final int sourceCcwNext = adjustCcwNexts(previousEdgeIndex, current, compressedLocation);
                    if (-current != targetEdgeIndex) {
                        final int targetCcwNext = adjustCcwNexts(targetEdgeIndex, -current, compressedLocation);
                        topology.add(compress(sourceCcwNext, targetCcwNext));
                    } else {
                        topology.add(toLeft(sourceCcwNext));
                    }

                    // Just set the object ID here, no checks required
                    addObject(compressedLocation);

                    this.previousEdgeIndex = -(this.topology.size() - TOPOLOGY_FIELDS_SIZE);
                    previousTargetCoordinateIndex = targetEdgeCoordIndex;
                }
            } else {
                // Appending to an existing edge
                // Ensure that the coordinates for the target node are created
                final int targetEdgeIndex = getTargetEdgeEnsureCoordinates(x, y);

                // Add source coordinates which were the target in the previous edge
                this.topology.add(compress(previousTargetCoordinateIndex, getEdgeCoordIndex(targetEdgeIndex)));

                addAnglesForLineSegment(x, y);

                // Adjust the ccw-next of the previous edge node target
                connectCurrentEdge(targetEdgeIndex, compressedLocation);

                addObject(compressedLocation);

                this.previousEdgeIndex = -(this.topology.size() - TOPOLOGY_FIELDS_SIZE);
                if (targetEdgeIndex > 0) {
                    this.previousTargetCoordinateIndex = getLeft(this.topology.getQuick(targetEdgeIndex));
                } else {
                    this.previousTargetCoordinateIndex = getRight(this.topology.getQuick(-targetEdgeIndex));
                }
            }
        }
    }

    private void addObject(final long compressedLocation) {
        if (this.exterior) {
            topology.add(toLeft(objectId));
        } else {
            topology.add(toLeft(-objectId));
        }
        this.topology.add(compressedLocation);
        this.topology.add(0);
    }

    private void collectErrorInnerRingSelfIntersection(final long compressedLocation, final int locationOffset) {
        final int edgeCoordIndex = getEdgeCoordIndex(previousEdgeIndex);
        errorCollector.collectError(
                INNER_RING_SELF_INTERSECTION,
                this.coordinates.get(edgeCoordIndex),
                this.coordinates.get(edgeCoordIndex + 1),
                // existing object id
                "IS", String.valueOf(topology.getQuick(locationOffset)),
                // new object overlapping id
                "O", String.valueOf(compressedLocation));
    }

    private void collectErrorOverlappingEdges(final long compressedLocation, final int locationOffset) {
        final int edgeCoordIndex = getEdgeCoordIndex(previousEdgeIndex);
        errorCollector.collectError(
                RING_OVERLAPPING_EDGES,
                this.coordinates.get(edgeCoordIndex),
                this.coordinates.get(edgeCoordIndex + 1),
                // existing object id
                "IS", String.valueOf(topology.getQuick(locationOffset)),
                // new object overlapping id
                "O", String.valueOf(compressedLocation));
    }

    private void setObject(final long compressedLocation) {
        final int reqIndex = abs(previousEdgeIndex);
        final long previousObjs = topology.getQuick(reqIndex + OBJ_OFFSET);
        final int leftObj = getLeft(previousObjs);
        final int rightObj = getRight(previousObjs);
        final int newObjectId = this.exterior ? objectId : -objectId;
        if (leftObj > 0) {
            // there is an outer ring on the left side
            if (this.exterior && previousEdgeIndex < 0) {
                // the negative previousEdgeIndex indicates that the edge has been created
                // by the outer ring on the left side.

                // TODO distinguish with an OUTER_RING_INVALID_CURVE_ORIENTATION error ?
                collectErrorOverlappingEdges(compressedLocation, reqIndex + LEFT_LOCATION_INDEX);
                /* errorCollector.collectError( // OUTER_RING_INVALID_CURVE_ORIENTATION, RING_OVERLAPPING_EDGES, "E1", String.valueOf(compressedLocation), "E2", String.valueOf(topology.getQuick(reqIndex + LEFT_LOCATION_INDEX)) ); */
            } else if (rightObj != 0) {
                // there is also inner or outer ring on the the right side
                /* errorCollector.collectError( RING_OVERLAPPING_EDGES, "E1", String.valueOf(compressedLocation), "E2", String.valueOf(topology.getQuick(reqIndex + RIGHT_LOCATION_INDEX))); */
                collectErrorOverlappingEdges(compressedLocation, reqIndex + RIGHT_LOCATION_INDEX);
            } else {
                // Set on right side
                topology.setQuick(reqIndex + OBJ_OFFSET, compress(leftObj, newObjectId));
                topology.setQuick(reqIndex + RIGHT_LOCATION_INDEX, compressedLocation);
            }
        } else if (leftObj < 0) {
            // there is an inner ring on the left side
            if (!this.exterior && previousEdgeIndex < 0) {
                // the negative previousEdgeIndex indicates that the edge has been created
                // by the inner ring on the left side.
                /* errorCollector.collectError( RING_OVERLAPPING_EDGES, "E1", String.valueOf(compressedLocation), "E2", String.valueOf(topology.getQuick(reqIndex + LEFT_LOCATION_INDEX))); */
                collectErrorOverlappingEdges(compressedLocation, reqIndex + LEFT_LOCATION_INDEX);
            } else if (rightObj != 0) {
                // there is also inner or outer ring on the the right side
                /* errorCollector.collectError( RING_OVERLAPPING_EDGES, "E1", String.valueOf(compressedLocation), "E2", String.valueOf(topology.getQuick(reqIndex + RIGHT_LOCATION_INDEX))); */
                collectErrorOverlappingEdges(compressedLocation, reqIndex + RIGHT_LOCATION_INDEX);
            } else {
                // Set on right side
                topology.setQuick(reqIndex + OBJ_OFFSET, compress(leftObj, newObjectId));
                topology.setQuick(reqIndex + RIGHT_LOCATION_INDEX, compressedLocation);
            }
        } else {
            assert previousEdgeIndex < 0;
            // Left side is free. If this is an inner ring, check on other side that no other inner
            // ring is intersecting with this one
            if (!this.exterior && rightObj < 0) {
                /* errorCollector.collectError( INNER_RING_SELF_INTERSECTION, "t", String.valueOf(compressedLocation), "o", String.valueOf(topology.getQuick(reqIndex + RIGHT_LOCATION_INDEX))); */

                collectErrorInnerRingSelfIntersection(compressedLocation, reqIndex + RIGHT_LOCATION_INDEX);
                this.previousEdgeIndex = 0;
                this.previousHash = 0;
            } else {
                // Set on left side
                topology.setQuick(reqIndex + OBJ_OFFSET, compress(newObjectId, rightObj));
                topology.setQuick(reqIndex + LEFT_LOCATION_INDEX, compressedLocation);
            }
        }
    }

    private void addAnglesForLineSegment(final double targetX, final double targetY) {
        addAngles(targetX, targetY,
                coordinates.getQuick(previousTargetCoordinateIndex),
                coordinates.getQuick(previousTargetCoordinateIndex + 1));
    }

    private void addAngles(final double targetX, final double targetY, final double sourceX, final double sourceY) {
        topology.add(
                Double.doubleToLongBits(
                        atan2(
                                targetY - sourceY,
                                targetX - sourceX)));
        topology.add(
                Double.doubleToLongBits(
                        atan2(
                                sourceY - targetY,
                                sourceX - targetX)));
    }

    private void addAnglesArc(final double targetX, final double targetY, final double sourceX, final double sourceY) {
        topology.add(
                Double.doubleToLongBits(
                        atan2(
                                targetY - sourceY,
                                targetX - sourceX)));
        topology.add(
                Double.doubleToLongBits(
                        atan2(
                                sourceY - targetY,
                                sourceX - targetX)));
    }

    private int getEdgeCoordIndex(final int edgeIndex) {
        if (edgeIndex == -topology.size()) {
            return this.coordinates.size() - 2;
        } else if (edgeIndex > 0) {
            return getLeft(this.topology.getQuick(edgeIndex + COORDINATE_OFFSET));
        } else {
            return getRight(this.topology.getQuick(-(edgeIndex) + COORDINATE_OFFSET));
        }
    }

    static long calcCoordHashCode(final double x, final double y) {
        long coordHash = 0xcbf29ce484222325L;
        coordHash = coordHash * 0x100000001b3L * Double.doubleToLongBits(x);
        coordHash = coordHash * 0x100000001b3L * Double.doubleToLongBits(y * y);
        return coordHash * 0x100000001b3L;
    }

    private boolean checkForExistingEdgeOrAdd(final long hash) {
        long edgeHash = 48527;
        edgeHash = edgeHash * 194977 * previousHash;
        edgeHash = edgeHash * 194977 * hash;
        // spread
        edgeHash = edgeHash * Math.max(previousHash, hash);
        return !edgeExistence.add(edgeHash);
    }

    int internalTopologicalDataSize() {
        return topology.size();
    }

    long getTopologicalData(final int edgeIndex) {
        return topology.getQuick(edgeIndex);
    }

    int internalCoordinateSize() {
        return coordinates.size();
    }

    double getCoordinate(final int coordinateIndex) {
        return coordinates.getQuick(coordinateIndex);
    }

    private boolean checkIfInteriorEdgeAndMark(final int edgeIndex) {
        final int rEdgeIndex = abs(edgeIndex);
        if (topology.getQuick(rEdgeIndex + RIGHT_LOCATION_INDEX) == 0) {
            final long obj = topology.getQuick(rEdgeIndex + OBJ_OFFSET);
            // Check if this is an exterior edge
            if (getLeft(obj) < 0) {
                // mark it
                topology.setQuick(rEdgeIndex + RIGHT_LOCATION_INDEX, Integer.MIN_VALUE);
                return true;
            }
        }
        return false;
    }

    int findNextEmptyInterior(final int currentPos) {
        final int maxEdgeSearch = 100_000;
        for (int i = currentPos; i < topology.size(); i += TOPOLOGY_FIELDS_SIZE) {
            // Check if an object is set on the right side
            if (checkIfInteriorEdgeAndMark(i)) {
                // Check if this is an interior edge
                final int emptyInteriorEdge = i;
                int next = getLeftOrRightByIndex(-emptyInteriorEdge, CCWI_OFFSET);
                int steps = 0;
                for (; steps < maxEdgeSearch && emptyInteriorEdge != next && -emptyInteriorEdge != next &&
                        checkIfInteriorEdgeAndMark(next); steps++) {
                    next = getLeftOrRightByIndex(-next, CCWI_OFFSET);
                }
                return emptyInteriorEdge;
            }
        }
        return topology.size();
    }

    /**
     * Check if edge is an exterior edge without an object on the right side and mark the edge by setting a max value on the right side.
     *
     * @param edgeIndex
     *            edge index
     * @return true if edge is an exterior edge without an object on the right side
     */
    private boolean checkIfOutsideExteriorEdgeAndMark(final int edgeIndex) {
        final int rEdgeIndex = abs(edgeIndex);
        if (topology.getQuick(rEdgeIndex + RIGHT_LOCATION_INDEX) == 0) {
            final long obj = topology.getQuick(rEdgeIndex + OBJ_OFFSET);
            // Check if this is an exterior edge
            if (getLeft(obj) > 0) {
                // mark it
                topology.setQuick(rEdgeIndex + RIGHT_LOCATION_INDEX, Integer.MIN_VALUE);
                return true;
            }
        }
        return false;
    }

    /**
     * Find free standing edges in the topological data strcuture
     *
     * @param firstFoundFreeStandingSurfaceEdges
     *            array for adding the first edge of the free standing surface
     * @param freeStandingSurfaceEdgeSize
     *            edge count of the free standing surface
     */
    void findFreeStandingSurfaces(final TIntArrayList firstFoundFreeStandingSurfaceEdges,
            final TIntArrayList freeStandingSurfaceEdgeSize) {
        final int maxEdgeSearch = 1_000_000;

        for (int i = TOPOLOGY_FIELDS_SIZE; i < topology.size(); i += TOPOLOGY_FIELDS_SIZE) {
            // Check if an object is set on the right side
            if (checkIfOutsideExteriorEdgeAndMark(i)) {
                // Found the first exterior edge without anything on the right side.
                // Mark this edge and begin to iterate along the edges until we either
                // return to this edge or find an edge that has something on the right side.
                final int freestandingSurfaceEdge = i;
                int next = getLeftOrRightByIndex(-freestandingSurfaceEdge, CCWI_OFFSET);
                int steps = 0;
                for (; steps < maxEdgeSearch && freestandingSurfaceEdge != next && -freestandingSurfaceEdge != next &&
                        checkIfOutsideExteriorEdgeAndMark(next); steps++) {
                    next = getLeftOrRightByIndex(-next, CCWI_OFFSET);
                }
                if (steps > 1) {
                    firstFoundFreeStandingSurfaceEdges.add(freestandingSurfaceEdge);
                    freeStandingSurfaceEdgeSize.add(steps);
                }
            }
        }
    }

    static double getSourceAngle(final TLongArrayList topology, final int index) {
        return Double.longBitsToDouble(topology.getQuick(index + SOURCE_ANGLE_OFFSET));
    }

    static double getTargetAngle(final TLongArrayList topology, final int index) {
        return Double.longBitsToDouble(topology.getQuick(index + TARGET_ANGLE_OFFSET));
    }

    double getAngleByIndex(final int index) {
        if (index > 0) {
            return getSourceAngle(this.topology, index);
        } else {
            return getTargetAngle(this.topology, -index);
        }
    }

    public static int getLeft(final long compressedValue) {
        return (int) (compressedValue >> 32);
    }

    public static int getRight(final long compressedValue) {
        return (int) compressedValue;
    }

    public static long compress(final int left, final int right) {
        return toLeft(left) | toRight(right);
    }

    public static long toLeft(final int left) {
        return (((long) left) << 32);
    }

    public static long toRight(final int right) {
        return (right & 0xFFFFFFFFL);
    }

    static void setRight(final TLongArrayList topology, final int index, final int right) {
        final long v = topology.getQuick(index);
        topology.setQuick(index, compress((int) (v >> 32), right));
    }

    static void setLeft(final TLongArrayList topology, final int index, final int left) {
        final long v = topology.getQuick(index);
        topology.setQuick(index, (v & 0x00000000FFFFFFFFL | (long) left << 32));
    }

    private int setLeftRightCcwNextIfNullOrGet(final int index, final int value) {
        if (index > 0) {
            return setLeftIfNullOrGet(this.topology, index + CCWI_OFFSET, value);
        } else {
            return setRightIfNullOrGet(this.topology, (-index) + CCWI_OFFSET, value);
        }
    }

    static int setRightIfNullOrGet(final TLongArrayList topology, final int index, final int right) {
        final long v = topology.getQuick(index);
        final int r = getRight(v);
        if (r == 0) {
            topology.setQuick(index, compress((int) (v >> 32), right));
            return 0;
        }
        return r;
    }

    static int setLeftIfNullOrGet(final TLongArrayList topology, final int index, final int left) {
        final long v = topology.getQuick(index);
        final int l = getLeft(v);
        if (l == 0) {
            topology.setQuick(index, (v & 0x00000000FFFFFFFFL | (long) left << 32));
            return 0;
        }
        return l;
    }

    private void setLeftOrRightCcwNextByIndex(final int index, final int value) {
        if (index > 0) {
            setLeft(topology, index + CCWI_OFFSET, value);
        } else {
            setRight(topology, (-index) + CCWI_OFFSET, value);
        }
    }

    int getLeftOrRightByIndex(final int index, final int propertyOffset) {
        if (index > 0) {
            return getLeft(topology.getQuick(index + propertyOffset));
        } else {
            return getRight(topology.getQuick((-index) + propertyOffset));
        }
    }

    private int setLeftOrRightCcwNextAndGetPrevious(final int index, final int value) {
        if (index > 0) {
            final long v = topology.getQuick(index + CCWI_OFFSET);
            final int l = getLeft(v);
            topology.setQuick(index + CCWI_OFFSET, (v & 0x00000000FFFFFFFFL | (long) value << 32));
            return l;
        } else {
            final long v = topology.getQuick((-index) + CCWI_OFFSET);
            final int l = getRight(v);
            topology.setQuick((-index) + CCWI_OFFSET, compress((int) (v >> 32), value));
            return l;
        }
    }

    /**
     * Returns the number of edges
     *
     * @return edge size
     */
    int size() {
        return (this.topology.size() - TOPOLOGY_FIELDS_SIZE) / TOPOLOGY_FIELDS_SIZE;
    }

    /**
     * Set the next object ID
     */
    @Override
    public void nextGeometricObject() {
        ++objectId;
        previousHash = 0;
        previousEdgeIndex = 0;
        exterior = true;
    }

    /**
     * Switch the boundary to interior
     */
    public void nextInterior() {
        previousHash = 0;
        previousEdgeIndex = 0;
        exterior = false;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TopologyBuilder{ ");
        sb.append("currentObjectId=");
        sb.append(objectId);
        sb.append(", objectsProcessed=");
        sb.append(objectsProcessed);
        sb.append(", edges=");
        sb.append(size());
        sb.append(", coordinates=");
        sb.append((coordinates.size() - 2) / 2);
        sb.append(", lookupCollisions=");
        sb.append(coordinateCollisionHashToEdgeMap.size());
        sb.append(", lookupErrors=");
        sb.append(errors);
        sb.append('}');
        return sb.toString();
    }

    int internalGetCurrentObjectId() {
        return objectId;
    }

    int internalGetObjectsProcessed() {
        return objectsProcessed;
    }

    int internalGetLookupCollisions() {
        return coordinateCollisionHashToEdgeMap.size();
    }

    int internalGetLookupErrors() {
        return errors;
    }
}
