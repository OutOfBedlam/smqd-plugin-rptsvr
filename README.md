
## How to run in IntelliJ

- Main class = `com.thing2x.smqd.Main`

- VM options
```
-Dconfig.file=./src/test/conf/rptsvr.conf
-Dlogback.configurationFile=./src/test/conf/logback.xml
-Djava.net.preferIPv4Stack=true
-Djava.net.preferIPv6Addresses=false
```

- This report server is developed and test with TIBCO Jaspersoft Studio 6.8.0

#### Font extension

To use additional fonts, add dependencies in build.sbt

```
libraryDependencies += "com.thing2x" %% "jasperreports-font-nanum" % "0.2.0"
```