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
 * TopoX error types
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
enum TopologyErrorType {

	/**
	 * Duplicate edges in two features
	 *
	 * Outputs:
	 * - X, Y for the point of failure
	 * - IS for the existing object that is on the edge
	 * - O for the object that collides with the existing one
	 *
	 * TODO rename to TOPO_OVERLAPPING_EDGES
	 */
	RING_OVERLAPPING_EDGES,

	/**
	 * 	Ring intersection
	 *
	 * 	Outputs:
	 * 	- X, Y for the point of failure
	 * 	- IS for the object where the intersection has been detected
	 * 	- CW for the object which is clockwise from the error
	 * 	- CCW for the object which is counter-clockwise from the error
	 *
	 * 	TODO rename to TOPO_INTERSECTION
	 */
	RING_INTERSECTION,

	/**
	 * 	Hole
	 *
	 * 	Outputs:
	 * 	- X, Y for the point of failure
	 * 	- IS for the object where the hole has been detected
	 */
	HOLE_EMPTY_INTERIOR,

	/**
	 * 	Free standing surface
	 *
	 * 	Outputs:
	 * 	- X, Y for the point of failure
	 * 	- IS for the object where the free standing surface has been detected
	 */
	FREE_STANDING_SURFACE,

	/**
	 * Detached boundary point
	 *
	 * A boundary point was defined that could not be found in the
	 * topological data.
	 *
	 * Outputs:
	 * - X, Y for the point of failure
	 * - IS for the object that defined the boundary
	 */
	BOUNDARY_POINT_DETACHED,

	/**
	 * Invalid boundary edge
	 *
	 * Edge points have been found but the points
	 * are not connected.
	 *
	 * Outputs:
	 * - X, Y for the point of failure
	 * - IS for the object that defined the boundary
	 * - X2, Y2 for the second point of failure
	 */
	BOUNDARY_EDGE_INVALID,


	//////////////////////////////////////////////////////////////////////////
	// Deprecated and removed later
	/**
	 * The curve orientation of the outer ring is invalid
	 */
	OUTER_RING_INVALID_CURVE_ORIENTATION,

	/**
	 * Self intersection of the inner ring
	 */
	INNER_RING_SELF_INTERSECTION,

	/**
	 * The inner ring orientation is invalid or intersects with the outer ring
	 */
	INVALID_INTERIOR_ORIENTATION_OR_OUTSIDE_EXTERIOR,

	/**
	 * Innter ring intersection in general
	 */
	INTERIOR_INTERSECTION,
	//////////////////////////////////////////////////////////////////////////

	// Subsequent errors that may occur due to previous errors
	// -or generally errors that indicate invalid data.
	//
	// In general, these errors can be ignored if errors have previously
	// been reported with the error codes from above.
	//
	// If only the error codes from below are reported , this can
	// be considered as an internal error!
	//////////////////////////////////////////////////////////////////////////

	/**
	 * An edge could not be found. This is most likely a consequential
	 * error if others occurred and for instance the connection between
	 * two points has been invalidated in the data structure. If this is
	 * the only type of error that occurred, then this may indicate a
	 * bug in the software.
	 */
	EDGE_NOT_FOUND,

	/**
	 * An error occurred while connecting an additional edge to a point.
	 * Attempting to traverse the angles of all connected edges has exceeded
	 * the maximum number of possible steps. If this is the only type of
	 * error that occurred, then this may indicate a bug in the software.
	 */
	INVALID_ANGLE

	//////////////////////////////////////////////////////////////////////////
}
