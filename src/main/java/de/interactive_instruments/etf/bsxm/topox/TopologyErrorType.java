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
 * Todo: discuss error types and document it
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
	 * - N for the object that collides with the existing one
	 *
	 * TODO rename to OVERLAPPING_EDGES
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
	 * 	TODO rename to INTERSECTION
	 */
	RING_INTERSECTION,

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
	 * The edge cannot be found. This most likely
	 * will happen if there are previous errors in the geometric object.
	 */
	EDGE_NOT_FOUND,

	/**
	 * Calculated edges of a node are invalid. This most likely
	 * will happen if previous edges of the geometric object are invalid.
	 */
	INVALID_ANGLE

	//////////////////////////////////////////////////////////////////////////
}
