# Configure IDE

* Set main class in Cucumber run plugin template config net.serenitybdd.cucumber.cli.Main
* Make sure no actor glue code is added in intellij

## docker container creation fails

use

```
docekr system prune
```

# TODOs

TestenvMgr: wait strategy seems to be not implemented neither for idp nor for erezept

TestenvMgr: how to tell erezept fd where to download tsl

Polarion box: check if internal ID exists before adding a new test case

Feature Parser: support at least also german key words

Banner component

# TODO Next release

# NOGOs

JANSI lib for colored output https://github.com/fusesource/jansijansi
(but serenity does not deal with this lib gracefully, changing all output to error level :)