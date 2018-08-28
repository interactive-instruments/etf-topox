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

package de.interactive_instruments.etf.bsxm;

import de.interactive_instruments.IFile;

import java.io.*;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class GeoJsonWriter implements Closeable {

	private final BufferedWriter writer;
	private final static String initalStructure =
			"{\"type\":\"FeatureCollection\",\"features\":[";
	private final IFile file;
	private int previousHash;

	enum WritingMode {
		DONE,
		INIT,
		FEATURE,
		FEATURE_POINT,
		COORDINATES,
		COORDINATES_ORIENTATION_SWITCH,
		COORDINATES_INTERIOR;
	}
	private WritingMode mode = WritingMode.INIT;

	public GeoJsonWriter(final IFile file) throws IOException {
		this.file = file;
		file.expectFileIsWritable();
		this.writer = new BufferedWriter(new FileWriter(file));
		previousHash=0;
	}

	public IFile getFile() {
		return this.file;
	}

	public void init() throws IOException {
		writer.write(initalStructure);
		mode = WritingMode.INIT;
	}

	public void writePointFeature(final String id, final String error, final String xCoordinate, final String yCoordinate) throws IOException {
		switch (mode) {
			case DONE:
			case FEATURE:
			case COORDINATES_ORIENTATION_SWITCH:
				throw new IllegalStateException();
			case COORDINATES:
			case COORDINATES_INTERIOR:
				endFeature();
			case FEATURE_POINT:
				writer.write(',');
			case INIT:
		}
		mode=WritingMode.FEATURE_POINT;
		writer.write("{\"type\":\"Feature\","
				+ "\"properties\":{\"id\":\"");
		writer.write(id);
		writer.write("\",\"e\":\"");
		writer.write(error);
		writer.write("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[");
		writer.write(xCoordinate);
		writer.write(',');
		writer.write(yCoordinate);
		writer.write("]}}");

	}

	public void startFeature(final String id) throws IOException {
		switch (mode) {
			case DONE:
			case FEATURE:
			case COORDINATES_ORIENTATION_SWITCH:
				throw new IllegalStateException();
			case COORDINATES:
			case COORDINATES_INTERIOR:
				endFeature();
			case FEATURE_POINT:
				writer.write(',');
			case INIT:
		}
		mode=WritingMode.FEATURE;
		previousHash=0;
		writer.write("{\"type\":\"Feature\","
				+ "\"properties\":{\"id\":\"");
		writer.write(id);
		writer.write("\"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");
	}

	public void addCoordinates(final byte[] data) throws IOException {
		switch (mode) {
			case DONE:
			case INIT:
			case FEATURE_POINT:
			case COORDINATES_INTERIOR:
			case COORDINATES_ORIENTATION_SWITCH:
				throw new IllegalStateException();
			case COORDINATES:
				writer.write(",");
			case FEATURE:
		}
		mode=WritingMode.COORDINATES;
		writeCoordinates(data);
	}

	private void writeCoordinates(final byte[] data) throws IOException {
		writer.write('[');

		boolean tuple=false;
		boolean written=false;
		int hash=13;
		int startPos=0;
		final char[] buffer = new char[data.length];
		for (int i = 0; i < data.length; i++) {
			if(data[i]==' ') {
				if(tuple) {
					if(hash!=previousHash) {
						if(written) {
							writer.write(',');
							writer.write('[');
						}
						writer.write(buffer,startPos,i-startPos);
						writer.write(']');
						written=true;
					}
					previousHash=hash;
					hash=13;
					startPos=i+1;
					tuple=false;
				}else{
					tuple=true;
				}
				buffer[i]=',';
			}else {
				hash ^= data[i];
				hash *= 31;
				buffer[i]=(char)data[i];
			}
		}
		if(tuple && hash!=previousHash) {
			if(written) {
				writer.write(',');
				writer.write('[');
			}
			writer.write(buffer, startPos, data.length-startPos);
			writer.write(']');
		}
		previousHash=hash;
	}

	public void nextInterior() throws IOException {
		switch (mode) {
			case DONE:
			case INIT:
			case FEATURE:
			case FEATURE_POINT:
			case COORDINATES_ORIENTATION_SWITCH:
				throw new IllegalStateException();
			case COORDINATES:
			case COORDINATES_INTERIOR:
				writer.write("],[");
		}
		mode=WritingMode.COORDINATES_ORIENTATION_SWITCH;
	}

	public void addCoordinatesInterior(final byte[] data) throws IOException {
		switch (mode) {
			case DONE:
			case INIT:
			case FEATURE:
			case FEATURE_POINT:
			case COORDINATES:
				throw new IllegalStateException();
			case COORDINATES_INTERIOR:
				writer.write(',');
			case COORDINATES_ORIENTATION_SWITCH:
		}
		mode=WritingMode.COORDINATES_INTERIOR;
		writeCoordinates(data);
	}

	private void endFeature() throws IOException {
		writer.write("]]}}");
	}

	@Override
	public void close() throws IOException {
		switch (mode) {
			case COORDINATES:
			case COORDINATES_INTERIOR:
				endFeature();
			case INIT:
			case FEATURE:
			case FEATURE_POINT:
			case COORDINATES_ORIENTATION_SWITCH:
		}
		writer.write("]}");
		writer.close();
		mode=WritingMode.DONE;
	}
}
