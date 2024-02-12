# Tiger

![TigerLogo](doc/images/tiger2-plain.svg)

## Pitching the Tiger

So you heard there is a new tool in the house? But what is tiger for?

Take a look at our short pitch video explaining the basic idea of Tiger

[![](doc/images/tiger-promo-screenie.png)](https://youtu.be/eJJZDeuFlyI)

## Target audience

* Internal test teams that want to perform iop or e2e tests for reference or product implementation.

* Internal test teams that want to perform acceptance tests for reference or product implementation.

Within the test teams we focus on testers, with not necessarily too much programming skills.
It must be easy to set up and implement tests against service nodes, potentially reusing test steps from other test
suites.
For non BDD test suites it must be easily possible to utilize most of the features of Tiger by directly calling public
interface methods.

* External product teams, which need to do automated IOP/E2E testing for their health applications.

## Tiger-User-Manual

Every information at one place: check out our Tiger-User-Manual!

* HTML: [Tiger-User-Manual](https://gematik.github.io/app-Tiger/Tiger-User-Manual.html)

* PDF: [Tiger-User-Manual](https://gematik.github.io/app-Tiger/Tiger-User-Manual.pdf)

## Use cases

![UseCaseDiagramme](doc/specification/tiger_use_cases.white.svg)

## Architecture in a nutshell

![ComponentsDiagramme](doc/specification/tiger_components.white.svg)

## Product specification draft

For more details please check out more Plantuml diagrammes at [specification folder](doc/specification)

## Simple example project

[For a standalone example see here in our examples section](doc/examples/tigerOnly)

## Using other libraries / source code

The package "de.gematik.test.tiger.mockserver" of the module tiger-proxy is based upon
mockserver (https://github.com/mock-server/mockserver), licensed under Apache License 2.0 (January 2004).

Files under the tiger-proxy/src/main/resources/css and webfonts folders were taken from the projects with listed
licenses:

* bulma css [MIT](http://opensource.org/licenses/MIT)
* bulma swatch css [MIT](http://opensource.org/licenses/MIT)
* Font Awesome by Dave Gandy - [Website](http://fontawesome.io) [License](https://fontawesome.com/license/free) (Icons:
  CC BY 4.0, Fonts: SIL OFL 1.1, Code: MIT License)
