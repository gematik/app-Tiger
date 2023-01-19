/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.lib.parser.model.gherkin;

public interface JSON {

    String STATUS = "Status";
    String MODUS = "Modus";
    String TESTABLAUF = "Testablauf/Testdurchführung";
    String INTERNE_ID = "Interne Id";
    String DESCRIPTION = "Description";
    String TESTSTUFE = "Teststufe";
    String TITEL = "Titel";
    String PRODUKT_TYP = "ProduktTyp";
    String VORBEDINGUNG = "Vorbedingung";
    String PRIO = "Priorität";
    String TESTART = "Testart";
    String AFOLINKS = "AFO-Verknüpfungen";
    String AF_ID = "Anwendungsfälle";
    String DATAVARIANTS = "Datenvarianten";
    String NEGATIVE_TF = "NegativTestfall";
}
