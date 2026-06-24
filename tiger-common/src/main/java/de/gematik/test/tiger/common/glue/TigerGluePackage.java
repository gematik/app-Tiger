/*
 *
 * Copyright 2021-2026 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package de.gematik.test.tiger.common.glue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Cucumber glue class so that its package is auto-discovered by Tiger's runner and unioned
 * into the effective {@code cucumber.glue} list.
 *
 * <p>Pure marker annotation; no attributes. Apply it to any class inside the glue package you want
 * Tiger to discover (typically the glue class itself). The runner scans {@code
 * de.gematik.test.tiger} for {@code @TigerGluePackage}-bearing classes and adds each annotated
 * class's package to the glue list before delegating to Cucumber. Mirrors the existing
 * classpath-scan discovery used for {@code @TigerServerType}.
 *
 * <p>Lives in {@code tiger-common} so both core and out-of-tree extensions can reference it without
 * forcing a compile-time dependency on {@code tiger-test-lib}.
 *
 * <p>Use it once per glue package — multiple annotated classes in the same package collapse to one
 * entry. The annotation has no behavioural effect at compile time; it is only read reflectively at
 * test-runner startup.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TigerGluePackage {}
