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
package de.interactive_instruments.etf.bsxm.topox;

/**
 * Interface for accessing topological data
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface Topology {

    String getName();

    /**
     * Represents a vertex in the graph
     */
    interface Node {

        /**
         * Get X coordinate
         *
         * @return X coordinate
         */
        double x();

        /**
         * Get Y coordinate
         *
         * @return Y coordinate
         */
        double y();

        /**
         * Returns an edge of which this is a node
         *
         * @return an edge
         */
        Edge anEdge();

        /**
         * Find an edge connected to this node
         *
         * @param node
         *            source or target node used to find the edge
         * @return NULL if the Node is not connected to the source or target node of this edge or the connected edge
         */
        Edge edge(final Node node);
    }

    /**
     * A pair of two adjacent nodes
     */
    interface Edge {
        /**
         * Get the source node of this edge
         *
         * @return a Node
         */
        Node source();

        /**
         * Get the target node of this edge
         *
         * @return a Node
         */
        Node target();

        /**
         * Get the angle of the source node
         *
         * @return angle in radians
         */
        double sourceAngle();

        /**
         * Get the angle of the target node
         *
         * @return angle in radians
         */
        double targetAngle();

        /**
         * Get the internal ID of the object on the left side
         *
         * @return encoded ID
         */
        int leftInternalObjectId();

        /**
         * Get the internal ID of the object on the right side
         *
         * @return encoded ID
         */
        int rightInternalObjectId();

        /**
         * Get the object on the left side
         *
         * @return object
         */
        long leftObject();

        /**
         * Get the object on the right side
         *
         * @return object
         */
        long rightObject();

        /**
         * Get the next edge counter-clockwise from the source node
         *
         * @return counter-clockwise edge
         */
        Edge sourceCcwNext();

        /**
         * Get the next edge counter-clockwise from the target node
         *
         * @return counter-clockwise edge
         */
        Edge targetCcwNext();

        /**
         * Find an edge connected to this edge
         *
         * @param node
         *            source or target node used to find the edge
         * @return NULL if the Node is not connected to the source or target node of this edge or the connected edge
         */
        Edge edge(final Node node);
    }

    /**
     * xmin, xmax, ymin, ymax
     *
     * @return
     */
    double[] bbox();

    /**
     * Get an edgy by source X/Y and target X/Y coordinates
     *
     * @param xSource
     *            source X coordinate
     * @param ySource
     *            source Y coordinate
     * @param xTarget
     *            target X coordinate
     * @param yTarget
     *            target Y coordinate
     * @return the edge or null if a edge with these coordinates does not exist
     */
    Edge edge(final double xSource, final double ySource, final double xTarget, final double yTarget);

    /**
     * Get a node by X/Y coordinate
     *
     * @param x
     *            X coordinate
     * @param y
     *            Y coordinate
     * @return the node or null if a node with these coordinates does not exist
     */
    Node node(final double x, final double y);

    /**
     * Returns edges that have objects only on one side
     *
     * Contract: This method may only be called once. The result should be cached by the caller.
     *
     * @return edge iterator
     */
    Iterable<Edge> emptyInteriors();

    /**
     * Returns edges of free-standing surfaces
     *
     * Contract: This method may only be called once. The result should be cached by the caller.
     *
     * @return edge iterator
     */
    Iterable<Edge> freeStandingSurfaces();
}
