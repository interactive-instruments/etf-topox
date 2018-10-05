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
 * Segment handler
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface HashingSegmentHandler {

	void coordinate2d(final double x, final double y, final long hash, final long location, final int type);

	void coordinates2d(final double[] coordinates, final long hashesAndLocations[], final int type);

	void nextGeometricObject();
}
