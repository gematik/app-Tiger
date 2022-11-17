/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.converter.brainpool;

public class BrainpoolAlgorithmSuiteIdentifiers {

    protected static final String INTERNAL_BRAINPOOL256_USING_SHA256 = "BP256R1";
    protected static final String INTERNAL_BRAINPOOL384_USING_SHA384 = "BP384R1";
    protected static final String INTERNAL_BRAINPOOL512_USING_SHA512 = "BP512R1";
    public static final String BRAINPOOL256_USING_SHA256 = getValueAndExecuteInitialisation(INTERNAL_BRAINPOOL256_USING_SHA256);
    public static final String BRAINPOOL384_USING_SHA384 = getValueAndExecuteInitialisation(INTERNAL_BRAINPOOL384_USING_SHA384);
    public static final String BRAINPOOL512_USING_SHA512 = getValueAndExecuteInitialisation(INTERNAL_BRAINPOOL512_USING_SHA512);

    private static String getValueAndExecuteInitialisation(final String value) {
        BrainpoolCurves.init();
        return value;
    }
}
