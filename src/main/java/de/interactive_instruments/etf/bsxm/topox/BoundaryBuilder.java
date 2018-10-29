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

import static de.interactive_instruments.etf.bsxm.topox.TopologyErrorType.BOUNDARY_EDGE_INVALID;
import static de.interactive_instruments.etf.bsxm.topox.TopologyErrorType.BOUNDARY_POINT_DETACHED;

/**
 * An object to verify that boundaries lie exactly on edges.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class BoundaryBuilder {
	public final PosListParser parser;

	public BoundaryBuilder(final Theme theme) {
		final InternalHandler handler = new InternalHandler(theme, theme.topologyErrorCollector);
		this.parser = new HashingPosListParser(handler);
	}

	private static class InternalHandler implements HashingSegmentHandler {
		private final Theme theme;
		private final TopologyErrorCollector errorCollector;

		private Topology.Node previousNode;

		InternalHandler(final Theme theme, final TopologyErrorCollector errorCollector) {
			this.theme = theme;
			this.errorCollector = errorCollector;
		}

		@Override
		public void coordinate2d(final double x, final double y, final long hash, final long location, final int type) {
			if(previousNode==null) {
				previousNode=theme.topology.node(x,y);
				if(previousNode==null) {
					errorCollector.collectError(BOUNDARY_POINT_DETACHED,
							"X", String.valueOf(x),
							"Y", String.valueOf(y),
							"IS", String.valueOf(location)
					);
				}
			}else{
				final Topology.Node nextNode = theme.topology.node(x, y);
				if(nextNode==null) {
					errorCollector.collectError(BOUNDARY_POINT_DETACHED,
							"X", String.valueOf(x),
							"Y", String.valueOf(y),
							"IS", String.valueOf(location)
					);
					previousNode = null;
				}else{
					final Topology.Edge edge = previousNode.edge(nextNode);
					if(edge==null) {
						errorCollector.collectError(BOUNDARY_EDGE_INVALID,
								"X", String.valueOf(x),
								"Y", String.valueOf(y),
								"IS", String.valueOf(location),
								"X2", String.valueOf(previousNode.x()),
								"Y2", String.valueOf(previousNode.y())
						);
					}
					previousNode = nextNode;
				}
			}
		}

		@Override
		public void nextGeometricObject() {
			previousNode = null;
		}
	}
}
