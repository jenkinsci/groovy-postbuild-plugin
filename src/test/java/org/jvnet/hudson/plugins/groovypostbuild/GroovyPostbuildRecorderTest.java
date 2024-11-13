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

import static org.junit.Assert.*;

import com.google.common.base.Function;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.scriptsecurity.scripts.ClasspathEntry;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.UnstableBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

public class GroovyPostbuildRecorderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String TEXT_ON_FAILED = "Groovy";

    private static final String SCRIPT_FOR_MATRIX = StringUtils.join(
            new String[] {
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
            },
            '\n');

    private static final String SCRIPT_FOR_MATRIX2 = SCRIPT_FOR_MATRIX
            .replace("jenkins-!-color-dark-indigo", "jenkins-!-error-color")
            .replace("jenkins-!-success-color", "jenkins-!-color-dark-blue");

    @Test
    public void testMatrixProjectWithParent() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(SCRIPT_FOR_MATRIX, true, Collections.<ClasspathEntry>emptyList()),
                        2,
                        true));

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
    public void testMatrixProjectWithoutParent() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(SCRIPT_FOR_MATRIX2, true, Collections.<ClasspathEntry>emptyList()),
                        2,
                        false));

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
    public void testBehaviorNotAffectWithSucceedingBuildSucceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior : behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();

            p.getPublishersList()
                    .add(new GroovyPostbuildRecorder(
                            new SecureGroovyScript(
                                    "manager.addShortText('testing', null, null, null, null);",
                                    true,
                                    Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorNotAffectWithUnstableBuildSuceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior : behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();

            p.getBuildersList().add(new UnstableBuilder());

            p.getPublishersList()
                    .add(new GroovyPostbuildRecorder(
                            new SecureGroovyScript(
                                    "manager.addShortText('testing');", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorNotAffectWithFailingBuildSuceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior : behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();

            p.getBuildersList().add(new FailureBuilder());

            p.getPublishersList()
                    .add(new GroovyPostbuildRecorder(
                            new SecureGroovyScript(
                                    "manager.addShortText('testing');", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorDoNothingWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorDoNothingWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new UnstableBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorDoNothingWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new FailureBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorMarkUnstableWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorMarkUnstableWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new UnstableBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorMarkUnstableWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new FailureBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorMarkFailedWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorMarkFailedWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new UnstableBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
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
    public void testBehaviorMarkFailedWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(new FailureBuilder());

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript("blahblahblah", true, Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(BadgeAction.class).getText());
    }

    @Test
    @LocalData
    public void testBadgeMigration() throws Exception {
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
    public void testAddShortText() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('some-badge-text');",
                                true, // sandbox
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertThat(j.createWebClient().getPage(p).getVisibleText(), Matchers.containsString("some-badge-text"));
    }

    @Test
    public void testAddShortTextHtmlIsEscaped() throws Exception {
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
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        assertNull(j.createWebClient().getPage(p).getElementById("should-be-escaped"));
    }

    @Test
    public void testAddHtmlBadge() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addHtmlBadge('<div id=\"added-as-badge\">foobar</div>');",
                                true, // sandbox
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertThat(j.createWebClient().getPage(p).getVisibleText(), Matchers.containsString("foobar"));
    }

    @Test
    public void testAddHtmlBadgeForUnsafeHtml() throws Exception {
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
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertNull(j.createWebClient().getPage(p).getElementById("should-be-untainted"));
    }

    @Test
    public void testRemoveBadge() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('test1');\n"
                                        + "manager.addShortText('test2');\n"
                                        + "manager.removeBadge(0);",
                                true, // sandbox
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(
                Arrays.asList("test2"),
                Lists.transform(b.getActions(BadgeAction.class), new Function<BadgeAction, String>() {
                    @Override
                    public String apply(BadgeAction badge) {
                        return badge.getText();
                    }
                }));
    }

    @Test
    public void testRemoveBadges() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('test1');\n"
                                        + "manager.addShortText('test2');\n"
                                        + "manager.removeBadges();",
                                true, // sandbox
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(Collections.emptyList(), b.getActions(BadgeAction.class));
    }

    @Test
    public void testRemoveBadgeForHtmlBadge() throws Exception {
        // removeBadge() also removes HtmlBadges
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addHtmlBadge('test1');\n"
                                        + "manager.addShortText('test2');\n"
                                        + "manager.removeBadge(0);",
                                true, // sandbox
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(
                Arrays.asList("test2"), Lists.transform(b.getActions(BadgeAction.class), AbstractBadgeAction::getText));
    }

    @Test
    public void testRemoveSummary() throws Exception {
        j.jenkins.setMarkupFormatter(RawHtmlMarkupFormatter.INSTANCE);

        String template = "method org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildRecorder$BadgeManager %s";
        ScriptApproval.get().approveSignature(String.format(template, "removeSummary int"));
        ScriptApproval.get().approveSignature(String.format(template, "createSummary java.lang.String"));
        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.createSummary('attribute.png').appendText('Test1', false, false, false, 'Black');\n"
                                        + "manager.createSummary('attribute.png').appendText('Test2', false, false, false, 'Black');\n"
                                        + "manager.removeSummary(0);",
                                true,
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(
                Arrays.asList("Test2"),
                Lists.transform(b.getActions(BadgeSummaryAction.class), AbstractBadgeAction::getText));
    }

    @Test
    public void testRemoveSummaries() throws Exception {
        String template = "method org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildRecorder$BadgeManager %s";
        ScriptApproval.get().approveSignature(String.format(template, "removeSummaries"));
        ScriptApproval.get().approveSignature(String.format(template, "createSummary java.lang.String"));
        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.createSummary('attribute.png').appendText('Test1', false, false, false, 'Black');\n"
                                        + "manager.removeSummaries();",
                                true,
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(Collections.emptyList(), b.getActions(BadgeSummaryAction.class));
    }

    @Test
    @Issue("JENKINS-54262")
    public void testRunWithNonAdministrator() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(
                                "manager.addShortText('test1');",
                                true, // sandbox
                                Collections.<ClasspathEntry>emptyList()),
                        2, // behavior
                        false // runForMatrixParent
                        ));

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(authStrategy);
        authStrategy.grant(Item.BUILD).onRoot().to("alice");
        authStrategy.grant(Computer.BUILD).onRoot().to("alice");

        Map<String, Authentication> authMap = new HashMap<>();
        authMap.put(p.getFullName(), User.getById("alice", true).impersonate());
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(authMap));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }
}
