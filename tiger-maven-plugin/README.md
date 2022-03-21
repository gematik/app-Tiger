# Tiger Maven Driver Generator Plugin

This mvn plugin allows to dynamically generate JUnit driver classes that will start Serenity based test runs.

To activate this feature in your maven project add the following plugin into your <build><plugins> section:

```xml

<plugin>
    <groupId>de.gematik.test</groupId>
    <artifactId>tiger-maven-plugin</artifactId>
    <version><!-- mandatory: put current version of plugin here -->></version>
    <configuration>
        <!-- optional -->
        <basedir>src/test/resources/features</basedir>
        <!-- mandatory -->
        <includes>
            <include>**/*.feature</include>
        </includes>
        <!-- mandatory -->
        <glues>
            <glue>org.company.yourproject.gluecode</glue>
            <glue>org.company.yourproject.hooks</glue>
        </glues>
        <!-- optional -->
        <driverPackage>org.company.yourproject.drivers</driverPackage>
        <!-- optional -->
        <driverClassName>TestDriver${ctr}</driverClassName>
    </configuration>
    <executions>
        <execution>
            <goals>
                <!-- mandatory -->
                <goal>generate-drivers</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Configuring the plugin

The following configuration properties are supported:

* **List[include] includes** (mandatory) ... list of include patterns for feature files in Ant format (*\/directory/**
  .feature)
* **List[glue] glues** (mandatory) ... list of packages to be included as glue or hooks code
* String basedir (default: local working directory) ... root folder from where to apply includes and excludes
* List[exclude] excludes (default: empty)) ... list of exclusion patterns for feature files in Ant format (*
  \/directory/**.feature)
* boolean skip (default: false) ... flag whether to skip the execution of this plugin
* String driverPackage (default: de.gematik.test.tiger.serenity.drivers) ... package of the to be generated driver class
* String driverClassName (default: Junit4SerenityTestDriver${ctr}) ... Name of the to be generated driver class. Note
  the ctr token which is mandatory! For more details see section about custom template file.
* String templateFile (default: null which means that the plugin will use the built-in template file) ... Optional path
  to a custom template file to be used for generating the driver Java source code file.
    * Currently supports the following list of tokens:
        * ${ctr} ... counter value that is unique and incremented for each feature file.
        * ${package} ... this is where the package declaration of the driver class will be added to. * Either empty or
          of the pattern "package xxx.yyy.zzz;" where xxx.yyy.zzz is replaced with the * configured driverPackage
          configuration property.
        * ${driverClassName} ... name of the driver class (with the ctr token already being replaced * by the counter
          value).
        * ${feature} ... path to the feature file.
        * ${glues} ... comma separated list of glue/hook packages as specified by the glues configuration property
