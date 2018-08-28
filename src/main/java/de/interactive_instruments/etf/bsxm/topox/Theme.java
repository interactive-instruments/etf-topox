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
public class Theme {
	private final String name;
	private final String identifier;
	private final String geometry;
	private final String path;

	public Theme(final String name, final String path, final String identifier, final String geometry) {
		this.name = name;
		this.identifier = identifier;
		this.geometry = geometry;
		this.path = path;
	}

	public String getName() {
		return name;
	}

	public String getIdentifier() {
		return identifier;
	}

	public String getGeometry() {
		return geometry;
	}

	public String getSelection() {
		return path;
	}
}
