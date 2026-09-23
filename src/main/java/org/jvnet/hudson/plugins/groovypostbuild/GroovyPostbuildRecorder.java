/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Serban Iordache
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

import com.jenkinsci.plugins.badge.action.AbstractBadgeAction;
import com.jenkinsci.plugins.badge.action.BadgeAction;
import com.jenkinsci.plugins.badge.action.BadgeSummaryAction;
import groovy.lang.Binding;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Functions;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import io.jenkins.plugins.ionicons.Ionicons;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ClasspathEntry;
import org.kohsuke.stapler.DataBoundConstructor;

/** This class associates {@link BadgeAction}s to a build. */
@SuppressWarnings("unchecked")
public class GroovyPostbuildRecorder extends Recorder implements MatrixAggregatable {
    private static final Logger LOGGER = Logger.getLogger(GroovyPostbuildRecorder.class.getName());

    @Deprecated
    private String groovyScript;

    private SecureGroovyScript script;
    private final int behavior;

    @Deprecated
    private List<GroovyScriptPath> classpath;

    private final boolean runForMatrixParent;

    public static class BadgeManager {
        private Run<?, ?> build;
        private final Run<?, ?> homeBuild;
        private final TaskListener listener;
        private final Result scriptFailureResult;
        private final Set<Run<?, ?>> builds = new HashSet<Run<?, ?>>();
        private EnvVars envVars;

        public BadgeManager(Run<?, ?> build, TaskListener listener, Result scriptFailureResult) {
            this(build, null, listener, scriptFailureResult);
        }

        /**
         * @param build the build {@code this} currently acts on, i.e. the target of a prior
         *     {@link #setBuildNumber(int)} redirect, or {@code homeBuild} if there was none
         * @param homeBuild the build the Pipeline step that obtained {@code this} is actually
         *     running on, where a {@link #setBuildNumber(int)} redirect is recorded so a later,
         *     separately-obtained {@code manager} in the same run picks it up; {@code null} for
         *     Freestyle, where a single {@code BadgeManager} instance is reused for the whole
         *     script and no such hand-off is needed
         */
        BadgeManager(Run<?, ?> build, Run<?, ?> homeBuild, TaskListener listener, Result scriptFailureResult) {
            setBuild(build);
            this.homeBuild = homeBuild;
            try {
                this.envVars = build.getEnvironment(listener);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace(listener.getLogger());
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
            }
            this.listener = listener;
            this.scriptFailureResult = scriptFailureResult;
        }

        // TBD: @Whitelisted
        public EnvVars getEnvVars() {
            return this.envVars;
        }

        @Whitelisted
        public void println(String string) {
            this.listener.getLogger().println(string);
        }

        @Whitelisted
        public String getEnvVariable(String key) throws IOException, InterruptedException {
            return this.envVars.get(key);
        }

        /**
         * @deprecated  use ${@link #getJenkins()}
         * @return the jenkins insance
         */
        @Deprecated
        // TBD: @Whitelisted
        public Hudson getHudson() {
            return (Hudson) getJenkins();
        }

        // TBD: @Whitelisted
        public Jenkins getJenkins() {
            return Jenkins.getInstance();
        }
        // TBD: @Whitelisted
        public Run<?, ?> getBuild() {
            return build;
        }

        public void setBuild(Run<?, ?> build) {
            if (build != null) {
                this.build = build;
                builds.add(build);
            }
        }

        public boolean setBuildNumber(int buildNumber) {
            Run<?, ?> newBuild = build.getParent().getBuildByNumber(buildNumber);
            if (newBuild == null) {
                return false;
            }
            setBuild(newBuild);
            if (homeBuild != null) {
                // Record the redirect on the run the step is actually executing on, so that the
                // next time the Pipeline script references `manager` - which WorkflowManager
                // resolves to a brand new BadgeManager - it is built against this same target.
                if (newBuild.getNumber() == homeBuild.getNumber()) {
                    PipelineManagerBuildNumberAction existing =
                            homeBuild.getAction(PipelineManagerBuildNumberAction.class);
                    if (existing != null) {
                        homeBuild.removeAction(existing);
                    }
                } else {
                    homeBuild.replaceAction(new PipelineManagerBuildNumberAction(newBuild.getNumber()));
                }
            }
            return true;
        }
        // TBD: @Whitelisted
        public TaskListener getListener() {
            return listener;
        }

        @Whitelisted
        public void addShortText(String text) {
            build.addAction(new BadgeAction(null, null, text, null, null, null, null));
        }

