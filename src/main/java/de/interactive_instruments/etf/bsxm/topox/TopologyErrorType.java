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

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
enum TopologyErrorType {

	RING_INTERSECTION_CONGRUENT_EDGES,

	RING_INTERSECTION,

	OUTER_RING_INVALID_CURVE_ORIENTATION,

	INNER_RING_SELF_INTERSECTION,

	INVALID_INTERIOR_ORIENTATION_OR_OUTSIDE_EXTERIOR,

	INTERIOR_INTERSECTION,

	// Subsequent errors that may occur due to previous errors
	// -or generally errors that indicate invalid data

	EDGE_NOT_FOUND,
	
	INVALID_ANGLE
}
