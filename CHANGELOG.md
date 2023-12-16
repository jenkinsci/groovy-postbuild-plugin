# Changelog

## Version 2.3.1 (Feb 7, 2016)

-   Dependency to pipeline (aka. workflow) is optional
    ([JENKINS-32589](https://issues.jenkins.io/browse/JENKINS-32589)).

## Version 2.3 (Dec 27, 2015)

-   Require Jenkins 1.609 or later
-   Supports workflow
    ([JENKINS-26918](https://issues.jenkins.io/browse/JENKINS-26918))
    -   You can use `manager` in workflow scripts.

## Version 2.2.2 (Oct 17, 2015)

-   Added MIT License
    ([JENKINS-21270](https://issues.jenkins.io/browse/JENKINS-21270))

## Version 2.2.1 (Aug 18, 2015)

-   Improved the behavior with [Template Project
    Plugin](https://wiki.jenkins.io/display/JENKINS/Template+Project+Plugin)
    ([JENKINS-21276](https://issues.jenkins.io/browse/JENKINS-21276))
    -   Prior versions disturbed the execution of [Template Project
        Plugin](https://wiki.jenkins.io/display/JENKINS/Template+Project+Plugin)
        when the build was failed (even if the groovy script succeeded).

## Version 2.2 (Dec 19, 2014)

-   added getResult() as a whitelisted method
    ([JENKINS-25738](https://issues.jenkins.io/browse/JENKINS-25738))

## Version 2.1 (Oct 25, 2014)

-   You can access other plugins in groovy scripts
    ([JENKINS-14154](https://issues.jenkins.io/browse/JENKINS-14154))

## Version 2.0 (Sep 21, 2014)

-   Require Jenkins 1.509.4 or later
-   Introduced [Script Security
    Plugin](https://wiki.jenkins.io/display/JENKINS/Script+Security+Plugin).
    ([JENKINS-15212](https://issues.jenkins.io/browse/JENKINS-15212))
    -   You need reconfigure your projects or approve scripts. Have a
        look at [\#Migration from
        1.X](https://wiki.jenkins.io/display/JENKINS/Groovy+Postbuild+Plugin#GroovyPostbuildPlugin-Migrationfrom1.X)
-   Added `manager.buildIsA(klcass)`.
    ([JENKINS-24694](https://issues.jenkins.io/browse/JENKINS-24694))

## Version 1.10 (July 26, 2014)

-   added `envVars` and `getEnvVariable(key)`

## Version 1.9 (April 29, 2014)

-   [JENKINS-21924](https://issues.jenkins.io/browse/JENKINS-21924)
    Support run for matrix parent
-   change log level for each search from info to fine

## Version 1.8 (August 22, 2012)

-   Require Jenkins version 1.466 or later
-   [JENKINS-13024](https://issues.jenkins.io/browse/JENKINS-13024)
    Error in log indicating a missing descriptor
-   added clickable badge

## Version 1.7 (May 2, 2012)

-   [JENKINS-13024](https://issues.jenkins.io/browse/JENKINS-13024)
    Error in log indicating a missing descriptor

## Version 1.6

-   [JENKINS-9383](https://issues.jenkins.io/browse/JENKINS-9383)
    security - restrict access to internal objects

Can use now additional classpath for groovy postbuild scripts to have
them in a central location. Scriptler Plugin?

## Version 1.5 (November 5, 2011)

-   Remove a html tag from the config page which causes an error on IE 7
    ([JENKINS-10079](https://issues.jenkins.io/browse/JENKINS-10079))
