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

import static de.interactive_instruments.etf.bsxm.topox.TopologyErrorType.*;

import java.util.ArrayList;
import java.util.List;

/**
 * An object to verify that boundaries lie exactly on edges.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class EdgeValidator {
    public final PosListParser parser;
    private final InternalHandler handler;
    private final TopologyErrorCollector errorCollector;

    public EdgeValidator(final Theme theme, int handlerType) {
        if (handlerType == 1) {
            this.handler = new SingleBoundaryHandler(theme, theme.topologyErrorCollector);
        } else {
            this.handler = new MultipleBoundaryHandler(theme, theme.topologyErrorCollector);
        }
        this.parser = new HashingPosListParser(handler);
        this.errorCollector = theme.topologyErrorCollector;
    }

    private static abstract class InternalHandler implements HashingSegmentHandler {
        private final Theme theme;
        private final TopologyErrorCollector errorCollector;

        protected Topology.Node previousNode;
        protected Topology.Edge previousEdge;

        InternalHandler(final Theme theme, final TopologyErrorCollector errorCollector) {
            this.theme = theme;
            this.errorCollector = errorCollector;
        }

        @Override
        public void coordinate2d(final double x, final double y, final long hash, final long location, final int type) {
            if (previousNode == null) {
                previousNode = theme.topology.node(x, y);
                if (previousNode == null) {
                    previousEdge = null;
                    errorCollector.collectError(POINT_DETACHED,
                            x, y,
                            "IS", String.valueOf(location));
                }
            } else {
                final Topology.Node nextNode = theme.topology.node(x, y);
                if (nextNode == null) {
                    errorCollector.collectError(POINT_DETACHED,
                            x, y,
                            "IS", String.valueOf(location));
                    previousNode = null;
                    previousEdge = null;
                } else {
                    previousEdge = previousNode.edge(nextNode);
                    if (previousEdge == null) {
                        errorCollector.collectError(EDGE_POINTS_INVALID,
                                x, y,
                                "IS", String.valueOf(location),
                                "X2", String.valueOf(previousNode.x()),
                                "Y2", String.valueOf(previousNode.y()));
                    }
                    previousNode = nextNode;
                }
            }
        }

        private Topology.Edge getPreviousEdge() {
            return this.previousEdge;
        }
    }

    private static class MultipleBoundaryHandler extends InternalHandler {

        MultipleBoundaryHandler(Theme theme, TopologyErrorCollector errorCollector) {
            super(theme, errorCollector);
        }

        @Override
        public void nextGeometricObject() {
            previousEdge = null;
            previousNode = null;
        }
    }

    private static class SingleBoundaryHandler extends InternalHandler {

        SingleBoundaryHandler(Theme theme, TopologyErrorCollector errorCollector) {
            super(theme, errorCollector);
        }

        @Override
        public void nextGeometricObject() {
            previousEdge = null;
        }
    }

    public long getLeft() {
        return this.handler.getPreviousEdge() != null ? this.handler.getPreviousEdge().leftObject() : 0;
    }

    public long getRight() {
        return this.handler.getPreviousEdge() != null ? this.handler.getPreviousEdge().rightObject() : 0;
    }

    public void report(final int result) {
        final Topology.Edge edge = this.handler.getPreviousEdge();
        if (edge != null) {
            if (result == 1) {
                reportRight(edge);
            } else if (result == 2) {
                reportLeft(edge);
            } else {
                reportInvalid(edge);
            }
        }
    }

    private void reportLeft(final Topology.Edge edge) {
        if (edge.leftObject() == 0) {
            errorCollector.collectError(EDGE_MISSING_LEFT,
                    edge.source().x(), edge.source().y(),
                    "X2", String.valueOf(edge.target().x()),
                    "Y2", String.valueOf(edge.target().y()));
        } else {
            errorCollector.collectError(EDGE_INVALID_LEFT,
                    edge.source().x(), edge.source().y(),
                    "IS", String.valueOf(edge.leftObject()),
                    "X2", String.valueOf(edge.target().x()),
                    "Y2", String.valueOf(edge.target().y()));
        }
    }

    private void reportRight(final Topology.Edge edge) {
        if (edge.rightObject() == 0) {
            errorCollector.collectError(EDGE_MISSING_RIGHT,
                    edge.source().x(), edge.source().y(),
                    "X2", String.valueOf(edge.target().x()),
                    "Y2", String.valueOf(edge.target().y()));
        } else {
            errorCollector.collectError(EDGE_INVALID_RIGHT,
                    edge.source().x(), edge.source().y(),
                    "IS", String.valueOf(edge.rightObject()),
                    "X2", String.valueOf(edge.target().x()),
                    "Y2", String.valueOf(edge.target().y()));
        }
    }

    private void reportInvalid(final Topology.Edge edge) {
        final List<String> params = new ArrayList<>(8);
        if (edge.rightObject() != 0) {
            params.add("R");
            params.add(String.valueOf(edge.rightObject()));
        }
        if (edge.leftObject() != 0) {
            params.add("L");
            params.add(String.valueOf(edge.leftObject()));
        }
        params.add("X2");
        params.add(String.valueOf(edge.target().x()));
        params.add("Y2");
        params.add(String.valueOf(edge.target().y()));

        errorCollector.collectError(EDGE_INVALID,
                edge.source().x(), edge.source().y(),
                params.toArray(new String[0]));
    }
}
