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
package de.interactive_instruments.etf.bsxm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.filefilter.OrFileFilter;
import org.basex.core.cmd.XQuery;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

import de.interactive_instruments.IFile;
import de.interactive_instruments.io.FilenameExtensionFilter;
import de.interactive_instruments.io.GmlAndXmlFilter;
import de.interactive_instruments.io.MultiFileFilter;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class BsxDataDrivenTest {
	private final IFile[] dataFiles;
	private final IFile queryDir;
	private final IFile defaultQueryFile;
	private final IFile[] expectedFiles;
	private final XmlUnitDetailFormatter formatter = new XmlUnitDetailFormatter();

	public class DDTResBundle {
		final int pos;

		private DDTResBundle(final int pos) {
			this.pos = pos;
		}

		public String getDataPath() {
			return dataFiles[pos].getAbsolutePath();
		}

		public XQuery getQuery() {
			try {
				final String queryFileName = dataFiles[pos].getFilenameWithoutExt() + ".xq";
				final IFile queryFile = queryDir.secureExpandPathDown(queryFileName);
				if (queryFile.exists()) {
					return new XQuery(queryFile.readContent().toString());
				} else {
					return new XQuery(defaultQueryFile.readContent().toString());
				}
			} catch (final IOException e) {
				fail("Error loading query file ", e);
				return null;
			}
		}

		public void compare(final IFile resultFile) {
			String expectedXml = null;
			try {
				expectedXml = new IFile(expectedFiles[pos]).readContent("UTF-8").toString();
			} catch (final IOException e) {
				fail("Could not read " + expectedFiles[pos].getAbsolutePath(), e);
			}
			String resultXml = null;
			try {
				resultXml = resultFile.readContent("UTF-8").toString();
			} catch (final IOException e) {
				fail("Could not read " + resultFile.getAbsolutePath());
			}

			final Diff diff = DiffBuilder.compare(Input.fromString(resultXml))
					.withTest(Input.fromString(expectedXml))
					.checkForSimilar().checkForIdentical()
					.ignoreComments()
					.ignoreWhitespace()
					.normalizeWhitespace()
					.ignoreElementContentWhitespace()
					.build();

			if (diff.hasDifferences()) {
				final Difference difference = diff.getDifferences().iterator().next();
				assertEquals(formatter.getControlDetailDescription(difference.getComparison()),
						formatter.getTestDetailDescription(difference.getComparison()));
			}
		}

		public String getName() {
			try {
				return dataFiles[pos].getFilenameWithoutExt();
			} catch (IOException e) {
				return Integer.toString(pos);
			}
		}

		@Override
		public String toString() {
			return getName();
		}
	}

	private BsxDataDrivenTest(final IFile ddtDirectory) {
		final MultiFileFilter xmlFileFilter = GmlAndXmlFilter.instance().filename();
		final FilenameExtensionFilter zipFileFilter = new FilenameExtensionFilter(".zip");
		final MultiFileFilter fileFilter = xmlFileFilter.or(zipFileFilter);
		dataFiles = ddtDirectory.secureExpandPathDown("data").listIFiles(fileFilter);
		queryDir = ddtDirectory.secureExpandPathDown("queries");
		defaultQueryFile = queryDir.secureExpandPathDown("default.xq");
		assertTrue(defaultQueryFile.exists(), "Default XQuery file missing");
		expectedFiles = ddtDirectory.secureExpandPathDown("expected").listIFiles(xmlFileFilter);
		assertTrue(dataFiles.length > 0, "No XML files found in test data directory");
		assertEquals(dataFiles.length, expectedFiles.length,
				"Number of test data files does not match the number of expected result files");
	}

	private ArrayList<DDTResBundle> getDDTResBundles() {
		return IntStream.range(0, dataFiles.length).mapToObj(DDTResBundle::new)
				.collect(Collectors.toCollection(() -> new ArrayList<>(dataFiles.length)));
	}

	public static List<DDTResBundle> createDDT(final IFile ddtDirectory) {
		return new BsxDataDrivenTest(ddtDirectory).getDDTResBundles();
	}

}
