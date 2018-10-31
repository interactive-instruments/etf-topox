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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import org.basex.query.QueryModule;
import org.junit.jupiter.api.Test;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class ArchTest {

	private final JavaClasses gmlgeoxClasses = new ClassFileImporter().withImportOption(
			new ImportOption.DontIncludeTests()).importPackages("de.interactive_instruments.etf.bsxm");

	@Test
	public void checkForDeterministicAnnotations() {
		final Class topoXClass = TopoX.class;
		final Method[] methods = topoXClass.getDeclaredMethods();
		for (final Method m : methods) {
			if (m.getName().startsWith("parse") || m.getName().startsWith("next")) {
				assertNull(m.getAnnotation(QueryModule.Deterministic.class),
						"parse...() or next...() methods should not be annotated with the Deterministic annotation");
			}
		}
	}

	/*
	TODO

	@Test
	public void testAccessPorts() {
		final ArchRule rule = classes().that().haveModifier(JavaModifier.PUBLIC).should()
				.haveFullyQualifiedName("de.interactive_instruments.etf.bsxm.TopoX")
				.orShould()
				.haveFullyQualifiedName("de.interactive_instruments.etf.bsxm.topox.Theme")
				.orShould()
				.haveFullyQualifiedName("de.interactive_instruments.etf.bsxm.topox.BoundaryBuilder")
				.because("The TopoX, Theme and Boundary classes should be the only "
						+ "entry points for this module.");
		rule.check(gmlgeoxClasses);
	}

	*/

}
