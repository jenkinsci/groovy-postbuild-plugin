# Changelog

## Version 2.5 (Jun 24, 2019)

-   Requires Jenkins 2.121.1 or later.
-   New feature: `addHtmlBadge` is available [JENKINS-57917](https://issues.jenkins.io/browse/JENKINS-57917) -

    ``` syntaxhighlighter-pre
    manager.addHtmlBadge('<i>Italic text</i>');
    ```

-   groovy-postbuild no longer requires Overall/Administer permission: [JENKINS-54262](https://issues.jenkins.io/browse/JENKINS-54262) - It caused problems when used with [Authorize Project plugin](https://plugins.jenkins.io/authorize-project/)
-   Suppress "WARNING: Avoid calling getLogFile on ..." when used in pipeline jobs.

## Version 2.4.3 (Nov 18, 2018)

-   FIX: removeSummary / removeSummaries doesn't work (throw UnsupportedOperationException) ([JENKINS-54184](https://issues.jenkins.io/browse/JENKINS-54184))

## Version 2.4.2 (Aug 8, 2018)

-   FIX: removeBadge / removeBadges doesn't work (throw UnsupportedOperationException) ([JENKINS-52043](https://issues.jenkins.io/browse/JENKINS-52043))

## Version 2.4.1 (May 4, 2018)

-   Have [Build Monitor Plugin](https://plugins.jenkins.io/build-monitor-plugin/) not cause errors.
    ([JENKINS-50420](https://issues.jenkins.io/browse/JENKINS-50420))
    -   Introduced fake `GroovyPostbuildAction`.
    -   The feature of build-monitor to cooperate with groovy-postbuild doesn't work yet. It requires an upcoming release of build-monitor plugin to have it work again.

## Version 2.4 (Mar 25, 2018)

-   Requires Jenkins 2.60.3 or later
-   Extract badge and summary features to [Badge plugin](https://plugins.jenkins.io/badge)
    ([JENKINS-43992](https://issues.jenkins.io/browse/JENKINS-43992)).
    -   You can use badge and summary features in pipeline only with badge-plugin. See [Badge Plugin](https://plugins.jenkins.io/badge/) for more details.
-   Sanitize HTML in the badges.
    -   You no longer be able to use HTML expressions in badge contents.
-   **Don't upgrade to this version if you use [Build Monitor Plugin](https://plugins.jenkins.io/build-monitor-plugin/).**
    -   Groovy-postbuild-2.4 breaks build-moitor-1.12 or earlier. Sorry.
    -   Please postpone upgrading groovy-postbuild till fixing this issue:
        [JENKINS-50420](https://issues.jenkins.io/browse/JENKINS-50420)
    -   No new features in groovy-postbuild-2.4, and you can use
        groovy-postbuild-2.3.1.
        -   You can downgrade your groovy-postbuild in the Jenkins
            Plugin Management page.
        -   You can download earlier versions of groovy-postbuild from
            "Archives" link in
            <https://plugins.jenkins.io/groovy-postbuild>
-   Some classes are migrated to badge-plugin. Please change classes in
    your codes if you access those classes:

    | Old Class                                                             | New Class                                             |
    |-----------------------------------------------------------------------|-------------------------------------------------------|
    | org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildAction        | com.jenkinsci.plugins.badge.action.BadgeAction        |
    | org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildSummaryAction | com.jenkinsci.plugins.badge.action.BadgeSummaryAction |

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
