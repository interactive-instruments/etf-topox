/**
 * Copyright 2010-2019 interactive instruments GmbH
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.interactive_instruments.etf.bsxm.topox.geojson.writer.GeoJsonWriter;

/**
 * The Theme object bundles all objects that are used to create topological information for one or multiple Features, including error handling, parsing and building topological data structure.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class Theme implements Serializable {

    public final String name;
    public final TopologyErrorCollector topologyErrorCollector;
    public final String errorFile;
    public final GeoJsonWriter geoJsonWriter;
    public final PosListParser parser;
    private boolean freeStandingSurfacesDetected = false;

    final Topology topology;
    private final TopologyBuilder topologyBuilder;

    public Theme(final String name, final TopologyErrorCollector topologyErrorCollector, final String errorFile,
            final GeoJsonWriter geoJsonWriter, final TopologyBuilder topologyBuilder) {
        this.name = name;
        this.topologyErrorCollector = topologyErrorCollector;
        this.errorFile = errorFile;
        this.geoJsonWriter = geoJsonWriter;
        this.topologyBuilder = topologyBuilder;
        this.topology = new TopologyStore(topologyBuilder);
        this.parser = new HashingPosListParser(topologyBuilder);
    }

    public void nextInterior() {
        this.topologyBuilder.nextInterior();
    }

    public int detectHoles() {
        int count = 0;
        for (final Topology.Edge emptyInterior : topology.emptyInteriors()) {
            count++;
            topologyErrorCollector.collectError(HOLE_EMPTY_INTERIOR,
                    emptyInterior.source().x(),
                    emptyInterior.source().y(),
                    "IS",
                    String.valueOf(emptyInterior.leftObject()));
        }
        return count;
    }

    private void checkFreeStandingSurfacesCalled() {
        if (freeStandingSurfacesDetected) {
            throw new IllegalStateException("Free-standing surface detection can only be called once");
        }
        freeStandingSurfacesDetected = true;
    }

    public int detectFreeStandingSurfaces() {
        checkFreeStandingSurfacesCalled();
        int count = 0;
        for (final Topology.Edge freeStandingSurface : topology.freeStandingSurfaces()) {
            count++;
            topologyErrorCollector.collectError(FREE_STANDING_SURFACE,
                    freeStandingSurface.source().x(),
                    freeStandingSurface.source().y(),
                    "IS",
                    String.valueOf(freeStandingSurface.leftObject()));
        }
        return count;
    }

    public int detectFreeStandingSurfacesWithAllObjects() {
        checkFreeStandingSurfacesCalled();
        int count = 0;
        for (final Topology.Edge freeStandingSurface : topology.freeStandingSurfaces()) {
            count++;

            Topology.Edge nextConnectedEdge = freeStandingSurface.targetCcwNext();
            final int maxCount = 1_000_000;
            int c = 0;
            // compressed indexes of the geometric objects
            final List<Long> compressedObjIds = new ArrayList<>();
            // object ids
            final Set<Integer> objIds = new HashSet<>();
            while (!nextConnectedEdge.equals(freeStandingSurface) && c++ < maxCount) {
                final long objId = nextConnectedEdge.leftObject();
                if (objId != 0 && objIds.add(DataCompression.preObject(objId))) {
                    compressedObjIds.add(objId);
                }
                nextConnectedEdge = nextConnectedEdge.targetCcwNext();
            }

            final String[] isIds = new String[objIds.size() * 2];
            int i = 0;
            for (final Long compressedObjId : compressedObjIds) {
                isIds[i++] = "IS";
                isIds[i++] = String.valueOf(compressedObjId);
            }

            topologyErrorCollector.collectError(
                    FREE_STANDING_SURFACE_DETAILED,
                    freeStandingSurface.source().x(),
                    freeStandingSurface.source().y(),
                    isIds);
        }
        return count;
    }

    public TopologyMXBean getMBean() {
        return (TopologyMXBean) topology;
    }

    @Override
    public String toString() {
        return topologyBuilder.toString();
    }
}