        @Whitelisted
        public void addShortText(String text, String color, String background, String border, String borderColor) {
            // translate old styling to new field
            String style = "border: " + (border != null ? border : "") + " solid"
                    + (borderColor != null ? " " + borderColor : "") + ";";
            if (background != null) {
                style += "background: " + background + ";";
            }
            if (color != null) {
                if (color.startsWith("jenkins-!-color-")) {
                    style += "color: var(--" + color.replaceFirst("jenkins-!-color-", "") + ");";
                } else if (color.startsWith("jenkins-!-")) {
                    style += "color: var(--" + color.replaceFirst("jenkins-!-", "") + ");";
                } else {
                    style += "color: " + color + ";";
                }
            }

            build.addAction(new BadgeAction(null, null, text, null, style, null, null));
        }

        @Whitelisted
        public void addBadge(String icon, String text) {
            build.addAction(new BadgeAction(null, icon, text, null, null, null, null));
        }

        @Whitelisted
        public void addBadge(String icon, String text, String link) {
            build.addAction(new BadgeAction(null, icon, text, null, null, link, null));
        }

        @Whitelisted
        public void addInfoBadge(String text) {
            build.addAction(new BadgeAction(
                    null,
                    Ionicons.getIconClassName("information-circle"),
                    text,
                    null,
                    "color: var(--blue)",
                    null,
                    null));
        }

        @Whitelisted
        public void addWarningBadge(String text) {
            build.addAction(new BadgeAction(
                    null, Ionicons.getIconClassName("warning"), text, null, "color: var(--warning-color)", null, null));
        }

        @Whitelisted
        public void addErrorBadge(String text) {
            build.addAction(new BadgeAction(
                    null,
                    Ionicons.getIconClassName("remove-circle"),
                    text,
                    null,
                    "color: var(--error-color)",
                    null,
                    null));
        }

        @Whitelisted
        public void addHtmlBadge(String html) {
            build.addAction(new BadgeAction(null, null, html, null, null, null, null));
        }

        @Whitelisted
        public String getResult() {
            Result r = build.getResult();
            return (r != null) ? r.toString() : null;
        }

        /** Removes every {@link AbstractBadgeAction} object from the {@link #build},
         *  which includes both actual {@link BadgeAction badges} seen in the build
         *  list column, and the {@link BadgeSummaryAction summaries} seen on the
         *  build page.<br/>
         *
         *  If you want only one of those Action types removed, use the variants
         *  constrained to it instead: {@link #removeBadgesOnly()} for badges only,
         *  or {@link #removeSummaries()} for summaries only.<br/>
         *
         *  Removal in this context means that the {@link Action} would no longer be
         *  linked to the specified build. An object in the pipeline (if someone gets
         *  the reference, e.g. via new Badge Plugin API) may still exist until it is
         *  no longer used and gets garbage-collected. Maybe someone can re-add such
         *  an Action object to same or different build before it evaporates, though.<br/>
         *
         *  @see #removeBadgeAction(int)
         *  @see #removeBadges()
         *  @see #removeBadge(int)
         *  @see #removeBadgesOnly()
         *  @see #removeBadgeOnly(int)
         *  @see #removeSummaries()
         *  @see #removeSummary(int)
         */
        @Whitelisted
        public void removeBadgeActions() {
            List<AbstractBadgeAction> badgeActions = build.getActions(AbstractBadgeAction.class);
            for (AbstractBadgeAction a : badgeActions) {
                build.removeAction(a);
            }
        }

        /** {@link #removeBadges()} is {@link #removeBadgeActions()} under a historic
         *  misnomer name: despite the name, it removes both badges and summaries. This
         *  method is kept for backward compatibility and simply delegates to
         *  {@link #removeBadgeActions()}.<br/>
         *
         *  <b>Note:</b> at a future major version, {@code removeBadges()} is scheduled
         *  to be narrowed to mean what {@link #removeBadgesOnly()} means today, i.e.
         *  removing {@link BadgeAction badges} only, not {@link BadgeSummaryAction
         *  summaries}. Callers who want today's behavior (removing every
         *  {@link AbstractBadgeAction}, badges and summaries alike) should move to
         *  {@link #removeBadgeActions()} instead.<br/>
         *
         *  @see #removeBadgeActions()
         *  @see #removeBadge(int)
         *  @see #removeBadgesOnly()
         *  @see #removeBadgeOnly(int)
         *  @see #removeSummaries()
         *  @see #removeSummary(int)
         */
        @Whitelisted
        public void removeBadges() {
            removeBadgeActions();
        }

