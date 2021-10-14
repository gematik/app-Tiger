# Tiger in einem ![](images/mvn-logo.png) Projekt nutzen

Um Tiger in ein Projekt Maven einzubinden sind folgende Schritte notwendig:

* Tiger Abhängigkeiten zum Projekt hinzufügen
* JUnit4 Treiberklasse für die Feature Dateien im Projekt erstellen
* Maven Failsafe Plugin konfigurieren
* Maven Serenity Plugin konfigurieren

[Ein Beispiel pom.xml findet sich im examples Verzeichnis](examples/tigerOnly/pom.xml)

## Abhängigkeiten

Wichtig ist hierbei, dass Tiger ZWEIFACH eingebunden wird. Die erste Abhängigkeit ist zum Test jar Artefakt und
ermöglicht die Nutzung der TGR steps in Feature Dateien.

Das zweite Jar beinhaltet Hilfsklassen und die Cucumber Tiger Hooks Klasse.

```xml

<project>
    ...
    <properties>
        <!-- Entweder von maven central:
          https://mvnrepository.com/artifact/de.gematik.test/tiger-test-lib
          oder vom Gematik internen Nexus
          https://build.top.local/nexus/#nexus-search;quick~tiger-test-lib
        -->
        <version.tiger>0.12.0</version.tiger>
        <version.serenity.core>2.4.34</version.serenity.core>
        <version.serenity.maven.plugin>2.4.34</version.serenity.maven.plugin>
    </properties>

    <dependencies>
        <dependency>
            <groupId>de.gematik.test</groupId>
            <artifactId>tiger-test-lib</artifactId>
            <version>${version.tiger}</version>
            <type>test-jar</type>
            <exclusions>
                <exclusion>
                    <groupId>de.gematik</groupId>
                    <artifactId>rbelLogger</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>de.gematik.test</groupId>
            <artifactId>tiger-test-lib</artifactId>
            <version>${version.tiger}</version>
            <exclusions>
                <exclusion>
                    <groupId>de.gematik</groupId>
                    <artifactId>rbelLogger</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
    ...
</project>
```

## JUnit4 Treiberklasse(n)

Serenity unterstützt derzeit noch KEIN Junit5, weswegen für das Starten der Tigertestsuiten eine JUnit4 Treiberklasse
erstellt werden.

Bei Projekten wo auch JUnit5 verwendet wird, muss zusätzlich noch eine dependency zur Junit5 vintage engine hinzugefügt
werden, da sonst die JUnit4 Testtreiber nicht gefunden bzw. ausgeführt werden und ein leerer Serenity Report erstellt
wird.

```xml

<dependency>
    <groupId>org.junit.vintage</groupId>
    <artifactId>junit-vintage-engine</artifactId>
    <version>5.7.2</version>
</dependency>

```

### Manuell eine Treiberklasse für jedes Featurefile anlegen

```java
package de.gematik.test.tiger.integration.YOURPROJECT;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = {"src/test/resources/features/YOURFEATURE.feature"},
    plugin = {"json:target/cucumber-parallel/1.json"},
    monochrome = false,
    glue = {"de.gematik.test.tiger.hooks", "de.gematik.test.tiger.glue",
        "ANY ADDITIONAL PACKAGES containing GLUE or HOOKS code"})
public class JUnit4TestDriver {

}
```

### oder dynamisch zur Laufzeit erzeugen

Unter Verwendung des Maven jvmparalalell plugins können die pro Feature Datei notwendigen Testtreiberklassen auch
automatisiert erstellt werden.

Hierfür muss eine Velocity Template Datei im Projekt (unter src/test/resources) hinterlegt werden:

```java
#parse("/array.java.vm")

#if($packageName)
package $packageName;
#end##

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.BeforeClass;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = {"$featureFile"},
    plugin = #stringArray($plugins),
    monochrome = $monochrome,
#if(!$featureFile.contains(".feature:") && $mytags)
    tags= #stringArray($mytags),
#end
    glue= #stringArray($glue))
public class $className {

}
```

Als zweiten Schritt muss im pom.xml das folgende Plugin eingefügt werden. Im Glue Segemnt sind alle projektspezifischen
weiteren packages hinzuzufügen und der Wert beim Attribute packageName ist entsprechend anzupassen.

```xml

<plugin>
    <groupId>com.github.temyers</groupId>
    <artifactId>cucumber-jvm-parallel-plugin</artifactId>
    <version>4.2.0</version>
    <executions>
        <execution>
            <configuration>
                <customVmTemplate>src/test/resources/cucumber-serenity-runner.vm</customVmTemplate>
                <glue>
                    <package>de.gematik.test.tiger.hooks</package>
                    <package>de.gematik.test.tiger.glue</package>
                    <!-- add your packages here -->
                </glue>
                <packageName>tiger.integration.YOURPROJECTNAMEHERE</packageName>
                <parallelScheme>FEATURE</parallelScheme>
            </configuration>
            <goals>
                <goal>generateRunners</goal>
            </goals>
            <id>generateRunners</id>
            <phase>generate-test-sources</phase>
        </execution>
    </executions>
</plugin>
```

![](images/attention.png) **Derzeit wird dies nur für englischsprachige Schlüsselwörter unterstützt**

## Maven failsafe plugin

Dieses Plugin triggert nun durch Angabe des entsprechenden Include Filters das Ausführen der JUnit4 Testtreiberklassen.

```xml

<plugin>
    <artifactId>maven-failsafe-plugin</artifactId>
    <groupId>org.apache.maven.plugins</groupId>
    <version>3.0.0-M5</version>
    <executions>
        <execution>
            <id>run-tests</id>
            <phase>integration-test</phase>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
            <configuration>
                <includes>
                    <include>**/JUnit4TestDriver.java</include>
                    <!-- Diesen Ausdruck an die im Projekt verwendete Bezeichnung der Treiberklassen anpassen -->
                </includes>
                <environmentVariables>
                    <TIGER_ACTIVE>1</TIGER_ACTIVE>
                    <!-- Dieses Umgebungsvariable triggert die Hooks der Tiger Test Lib um die Testumgebung hochzufahren -->
                    <!-- und die Tiger Proxies zu aktivieren -->
                </environmentVariables>
                <skipITs>false</skipITs>
                <forkMode>never</forkMode>
                <!-- Um eine verspätete Ausgabe der Logs aus den externen Jar Knoten zu verhinden -->
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Maven serenity reports plugin

Nach dem Testlauf erstellt dieses Plugin aus den Testergebnissen einen Serenity Testbericht.

```xml

<plugin>
    <groupId>net.serenity-bdd.maven.plugins</groupId>
    <artifactId>serenity-maven-plugin</artifactId>
    <version>${version.serenity.maven.plugin}</version>
    <dependencies>
        <dependency>
            <groupId>net.serenity-bdd</groupId>
            <artifactId>serenity-core</artifactId>
            <version>${version.serenity.core}</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <id>serenity-reports</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>reports</goal>
                <goal>aggregate</goal>
            </goals>
        </execution>
    </executions>
</plugin> 
```
