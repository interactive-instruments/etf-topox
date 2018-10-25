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

/**
 * Flyweight interface for a Topology
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
		 * Returns an edge of which this is a node
		 *
		 * @return an edge
		 */
		Edge anEdge();

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
		 * Get the ID of the object on the left side
		 *
		 * @return encoded ID
		 */
		int leftObject();

		/**
		 * Get the ID of the object on the right side
		 *
		 * @return encoded ID
		 */
		int rightObject();

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
		 * @param node source or target node used to find the edge
		 * @return NULL if the Node is not connected to the
		 * source or target node of this edge or the connected edge
		 */
		Edge edge(Node node);
	}

	/**
	 * xmin, xmax, ymin, ymax
	 *
	 * @return
	 */
	double[] bbox();

	Edge edge(final double xSource, final double ySource, final double xTarget, final double yTarget);

	Node node(final double x, final double y);

	/**
	 * Returns edges that have objects only on one side
	 *
	 * @return edge iterator
	 */
	Iterable<Edge> borders();
}
