/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.collect.Lists;
import com.jenkinsci.plugins.badge.action.AbstractBadgeAction;
import com.jenkinsci.plugins.badge.action.BadgeAction;
import com.jenkinsci.plugins.badge.action.BadgeSummaryAction;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class WorkflowTest {

    private static final String SIGNATURE_TEMPLATE =
            "method org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildRecorder$BadgeManager %s";

    private final LogRecorder logging = new LogRecorder();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private static void approveSignature(String signature) throws Exception {
        ScriptApproval.get().approveSignature(SIGNATURE_TEMPLATE.formatted(signature));
    }

    @Issue("JENKINS-26918")
    @Test
    void usingManager() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("manager.addWarningBadge 'stuff is broken'", true));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("stuff is broken", b.getAction(BadgeAction.class).getText());
    }

    @Test
    void usingManagerAddBadge2Args() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p-addBadge2");
        p.setDefinition(new CpsFlowDefinition("manager.addBadge('yellow.gif', 'stuff is broken')", true));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("stuff is broken", b.getAction(BadgeAction.class).getText());
    }

    @Test
    void usingManagerInfoBadge() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p-infoBadge");
        p.setDefinition(new CpsFlowDefinition("manager.addInfoBadge 'stuff is broken'", true));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("stuff is broken", b.getAction(BadgeAction.class).getText());
    }

    @Test
    void usingManagerErrorBadge() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p-errorBadge");
        p.setDefinition(new CpsFlowDefinition("manager.addErrorBadge 'stuff is broken'", true));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("stuff is broken", b.getAction(BadgeAction.class).getText());
    }

    @Issue("JENKINS-31038")
    @Test
    void usingManagerCreateSummaryAndRemoveSummary() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p-createSummary");
        p.setDefinition(new CpsFlowDefinition("""
                manager.createSummary('attribute.png').appendText('one', false);
                manager.createSummary('attribute.png').appendText('two', false);
                manager.removeSummary(0);""", true));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(
                List.of("two"), Lists.transform(b.getActions(BadgeSummaryAction.class), AbstractBadgeAction::getText));
    }

    @Issue("JENKINS-54128")
    @Test
    void logContains() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                echo '1st message'
                echo '2nd message'
                sleep 1
                echo(/found first message? ${manager.logContains(/1st message/)} second? ${manager.logContains(/2nd message/)} third? ${manager.logContains(/3rd message/)} /);""", true));
        logging.record(WorkflowRun.class, Level.WARNING).capture(100);
        j.assertLogContains(
                "found first message? true second? true third? false", j.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        assertEquals(Collections.emptyList(), logging.getRecords());
    }

    @Issue("JENKINS-43012")
    @Test
    void setBuildNumberRedirectsBadgeToTargetBuild() throws Exception {
        approveSignature("setBuildNumber int");
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p-setBuildNumber-target");
        p.setDefinition(new CpsFlowDefinition("echo 'first build'", true));
        WorkflowRun first = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        p.setDefinition(new CpsFlowDefinition("""
                manager.setBuildNumber(%d)
                manager.addBadge('yellow.gif', 'redirected')
                """.formatted(first.getNumber()), true));
        WorkflowRun second = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertEquals("redirected", first.getAction(BadgeAction.class).getText());
        assertNull(second.getAction(BadgeAction.class));
    }

    @Issue("JENKINS-43012")
    @Test
    void laterManagerReferenceKeepsTheRedirect() throws Exception {
        approveSignature("setBuildNumber int");
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p-setBuildNumber-later-reference");
        p.setDefinition(new CpsFlowDefinition("echo 'first build'", true));
        WorkflowRun first = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // Each bare `manager` reference below is resolved by WorkflowManager independently; the
        // redirect chosen by the first one must still apply to the ones further down.
        p.setDefinition(new CpsFlowDefinition("""
                manager.setBuildNumber(%d)
                manager.addShortText('short text')
                manager.addBadge('yellow.gif', 'still redirected')
                """.formatted(first.getNumber()), true));
        WorkflowRun second = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertEquals(2, first.getActions(BadgeAction.class).size());
        assertEquals("short text", first.getActions(BadgeAction.class).get(0).getText());
        assertEquals(
                "still redirected", first.getActions(BadgeAction.class).get(1).getText());
        assertNull(second.getAction(BadgeAction.class));
    }

    @Issue("JENKINS-43012")
    @Test
    void setBuildNumberBackToCurrentBuildRestoresDefault() throws Exception {
        approveSignature("setBuildNumber int");
        approveSignature("getBuild");
        ScriptApproval.get().approveSignature("method jenkins.model.HistoricalBuild getNumber");
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p-setBuildNumber-restore");
        p.setDefinition(new CpsFlowDefinition("echo 'first build'", true));
        WorkflowRun first = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        p.setDefinition(new CpsFlowDefinition("""
                def homeNumber = manager.build.number
                manager.setBuildNumber(%d)
                manager.setBuildNumber(homeNumber)
                manager.addBadge('yellow.gif', 'on the running build')
                """.formatted(first.getNumber()), true));
        WorkflowRun second = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertNull(first.getAction(BadgeAction.class));
        assertEquals("on the running build", second.getAction(BadgeAction.class).getText());
    }

    @Issue("JENKINS-43012")
    @Test
    void setBuildNumberWithInvalidNumberIsIgnored() throws Exception {
        approveSignature("setBuildNumber int");
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p-setBuildNumber-invalid");
        p.setDefinition(new CpsFlowDefinition("""
                def ok = manager.setBuildNumber(999)
                manager.addBadge('yellow.gif', "ok=${ok}")
                """, true));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertEquals("ok=false", b.getAction(BadgeAction.class).getText());
    }
}
