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
 * An interface for parsing the direct positions of geometric objects
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface PosListParser {

	/**
	 * Parse the direct positions of a byte array.
	 *
	 * @param byteSequence byte array containing direct positions
	 * @param location location information of the direct positions, e.g. an ID
	 * @param geoType Geometry type
	 */
	void parseDirectPositions(final byte[] byteSequence, final long location, final int geoType);

	/**
	 * Parse the direct positions of a byte array.
	 *
	 * @param sequence char sequence containing direct positions
	 * @param location location information of the direct positions, e.g. an ID
	 * @param geoType Geometry type
	 */
	void parseDirectPositions(final CharSequence sequence, final long location, final int geoType);

	/**
	 * Parse the direct positions of a byte array. The second argument overrides a
	 * previous dimension() call temporarily.
	 *
	 * @param byteSequence byte array containing direct positions
	 * @threeDCoordinates set to true if the dimension is 3, false for 2D coordinates
	 * @param location location information of the direct positions, e.g. an ID
	 * @param geoType Geometry type
	 */
	void parseDirectPositions(final byte[] byteSequence, final boolean threeDCoordinates, final long location,
			final int geoType);

	/**
	 * Parse the direct positions of a byte array. The second argument overrides a
	 * previous dimension() call temporarily.
	 *
	 * @param sequence char sequence containing direct positions
	 * @threeDCoordinates set to true if the dimension is 3, false for 2D coordinates
	 * @param location location information of the direct positions, e.g. an ID
	 * @param geoType Geometry type
	 */
	void parseDirectPositions(final CharSequence sequence, final boolean threeDCoordinates, final long location,
			final int geoType);

	/**
	 * Set the dimension of the next parsed coordinates.
	 *
	 * @param threeDCoordinates set to true if the dimension is 3, false for 2D coordinates
	 */
	void dimension(final boolean threeDCoordinates);

	/**
	 * Parse the next geometric object.
	 */
	void nextGeometricObject();
}
