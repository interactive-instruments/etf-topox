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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.stream.Stream;

import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.cmd.CreateDB;
import org.basex.core.cmd.DropDB;
import org.basex.core.cmd.XQuery;
import org.basex.query.QueryException;
import org.basex.query.util.pkg.RepoManager;
import org.junit.jupiter.api.*;

import de.interactive_instruments.IFile;
import de.interactive_instruments.exceptions.ExcUtils;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class TopoXTest {

	private Context ctx = new Context();

	private final static IFile ddtDirectory = new IFile("src/test/resources/ddt");
	private final static IFile outputDirectory = new IFile("build/tmp/ddt/results");
	private final static IFile tmpOutputDirectory = new IFile("build/tmp/ddt/tmp_outputs");
	private final static String TOPOX_INSTALL_PATH = "build/libs/TopoX.xar";
	private final static String DB_NAME = "TOPOX-JUNIT-TEST-DB-000";

	@BeforeAll
	static void setUp() throws QueryException, IOException {
		final RepoManager repoManger = new RepoManager(new Context());
		try {
			repoManger.delete("ETF TopoX");
		} catch (QueryException ign) {
			ExcUtils.suppress(ign);
		}
		repoManger.install(TOPOX_INSTALL_PATH);
		outputDirectory.ensureDir();
		tmpOutputDirectory.ensureDir();
	}

	@AfterEach
	@BeforeEach
	void dropDb() throws BaseXException {
		new DropDB(DB_NAME).execute(ctx);
	}

	private OutputStream outputStream(final IFile outputFile) {
		try {
			return new FileOutputStream(outputFile);
		} catch (FileNotFoundException e) {
			fail("Error writing to file", e);
			return null;
		}
	}

	@TestFactory
	Stream<DynamicTest> createTests() {
		return BsxDataDrivenTest.createDDT(ddtDirectory).stream()
				.map(t -> DynamicTest.dynamicTest(
						"Data Test: " + t.getName(),
						() -> {
							final IFile testTmpOutputDirectory = tmpOutputDirectory.secureExpandPathDown(t.getName())
									.ensureDir();
							new CreateDB(DB_NAME, t.getDataPath()).execute(ctx);
							final XQuery xQuery = t.getQuery();
							xQuery.bind("$tmpOutputDirectory", testTmpOutputDirectory.getAbsolutePath());
							xQuery.bind("$dbname", DB_NAME);
							final IFile outputFile = new IFile(outputDirectory, t.getName() + ".xml");
							xQuery.execute(ctx, outputStream(outputFile));
							t.compare(outputFile);
						}));
	}

}
