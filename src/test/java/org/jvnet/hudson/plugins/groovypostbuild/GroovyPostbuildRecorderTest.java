/*
 * The MIT License
 *
 * Copyright (c) 2014 IKEDA Yasuyuki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.plugins.groovypostbuild;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Lists;
import com.jenkinsci.plugins.badge.action.AbstractBadgeAction;
import com.jenkinsci.plugins.badge.action.BadgeAction;
import com.jenkinsci.plugins.badge.action.BadgeSummaryAction;
import hudson.markup.RawHtmlMarkupFormatter;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.User;
import hudson.util.VersionNumber;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.UnstableBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class GroovyPostbuildRecorderTest {

    private static final String TEXT_ON_FAILED = "Groovy";

    private static final String SCRIPT_FOR_MATRIX = String.join("\n", new String[] {
        "import hudson.matrix.MatrixBuild;",
        "import hudson.matrix.MatrixRun;",
        "if (manager.buildIsA(MatrixBuild.class)) {",
        "  // codes for matrix parents.",
        "  manager.addShortText('parent');",
        "} else if(manager.buildIsA(MatrixRun)) {",
        "  // codes for matrix children.",
        "  manager.addShortText(manager.getEnvVariable('axis1'),",
        "                       'jenkins-!-color-dark-indigo',",
        "                       'jenkins-!-color-light-purple',",
        "                       '3px dotted',",
        "                       'jenkins-!-success-color');",
        "} else {",
        "  // unexpected case.",
        "  manager.buildFailure();",
        "}"
    });

    private static final String SCRIPT_FOR_MATRIX2 = SCRIPT_FOR_MATRIX
            .replace("jenkins-!-color-dark-indigo", "jenkins-!-error-color")
            .replace("jenkins-!-success-color", "jenkins-!-color-dark-blue");

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testMatrixProjectWithParent() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(SCRIPT_FOR_MATRIX, true, Collections.emptyList()), 2, true));

        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);

        assertEquals("parent", b.getAction(BadgeAction.class).getText());
        assertEquals(
                "value1",
                b.getRun(new Combination(axisList, "value1"))
                        .getAction(BadgeAction.class)
                        .getText());
        assertEquals(
                "value2",
                b.getRun(new Combination(axisList, "value2"))
                        .getAction(BadgeAction.class)
                        .getText());
    }

    @Test
    void testMatrixProjectWithoutParent() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(SCRIPT_FOR_MATRIX2, true, Collections.emptyList()), 2, false));

        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);

        assertNull(b.getAction(BadgeAction.class));
        assertEquals(
                "value1",
                b.getRun(new Combination(axisList, "value1"))
                        .getAction(BadgeAction.class)
                        .getText());
        assertEquals(
                "value2",
                b.getRun(new Combination(axisList, "value2"))
                        .getAction(BadgeAction.class)
                        .getText());
    }

    /**
     * behavior = any
     * build succeeds
     * script succeeds
     * -> build succeeds
     * @throws Exception
     */
    @Test
    void testBehaviorNotAffectWithSucceedingBuildSucceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior : behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();

            p.getPublishersList()
                    .add(new GroovyPostbuildRecorder(
                            new SecureGroovyScript(
                                    "manager.addShortText('testing', null, null, null, null);",
                                    true,
                                    Collections.emptyList()),
                            behavior, // behavior
                            false // runForMatrixParent
                            ));

            FreeStyleBuild b = p.scheduleBuild2(0).get();
            j.assertBuildStatus(Result.SUCCESS, b);
            assertEquals("testing", b.getAction(BadgeAction.class).getText());
        }
    }

    /**
     * behavior = any
     * build unstable
     * script succeeds
     * -> build unstable
     * @throws Exception
     */
    @Test
    void testBehaviorNotAffectWithUnstableBuildSucceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior : behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();

            p.getBuildersList().add(new UnstableBuilder());

            p.getPublishersList()
                    .add(new GroovyPostbuildRecorder(
                            new SecureGroovyScript("manager.addShortText('testing');", true, Collections.emptyList()),
                            behavior, // behavior
                            false // runForMatrixParent
                            ));

            FreeStyleBuild b = p.scheduleBuild2(0).get();
            j.assertBuildStatus(Result.UNSTABLE, b);
            assertEquals("testing", b.getAction(BadgeAction.class).getText());
        }
    }

    /**
     * behavior = any
     * build failed
     * script succeeds
     * -> build failed
     * @throws Exception
     */
    @Test
    void testBehaviorNotAffectWithFailingBuildSucceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior : behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();

            p.getBuildersList().add(new FailureBuilder());

            p.getPublishersList()
                    .add(new GroovyPostbuildRecorder(
                            new SecureGroovyScript("manager.addShortText('testing');", true, Collections.emptyList()),
                            behavior, // behavior
                            false // runForMatrixParent
                            ));

            FreeStyleBuild b = p.scheduleBuild2(0).get();
            j.assertBuildStatus(Result.FAILURE, b);
            assertEquals("testing", b.getAction(BadgeAction.class).getText());
        }
    }

    /**
     * behavior = DoNothing(0)
     * build succeeds
     * script failed
     * -> build succeeds
     * @throws Exception
     */
    @Test
    void testBehaviorDoNothingWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        0, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    /**
     * behavior = DoNothing(0)
     * build unstable
     * script failed
     * -> build unstable
     * @throws Exception
     */
    @Test
    void testBehaviorDoNothingWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new UnstableBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        0, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    /**
     * behavior = DoNothing(0)
     * build failed
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    void testBehaviorDoNothingWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new FailureBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        0, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    /**
     * behavior = Mark build as unstable(1)
     * build succeeds
     * script failed
     * -> build unstable
     * @throws Exception
     */
    @Test
    void testBehaviorMarkUnstableWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        1, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    /**
     * behavior = Mark build as unstable(1)
     * build unstable
     * script failed
     * -> build unstable
     * @throws Exception
     */
    @Test
    void testBehaviorMarkUnstableWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new UnstableBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        1, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    /**
     * behavior = Mark build as unstable(1)
     * build failed
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    void testBehaviorMarkUnstableWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new FailureBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        1, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    /**
     * behavior = Mark build as failed (2)
     * build succeeds
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    void testBehaviorMarkFailedWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    /**
     * behavior = Mark build as failed (2)
     * build unstable
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    void testBehaviorMarkFailedWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new UnstableBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    /**
     * behavior = Mark build as failed (2)
     * build failed
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    void testBehaviorMarkFailedWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new FailureBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    @Test
    @LocalData
    void testBadgeMigration() throws Exception {
        j.jenkins.setMarkupFormatter(RawHtmlMarkupFormatter.INSTANCE);

        FreeStyleProject p = j.jenkins.getItemByFullName("groovy-postbuild-2.3.1", FreeStyleProject.class);
        assertNotNull(p);

        // Test that the build configuration migrates successfully
        {
            FreeStyleBuild b = p.getLastBuild();
            assertNotNull(b);

            BadgeAction badgeAction = b.getAction(BadgeAction.class);
            assertNotNull(badgeAction);
            assertEquals("/plugin/groovy-postbuild/images/success.gif", badgeAction.getIcon());
            assertEquals("shortText", badgeAction.getText());
            assertEquals("border: 1px solid #C0C000;background: #FFFF00;color: #000000;", badgeAction.getStyle());
            assertEquals("https://jenkins.io/", badgeAction.getLink());

            BadgeSummaryAction badgeSummaryAction = b.getAction(BadgeSummaryAction.class);
            assertNotNull(badgeSummaryAction);

            VersionNumber badgePluginVersion =
                    j.getPluginManager().getPlugin("badge").getVersionNumber();

            if (badgePluginVersion.isNewerThanOrEqualTo(new VersionNumber("2.5"))) {
                assertEquals("symbol-information-circle", badgeSummaryAction.getIcon());
            } else {
                assertEquals("/plugin/badge/images/info.gif", badgeSummaryAction.getIcon());
            }
            assertEquals("<b>summaryText</b>", badgeSummaryAction.getText());
        }

        // Test that the job configuration migrates successfully
        {
            FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
            assertNotNull(b);

            VersionNumber badgePluginVersion =
                    j.getPluginManager().getPlugin("badge").getVersionNumber();

            BadgeAction badgeAction = b.getAction(BadgeAction.class);
            assertNotNull(badgeAction);

            if (badgePluginVersion.isNewerThanOrEqualTo(new VersionNumber("2.5"))) {
                assertEquals("symbol-status-blue", badgeAction.getIcon());
            } else {
                assertEquals("/plugin/badge/images/success.gif", badgeAction.getIcon());
            }
            assertEquals("shortText", badgeAction.getText());
            assertEquals("https://jenkins.io/", badgeAction.getLink());

            BadgeSummaryAction badgeSummaryAction = b.getAction(BadgeSummaryAction.class);
            assertNotNull(badgeSummaryAction);

            if (badgePluginVersion.isNewerThanOrEqualTo(new VersionNumber("2.5"))) {
                assertEquals("symbol-information-circle", badgeSummaryAction.getIcon());
            } else {
                assertEquals("/plugin/badge/images/info.gif", badgeSummaryAction.getIcon());
            }
            assertEquals("<b>summaryText</b>", badgeSummaryAction.getText());
        }
    }

    @Test
    void testAddShortText() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('some-badge-text');",
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertThat(j.createWebClient().getPage(p).getVisibleText(), Matchers.containsString("some-badge-text"));
    }

    @Test
    void testAddShortTextHtmlIsEscaped() throws Exception {
        // This test is to make it clear that
        // addShortText() doesn't allow HTMLs
        // even though that implementation is
        // in badge-plugin.
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('<div id=\"should-be-escaped\">foobar</div>');",
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        assertNull(j.createWebClient().getPage(p).getElementById("should-be-escaped"));
    }

    @Test
    void testAddShortTextWithColorVariablePrefixTranslatesToCssCustomProperty() throws Exception {
        // 'jenkins-!-color-<name>' must translate to the CSS custom property
        // var(--<name>), not var(---<name>) with a stray extra hyphen.
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('testing', 'jenkins-!-color-dark-indigo', null, null, null);",
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        String style = b.getAction(BadgeAction.class).getStyle();
        assertThat(style, Matchers.containsString("var(--dark-indigo)"));
        assertThat(style, Matchers.not(Matchers.containsString("var(---dark-indigo)")));
    }

    @Test
    void testAddShortTextWithNullBorderColorEmitsWellFormedBorder() throws Exception {
        // A null border colour must not leave a stray space before the
        // semicolon (e.g. "border: 1px solid ;"), which browsers drop as
        // malformed and which causes the border to disappear entirely.
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('testing', null, null, '1px', null);",
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        String style = b.getAction(BadgeAction.class).getStyle();
        assertThat(style, Matchers.containsString("border: 1px solid;"));
        assertThat(style, Matchers.not(Matchers.containsString("solid ;")));
    }

    @Test
    void testAddHtmlBadge() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addHtmlBadge('<div id=\"added-as-badge\">foobar</div>');",
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertThat(j.createWebClient().getPage(p).getVisibleText(), Matchers.containsString("foobar"));
    }

    @Test
    void testAddHtmlBadgeForUnsafeHtml() throws Exception {
        // This test is to make it sure that
        // addHtmlBadge() doesn't allow danger HTMLs
        // even though that implementation is
        // in badge-plugin.
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addHtmlBadge('<script id=\"should-be-untainted\">alert(\"exploit!\");</script>');",
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertNull(j.createWebClient().getPage(p).getElementById("should-be-untainted"));
    }

    @Test
    void testRemoveBadge() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                """
                                manager.addShortText('test1');
                                manager.addShortText('test2');
                                manager.removeBadge(0);
                                """,
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(List.of("test2"), Lists.transform(b.getActions(BadgeAction.class), AbstractBadgeAction::getText));
    }

    @Test
    void testRemoveBadges() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                """
                                manager.addShortText('test1');
                                manager.addShortText('test2');
                                manager.removeBadges();
                                """,
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(Collections.emptyList(), b.getActions(BadgeAction.class));
    }

    @Test
    void testRemoveBadgeForHtmlBadge() throws Exception {
        // removeBadge() also removes HtmlBadges
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                """
                                manager.addHtmlBadge('test1');
                                manager.addShortText('test2');
                                manager.removeBadge(0);
                                """,
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(List.of("test2"), Lists.transform(b.getActions(BadgeAction.class), AbstractBadgeAction::getText));
    }

    @Test
    void testRemoveBadgesOnlyLeavesSummaries() throws Exception {
        // removeBadgesOnly() is constrained to BadgeAction and must not touch BadgeSummaryAction
        String template = "method org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildRecorder$BadgeManager %s";
        ScriptApproval.get().approveSignature(template.formatted("createSummary java.lang.String"));
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                """
                                manager.addShortText('test1');
                                manager.addShortText('test2');
                                manager.createSummary('attribute.png');
                                manager.removeBadgesOnly();
                                """,
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(Collections.emptyList(), b.getActions(BadgeAction.class));
        assertEquals(1, b.getActions(BadgeSummaryAction.class).size());
    }

    @Test
    void testRemoveBadgesAlsoRemovesSummaries() throws Exception {
        // Pins today's (surprising) behavior: removeBadges() filters on the shared
        // AbstractBadgeAction superclass, so it removes summaries too, not just badges.
        // If this is ever fixed to match its name, this test must be updated deliberately.
        String template = "method org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildRecorder$BadgeManager %s";
        ScriptApproval.get().approveSignature(template.formatted("createSummary java.lang.String"));
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                """
                                manager.addShortText('test1');
                                manager.addShortText('test2');
                                manager.createSummary('attribute.png');
                                manager.removeBadges();
                                """,
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(Collections.emptyList(), b.getActions(BadgeAction.class));
        assertEquals(Collections.emptyList(), b.getActions(BadgeSummaryAction.class));
    }

    @Test
    void testRemoveSummary() throws Exception {
        j.jenkins.setMarkupFormatter(RawHtmlMarkupFormatter.INSTANCE);

        String template = "method org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildRecorder$BadgeManager %s";
        ScriptApproval.get().approveSignature(template.formatted("removeSummary int"));
        ScriptApproval.get().approveSignature(template.formatted("createSummary java.lang.String"));
        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("""
                                manager.createSummary('attribute.png').appendText('Test1', false, false, false, 'Black');
                                manager.createSummary('attribute.png').appendText('Test2', false, false, false, 'Black');
                                manager.removeSummary(0);
                                """, true, Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(
                List.of("Test2"),
                Lists.transform(b.getActions(BadgeSummaryAction.class), AbstractBadgeAction::getText));
    }

    @Test
    void testRemoveSummaries() throws Exception {
        String template = "method org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildRecorder$BadgeManager %s";
        ScriptApproval.get().approveSignature(template.formatted("removeSummaries"));
        ScriptApproval.get().approveSignature(template.formatted("createSummary java.lang.String"));
        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("""
                                manager.createSummary('attribute.png').appendText('Test1', false, false, false, 'Black');
                                manager.removeSummaries();
                                """, true, Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(Collections.emptyList(), b.getActions(BadgeSummaryAction.class));
    }

    @Test
    @Issue("JENKINS-54262")
    void testRunWithNonAdministrator() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('test1');",
                                true, // sandbox
                                Collections.emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(authStrategy);
        authStrategy.grant(Item.BUILD).onRoot().to("alice");
        authStrategy.grant(Computer.BUILD).onRoot().to("alice");

        MockQueueItemAuthenticator authenticator = new MockQueueItemAuthenticator();
        authenticator.authenticate(p.getFullName(), User.getById("alice", true).impersonate2());
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(authenticator);

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    /**
     * Extracts and XML-unescapes the text content of a single-line {@code <tag>...</tag>} element,
     * bypassing getText()/getLink() entirely - those getters apply a markup formatter or a pattern
     * filter respectively, neither of which is relevant to what was actually persisted.
     */
    private static String extractElement(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        if (start < 0) {
            return null;
        }
        start += open.length();
        int end = xml.indexOf(close, start);
        return org.apache.commons.text.StringEscapeUtils.unescapeXml(xml.substring(start, end));
    }

    /**
     * {@link AppendTextBadgeSummaryAction} must never be the class actually written to build.xml:
     * an installation without this exact plugin-local class (a downgrade, or simply the day this
     * deprecated shim is removed) would otherwise get CannotResolveClassException on every build
     * that used it, and Jenkins would silently drop the summary. writeReplace() is what prevents
     * that; this test proves it is honored by Jenkins' actual XStream setup (Run.XSTREAM2,
     * RobustReflectionConverter included) rather than assuming XStream's general documentation
     * applies unmodified, and proves the persisted fields are the raw values, not the transformed
     * view getText()/getLink() return.
     */
    @Test
    void testShimPersistsAsPlainBadgeSummaryAction() throws Exception {
        AppendTextBadgeSummaryAction action =
                new AppendTextBadgeSummaryAction(null, "exp.png", null, null, null, null, null);
        action.appendText("ExpText", false, false, false, "Black");
        action.setLink("example.com/not-a-recognized-scheme"); // getLink() would silently return null for this

        String expectedRawText = "<font color=\"Black\">ExpText</font>";
        String expectedRawLink = "example.com/not-a-recognized-scheme";

        String xml = hudson.model.Run.XSTREAM2.toXML(action);

        // (1) the class actually written to build.xml must not be this plugin's own class.
        Object roundTripped = hudson.model.Run.XSTREAM2.fromXML(xml);
        assertEquals(
                "com.jenkinsci.plugins.badge.action.BadgeSummaryAction",
                roundTripped.getClass().getName());

        // (2) the persisted <text>/<link> must be the RAW values, not getText()/getLink()'s
        // transformed view (markup-formatter translation, backwards-compat icon rewriting, and
        // getLink()'s silent drop of anything that fails its scheme check, respectively).
        assertEquals(
                expectedRawText,
                extractElement(xml, "text"),
                "writeReplace must persist the RAW text, not getText()'s translated view");
        assertEquals(
                expectedRawLink,
                extractElement(xml, "link"),
                "writeReplace must persist the RAW link, not getLink()'s filtered view");

        // (3) round-tripping again from the already-persisted XML must reproduce the same raw
        // values (i.e. the plain BadgeSummaryAction we replaced ourselves with round-trips using
        // ordinary field reflection, with no further transformation).
        String xml2 = hudson.model.Run.XSTREAM2.toXML(roundTripped);
        assertEquals(expectedRawText, extractElement(xml2, "text"));
        assertEquals(expectedRawLink, extractElement(xml2, "link"));
    }

    /**
     * Simulates the shim class having been removed from a later plugin release (or a downgrade to
     * a controller that never had it): the outer XML tag is the only place its FQCN appears, so
     * replacing it with a name that resolves to nothing on this classpath reproduces exactly that
     * situation. Deserialization must still succeed, purely via the resolves-to attribute
     * writeReplace() causes XStream to write - proving old build.xml files stay readable forever,
     * with no future maintainer action required on the day this shim is deleted.
     */
    /**
     * testRemoveSummary/testRemoveSummaries run under RawHtmlMarkupFormatter, which sanitizes
     * {@code <font color="...">} away entirely (it is backed by OWASP AntiSamy, and font/color
     * are not in its default allowed-tags policy) before any assertion could see it - so neither
     * of those tests exercises the 5-arg overload's tag construction at all. This test uses a
     * trivial identity MarkupFormatter (write the input back unchanged) so the assertions see the
     * literal markup appendText(...) actually produced: both wrappers plus color, in the exact
     * (not properly re-nested) order badge 2.8 produced them, with the color escaped always and
     * the text escaped only when requested.
     */
    @Test
    void testAppendTextFiveArgOverloadProducesExactMarkup() throws Exception {
        j.jenkins.setMarkupFormatter(new hudson.markup.MarkupFormatter() {
            @Override
            public void translate(String markup, java.io.Writer output) throws java.io.IOException {
                output.write(markup);
            }
        });

        AppendTextBadgeSummaryAction escaped =
                new AppendTextBadgeSummaryAction(null, "escaped.png", null, null, null, null, null);
        escaped.appendText("<script>", true, true, true, "a&b");
        assertEquals("<b><i><font color=\"a&amp;b\">&lt;script&gt;</b></i></font>", escaped.getText());

        AppendTextBadgeSummaryAction unescaped =
                new AppendTextBadgeSummaryAction(null, "unescaped.png", null, null, null, null, null);
        unescaped.appendText("<u>raw</u>", false, false, false, null);
        assertEquals("<u>raw</u>", unescaped.getText());
    }

    @Test
    void testShimSurvivesRemovalFromClasspath() throws Exception {
        AppendTextBadgeSummaryAction action =
                new AppendTextBadgeSummaryAction(null, "exp2.png", null, null, null, null, null);
        action.appendText("ExpText2", false, false, false, "Black");
        String xml = hudson.model.Run.XSTREAM2.toXML(action);

        String simulated = xml.replace(
                "org.jvnet.hudson.plugins.groovypostbuild.AppendTextBadgeSummaryAction",
                "org.jvnet.hudson.plugins.groovypostbuild.ThisClassDoesNotExistAnymore");
        Object result = hudson.model.Run.XSTREAM2.fromXML(simulated);
        assertEquals(
                "com.jenkinsci.plugins.badge.action.BadgeSummaryAction",
                result.getClass().getName());
        assertEquals("<font color=\"Black\">ExpText2</font>", extractElement(simulated, "text"));
    }
}
