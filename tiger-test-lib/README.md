# Configure IDE

* Set main class in Cucumber run plugin template config net.serenitybdd.cucumber.cli.Main
* Make sure no actor glue code is added in intellij

## docker container creation fails

use

```
docekr system prune
```

# TODOs

TestenvMgr: add timeout for health check loop

Banner component

# TODO Next release

# NOGOs

JANSI lib for colored output https://github.com/fusesource/jansijansi
(but serenity does not deal with this lib gracefully, changing all output to error level :)