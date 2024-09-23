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

import static org.junit.Assert.*;

import com.jenkinsci.plugins.badge.action.BadgeAction;
import java.util.Collections;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class WorkflowTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    @Issue("JENKINS-26918")
    @Test
    public void usingManager() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("manager.addWarningBadge 'stuff is broken'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("stuff is broken", b.getAction(BadgeAction.class).getText());
    }

    @Test
    public void usingManagerAddBadge2Args() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p-addBadge2");
        p.setDefinition(new CpsFlowDefinition("manager.addBadge('yellow.gif', 'stuff is broken')", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("stuff is broken", b.getAction(BadgeAction.class).getText());
    }

    @Test
    public void usingManagerInfoBadge() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p-infoBadge");
        p.setDefinition(new CpsFlowDefinition("manager.addInfoBadge 'stuff is broken'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("stuff is broken", b.getAction(BadgeAction.class).getText());
    }

    @Test
    public void usingManagerErrorBadge() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p-errorBadge");
        p.setDefinition(new CpsFlowDefinition("manager.addErrorBadge 'stuff is broken'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("stuff is broken", b.getAction(BadgeAction.class).getText());
    }

    @Issue("JENKINS-54128")
    @Test
    public void logContains() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "echo '1st message'\n" + "echo '2nd message'\n"
                        + "sleep 1\n"
                        + // to flush output (inspecting Run.log from the build itself is unreliable; use a
                        // TaskListenerDecorator instead)
                        "echo(/found first message? ${manager.logContains(/1st message/)} second? ${manager.logContains(/2nd message/)} third? ${manager.logContains(/3rd message/)} /); ",
                true));
        logging.record(WorkflowRun.class, Level.WARNING).capture(100);
        r.assertLogContains(
                "found first message? true second? true third? false", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        assertEquals(Collections.emptyList(), logging.getRecords());
    }
}
