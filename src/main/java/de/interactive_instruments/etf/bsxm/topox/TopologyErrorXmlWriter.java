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

import de.interactive_instruments.SUtils;
import de.interactive_instruments.exceptions.ExcUtils;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 *
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class TopologyErrorXmlWriter implements TopologyErrorCollector {

	private static final String TOPOX_ERROR_NS = "http://www.interactive-instruments.de/etf/topology-error/1.0";
	private static final String TOPOX_ERROR_NS_PREFIX = "ete";
	private final Theme theme;
	private final XMLStreamWriter writer;
	private int counter=0;

	public TopologyErrorXmlWriter(final Theme theme, final XMLStreamWriter writer) {
		this.theme = theme;
		this.writer = writer;
	}

	@Override
	public void init() {
		try {
			writer.writeStartDocument("UTF-8", "1.0");
			writer.writeStartElement(TOPOX_ERROR_NS_PREFIX, "TopologicalErrors", TOPOX_ERROR_NS);
			writer.setPrefix(TOPOX_ERROR_NS_PREFIX, TOPOX_ERROR_NS);
			writer.writeNamespace(TOPOX_ERROR_NS_PREFIX, TOPOX_ERROR_NS);
			writer.writeAttribute("id", theme.getIdentifier());
			writer.writeAttribute("theme", theme.getName());
			writer.writeAttribute("geometry", theme.getGeometry());
			writer.writeAttribute("selection", theme.getSelection());
		}catch (final XMLStreamException e) {
			throw new IllegalStateException("Initialization failed: ", e);
		}
	}

	@Override
	public void collectError(final TopologyErrorType topologyErrorType, final String...parameter) {
		try {
			writer.writeStartElement("e");
			writer.writeAttribute("i", Integer.toString(++counter));
			writer.writeAttribute("t", topologyErrorType.toString());
			if(parameter!=null) {
				for (int i = 0; i < parameter.length; i++) {
					writer.writeStartElement(parameter[i]);
					writer.writeCharacters(parameter[++i]);
					writer.writeEndElement();
				}
			}
			writer.writeEndElement();
		}catch (final XMLStreamException e) {
			final String message = theme.getName() + " " +
					topologyErrorType.toString() + " : " + SUtils.concatStr(" ", parameter);
			throw new IllegalStateException("Error writing topological error: "+message, e);
		}
	}

	@Override
	public void release() {
		try {
			// TopologicalErrors
			writer.writeEndElement();
			writer.writeEndDocument();
			writer.flush();
			writer.close();
		} catch (final XMLStreamException e) {
			ExcUtils.suppress(e);
		}
	}
}