        /** Removes the {@link AbstractBadgeAction} at the given {@code index} from
         *  the {@link #build}. The index is into the combined list of every
         *  {@link AbstractBadgeAction}, i.e. both actual {@link BadgeAction badges}
         *  seen in the build list column, and the {@link BadgeSummaryAction summaries}
         *  seen on the build page. See {@link #removeBadgeActions()} for what that
         *  list contains and what removal means.<br/>
         *
         *  If {@code index} is out of range, an error is written to the
         *  {@link #listener} and nothing is removed.<br/>
         *
         *  @see #removeBadgeActions()
         *  @see #removeBadges()
         *  @see #removeBadge(int)
         *  @see #removeBadgesOnly()
         *  @see #removeBadgeOnly(int)
         *  @see #removeSummaries()
         *  @see #removeSummary(int)
         */
        @Whitelisted
        public void removeBadgeAction(int index) {
            List<AbstractBadgeAction> badgeActions = build.getActions(AbstractBadgeAction.class);
            if (index < 0 || index >= badgeActions.size()) {
                listener.error("Invalid badge index: " + index + ". Allowed values: 0 .. " + (badgeActions.size() - 1));
            } else {
                AbstractBadgeAction action = badgeActions.get(index);
                build.removeAction(action);
            }
        }

        /** {@link #removeBadge(int)} is {@link #removeBadgeAction(int)} under a
         *  historic misnomer name: despite the name, it indexes into badges and
         *  summaries together. This method is kept for backward compatibility and
         *  simply delegates to {@link #removeBadgeAction(int)}.<br/>
         *
         *  <b>Note:</b> at a future major version, {@code removeBadge(int)} is
         *  scheduled to be narrowed to mean what {@link #removeBadgeOnly(int)} means
         *  today, i.e. indexing into {@link BadgeAction badges} only, not
         *  {@link BadgeSummaryAction summaries}. Callers who want today's behavior
         *  (indexing into every {@link AbstractBadgeAction}, badges and summaries
         *  alike) should move to {@link #removeBadgeAction(int)} instead.<br/>
         *
         *  @see #removeBadgeAction(int)
         *  @see #removeBadges()
         *  @see #removeBadgesOnly()
         *  @see #removeBadgeOnly(int)
         *  @see #removeSummaries()
         *  @see #removeSummary(int)
         */
        @Whitelisted
        public void removeBadge(int index) {
            removeBadgeAction(index);
        }

        /** For details, see {@link #removeBadgeActions()}.<br/>
         *
         *  @see #removeBadgeActions()
         *  @see #removeBadgeAction(int)
         *  @see #removeBadges()
         *  @see #removeBadge(int)
         *  @see #removeBadgeOnly(int)
         *  @see #removeSummaries()
         *  @see #removeSummary(int)
         */
        @Whitelisted
        public void removeBadgesOnly() {
            List<BadgeAction> badgeActions = build.getActions(BadgeAction.class);
            for (BadgeAction a : badgeActions) {
                build.removeAction(a);
            }
        }

        /** For details, see {@link #removeBadgeActions()}.<br/>
         *
         *  @see #removeBadgeActions()
         *  @see #removeBadgeAction(int)
         *  @see #removeBadges()
         *  @see #removeBadge(int)
         *  @see #removeBadgesOnly()
         *  @see #removeSummaries()
         *  @see #removeSummary(int)
         */
        @Whitelisted
        public void removeBadgeOnly(int index) {
            List<BadgeAction> badgeActions = build.getActions(BadgeAction.class);
            if (index < 0 || index >= badgeActions.size()) {
                listener.error("Invalid badge index: " + index + ". Allowed values: 0 .. " + (badgeActions.size() - 1));
            } else {
                BadgeAction action = badgeActions.get(index);
                build.removeAction(action);
            }
        }

        // NOTE: declared return type is BadgeSummaryAction, not AppendTextBadgeSummaryAction,
        // even though the object returned is always the latter. BadgeManager is public API
        // (WorkflowManager.getValue hands it to Pipeline), and narrowing a public method's return
        // type changes its descriptor with no bridge method: a plugin compiled against the old
        // signature would get NoSuchMethodError. Groovy dispatches appendText(...) dynamically
        // against the actual runtime object, so nothing about calling it from a script depends on
        // the declared type here.
        public BadgeSummaryAction createSummary(String icon) {
            AppendTextBadgeSummaryAction action =
                    new AppendTextBadgeSummaryAction(null, icon, null, null, null, null, null);
            build.addAction(action);
            return action;
        }

