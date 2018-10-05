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

	interface Node {
		/**
		 * Returns an edge that is connected to this node.
		 *
		 * @return an edge
		 */
		Edge anEdge();

		/**
		 * Get X coordinate
		 * @return
		 */
		double x();

		/**
		 * Get Y coordinate
		 * @return
		 */
		double y();
	}

	interface Edge {
		Node source();

		Node target();

		double sourceAngle();

		double targetAngle();

		/**
		 * Left object
		 * @return
		 */
		int leftObject();

		/**
		 * Right object
		 * @return
		 */
		int rightObject();

		/**
		 * Object on edge
		 *
		 * @return ID
		 */
		int object();

		Edge sourceCcwNext();

		Edge targetCcwNext();

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
