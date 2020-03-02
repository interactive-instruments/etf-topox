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
 * For development purposes
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface TopologyMXBean {

    String getName();

    int getCurrentObjectId();

    int getObjectsProcessed();

    int getEdgeSize();

    int getCoordinatesSize();

    int getLookupCollisions();

    int getLookupErrors();

    String getEdgesAtPoint(double x, double y);

    String getEdge(double x1, double y1, double x2, double y2);
}