        /** For details, see {@link #removeBadgeActions()}.<br/>
         *
         *  @see #removeBadgeActions()
         *  @see #removeBadgeAction(int)
         *  @see #removeBadges()
         *  @see #removeBadge(int)
         *  @see #removeBadgesOnly()
         *  @see #removeBadgeOnly(int)
         *  @see #removeSummary(int)
         */
        public void removeSummaries() {
            List<BadgeSummaryAction> summaryActions = build.getActions(BadgeSummaryAction.class);
            for (BadgeSummaryAction a : summaryActions) {
                build.removeAction(a);
            }
        }

        /** For details, see {@link #removeBadgeActions()}.<br/>
         *
         *  @see #removeBadgeActions()
         *  @see #removeBadgeAction(int)
         *  @see #removeBadges()
         *  @see #removeBadge(int)
         *  @see #removeBadgesOnly()
         *  @see #removeBadgeOnly(int)
         *  @see #removeSummaries()
         */
        public void removeSummary(int index) {
            List<BadgeSummaryAction> summaryActions = build.getActions(BadgeSummaryAction.class);
            if (index < 0 || index >= summaryActions.size()) {
                listener.error(
                        "Invalid summary index: " + index + ". Allowed values: 0 .. " + (summaryActions.size() - 1));
            } else {
                BadgeSummaryAction action = summaryActions.get(index);
                build.removeAction(action);
            }
        }

        @Whitelisted
        public void buildUnstable() {
            build.setResult(Result.UNSTABLE);
        }

        @Whitelisted
        public void buildFailure() {
            build.setResult(Result.FAILURE);
        }

        @Whitelisted
        public void buildSuccess() {
            build.setResult(Result.SUCCESS);
        }

        @Whitelisted
        public void buildAborted() {
            build.setResult(Result.ABORTED);
        }

        @Whitelisted
        public void buildNotBuilt() {
            build.setResult(Result.NOT_BUILT);
        }

        public void buildScriptFailed(Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            boolean isError = scriptFailureResult.isWorseThan(Result.UNSTABLE);
            String icon = isError ? "error" : "warning";
            BadgeSummaryAction summary = createSummary(icon + ".gif");
            summary.setText("<b><font color=\"red\">Groovy script failed:</font></b><br><pre>" + writer + "</pre>");

            addShortText("Groovy", "black", isError ? "#FFE0E0" : "#FFFFC0", "1px", isError ? "#E08080" : "#C0C080");

            Result result = build.getResult();
            if (result == null || result.isBetterThan(scriptFailureResult)) {
                build.setResult(scriptFailureResult);
            }
        }

        @Whitelisted
        public boolean logContains(String regexp) {
            return getLogMatcher(regexp) != null;
        }

        @Deprecated
        public boolean contains(File f, String regexp) {
            return contains(f, Charset.defaultCharset(), regexp);
        }

        @Deprecated
        // not @Whitelisted unless we know what file that is
        public boolean contains(File f, Charset charset, String regexp) {
            Matcher matcher = getMatcher(f, charset, regexp);
            return (matcher != null) && matcher.matches();
        }

        @Whitelisted
        public Matcher getLogMatcher(String regexp) {
            try (Reader r = build.getLogReader()) {
                return getMatcher(r, regexp);
            } catch (IOException e) {
                Functions.printStackTrace(
                        e, listener.error("Groovy Postbuild: logContains(\"" + regexp + "\") failed."));
                buildScriptFailed(e);
                return null;
            }
        }

        @Deprecated
        public Matcher getMatcher(File f, String regexp) {
            return getMatcher(f, Charset.defaultCharset(), regexp);
        }

        public Matcher getMatcher(Reader r, String regexp) {
            Matcher matcher = null;
            try (BufferedReader reader = new BufferedReader(r)) {
                Pattern pattern = compilePattern(regexp);
                // Assume default encoding and text files
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = pattern.matcher(line);
                    if (m.matches()) {
                        matcher = m;
                        break;
                    }
                }
            } catch (IOException e) {
                Functions.printStackTrace(
                        e, listener.error("Groovy Postbuild: getMatcher(…, \"" + regexp + "\") failed."));
                buildScriptFailed(e);
            }
            return matcher;
        }

        @Deprecated
        public Matcher getMatcher(File f, Charset charset, String regexp) {
            LOGGER.fine("Searching for '" + regexp + "' in '" + f + "'.");
            try (InputStream is = new FileInputStream(f);
                    Reader r = new InputStreamReader(is, charset)) {
                return getMatcher(r, regexp);
            } catch (IOException e) {
                Functions.printStackTrace(
                        e, listener.error("Groovy Postbuild: getMatcher(\"" + f + "\", \"" + regexp + "\") failed."));
                buildScriptFailed(e);
            }
            return null;
        }

        private Pattern compilePattern(String regexp) throws AbortException {
            Pattern pattern;
            try {
                pattern = Pattern.compile(regexp);
            } catch (PatternSyntaxException e) {
                listener.getLogger().println("Groovy Postbuild: Unable to compile regular expression '" + regexp + "'");
                throw new AbortException();
            }
            return pattern;
        }

        /**
         * Test whether the current build is specified type.
         *
         * @param buildClass
         * @return true if the current build is an instance of buildClass
         */
        @Whitelisted
        public boolean buildIsA(Class<? extends AbstractBuild<?, ?>> buildClass) {
            return buildClass.isInstance(getBuild());
        }
    }

    @DataBoundConstructor
    public GroovyPostbuildRecorder(SecureGroovyScript script, int behavior, boolean runForMatrixParent) {
        this.script = script.configuringWithNonKeyItem();
        this.behavior = behavior;
        this.runForMatrixParent = runForMatrixParent;
    }

    private Object readResolve() {
        if (groovyScript != null) {
            List<ClasspathEntry> cp = new ArrayList<ClasspathEntry>();
            if (classpath != null) {
                for (@SuppressWarnings("deprecation") GroovyScriptPath gsp : classpath) {
                    try {
                        cp.add(new ClasspathEntry(gsp.path.getAbsolutePath()));
                    } catch (MalformedURLException x) {
                        LOGGER.log(Level.WARNING, "cannot load " + gsp.path, x);
                    }
                }
                classpath = null;
            }
            try {
                script = new SecureGroovyScript(groovyScript, false, cp).configuring(ApprovalContext.create());
            } catch (Descriptor.FormException ex) {
                LOGGER.log(Level.WARNING, "Failed to resolve groovy script during readResolve ", ex);
            }

            groovyScript = null;
        }
        return this;
    }

    @Override
    public final Action getProjectAction(final AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public GroovyPostbuildDescriptor getDescriptor() {
        return (GroovyPostbuildDescriptor) super.getDescriptor();
    }

    @Override
    public final boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {
        boolean scriptResult = true;
        LOGGER.fine("perform() called for script");
        LOGGER.fine("behavior: " + behavior);
        Result scriptFailureResult = Result.SUCCESS;
        switch (behavior) {
            case 0:
                scriptFailureResult = Result.SUCCESS;
                break;
            case 1:
                scriptFailureResult = Result.UNSTABLE;
                break;
            case 2:
                scriptFailureResult = Result.FAILURE;
                break;
            default:
                scriptFailureResult = Result.SUCCESS;
                break; // same to 0
        }
        BadgeManager badgeManager = new BadgeManager(build, listener, scriptFailureResult);
        ClassLoader cl = Jenkins.getInstance().getPluginManager().uberClassLoader;
        Binding binding = new Binding();
        binding.setVariable("manager", badgeManager);
        try {
            script.evaluate(cl, binding);
        } catch (Exception e) {
            // TODO could print more refined errors for UnapprovedUsageException and/or RejectedAccessException:
            e.printStackTrace(listener.error("Failed to evaluate groovy script."));
            badgeManager.buildScriptFailed(e);
            scriptResult = false;
        }
        for (Run<?, ?> b : badgeManager.builds) {
            b.save();
        }

        if (!scriptResult && scriptFailureResult.isWorseOrEqualTo(Result.FAILURE)) {
            return false;
        } else {
            return true;
        }
    }

    public final BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public SecureGroovyScript getScript() {
        return script;
    }

    public int getBehavior() {
        return behavior;
    }

    public boolean isRunForMatrixParent() {
        return runForMatrixParent;
    }

    /**
     * @param build
     * @param launcher
     * @param listener
     * @return
     * @see hudson.matrix.MatrixAggregatable#createAggregator(hudson.matrix.MatrixBuild, hudson.Launcher, hudson.model.BuildListener)
     */
    public MatrixAggregator createAggregator(
            final MatrixBuild build, final Launcher launcher, final BuildListener listener) {
        if (!isRunForMatrixParent()) {
            return null;
        }

        return new MatrixAggregator(build, launcher, listener) {
            /**
             * Called when all child builds are finished.
             *
             * @return
             * @throws InterruptedException
             * @throws IOException
             * @see hudson.matrix.MatrixAggregator#endBuild()
             */
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return perform(build, launcher, listener);
            }
        };
    }
}
