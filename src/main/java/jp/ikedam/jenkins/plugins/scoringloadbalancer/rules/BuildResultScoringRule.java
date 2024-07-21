/*
 * The MIT License
 *
 * Copyright (c) 2013 IKEDA Yasuyuki
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
package jp.ikedam.jenkins.plugins.scoringloadbalancer.rules;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.Result;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import hudson.model.queue.SubTask;
import hudson.util.FormValidation;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.Jenkins;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.util.ValidationUtil;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * A score keeper depends on build results on each nodes.
 */
public class BuildResultScoringRule extends ScoringRule {
    // default values are defined in config.jelly
    private int numberOfBuilds;
    private int scale;
    private int scaleAdjustForOlder;
    private int scoreForSuccess;
    private int scoreForUnstable;
    private int scoreForFailure;

    /**
     * @return the numberOfBuilds
     */
    public int getNumberOfBuilds() {
        return numberOfBuilds;
    }

    /**
     * @return the scale
     */
    public int getScale() {
        return scale;
    }

    /**
     * @return the scaleAdjustForOlder
     */
    public int getScaleAdjustForOlder() {
        return scaleAdjustForOlder;
    }

    /**
     * @return the scoreForSuccess
     */
    public int getScoreForSuccess() {
        return scoreForSuccess;
    }

    /**
     * @return the scoreForUnstable
     */
    public int getScoreForUnstable() {
        return scoreForUnstable;
    }

    /**
     * @return the scoreForFailure
     */
    public int getScoreForFailure() {
        return scoreForFailure;
    }

    /**
     * Constructor.
     *
     * Initialized with values a user configured.
     *
     * @param numberOfBuilds
     * @param scale
     * @param scaleAdjustForOlder
     * @param scoreForSuccess
     * @param scoreForUnstable
     * @param scoreForFailure
     */
    @DataBoundConstructor
    public BuildResultScoringRule(
            int numberOfBuilds,
            int scale,
            int scaleAdjustForOlder,
            int scoreForSuccess,
            int scoreForUnstable,
            int scoreForFailure) {
        this.numberOfBuilds = numberOfBuilds;
        this.scale = scale;
        this.scaleAdjustForOlder = scaleAdjustForOlder;
        this.scoreForSuccess = scoreForSuccess;
        this.scoreForUnstable = scoreForUnstable;
        this.scoreForFailure = scoreForFailure;
    }

    /**
     * Scores the nodes depending on build results on those nodes.
     *
     * @param task
     * @param wc
     * @param m
     * @param nodesScore
     */
    @Override
    public boolean updateScores(Task task, WorkChunk wc, Mapping m, NodesScore nodesScore) {
        for (SubTask subtask : wc) {

            AbstractBuild<?, ?> freestylebuild = null;
            WorkflowRun pipelinebuild = null;

            if ((subtask instanceof AbstractProject)) {
                AbstractProject<?, ?> project = (AbstractProject<?, ?>) subtask;
                freestylebuild = project.getLastBuild();
            } else if (subtask != null) {
                Executable qexec = subtask.getOwnerExecutable();
                if (qexec != null && (qexec.getParent() instanceof WorkflowJob)) {
                    WorkflowJob wfj = (WorkflowJob) qexec.getParent();
                    pipelinebuild = (WorkflowRun) wfj.getLastCompletedBuild();
                }
            }

            if (freestylebuild == null && pipelinebuild == null) {
                continue;
            }

            Set<Node> nodeSet = new HashSet<Node>(nodesScore.getNodes());

            for (int pastNum = 0; pastNum < getNumberOfBuilds(); ++pastNum) {

                Node node = null;
                Result result = null;

                if (freestylebuild != null) {
                    node = freestylebuild.getBuiltOn();
                    result = freestylebuild.getResult();
                    freestylebuild = freestylebuild.getPreviousBuild();
                } else if (pipelinebuild != null) {
                    FlowExecution fexec = pipelinebuild.getExecution();
                    result = pipelinebuild.getResult();
                    pipelinebuild = pipelinebuild.getPreviousBuild();

                    if (fexec == null) {
                        continue;
                    }

                    FlowGraphWalker fgw = new FlowGraphWalker(fexec);
                    for (FlowNode fn : fgw) {
                        if (fn instanceof StepStartNode) {
                            WorkspaceAction action = (WorkspaceAction) fn.getAction(WorkspaceAction.class);
                            if (action != null) {
                                node = Jenkins.get().getNode(action.getNode());
                                if (node != null && nodeSet.contains(node)) {
                                    break;
                                }
                            }
                        }
                    }
                }

                if (node == null || result == null || !nodeSet.contains(node)) {
                    continue;
                }

                int scale = getScale() + getScaleAdjustForOlder() * pastNum;

                if (Result.SUCCESS == result) {
                    nodesScore.addScore(node, getScoreForSuccess() * scale);
                    nodeSet.remove(node);
                } else if (Result.FAILURE == result) {
                    nodesScore.addScore(node, getScoreForFailure() * scale);
                    nodeSet.remove(node);
                } else if (Result.UNSTABLE == result) {
                    nodesScore.addScore(node, getScoreForUnstable() * scale);
                    nodeSet.remove(node);
                }
            }
        }

        return true;
    }

    /**
     * Manages views for {@link BuildResultScoringRule}
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<ScoringRule> {
        /**
         * Returns the name to display.
         *
         * Displayed in System Configuration page, as a name of a scoring rule.
         *
         * @return the name to display
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return Messages.BuildResultScoringRule_DisplayName();
        }

        /**
         * Verify the input number of builds.
         *
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckNumberOfBuilds(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.READ);
            if (value == null || value.isBlank()) {
                return FormValidation.error(Messages.BuildResultScoringRule_numberOfBuilds_required());
            }

            try {
                int num = Integer.parseInt(value.trim());
                if (num <= 0) {
                    return FormValidation.error(Messages.BuildResultScoringRule_numberOfBuilds_invalid());
                }
            } catch (NumberFormatException e) {
                return FormValidation.error(e, Messages.BuildResultScoringRule_numberOfBuilds_invalid());
            }
            return FormValidation.ok();
        }

        /**
         * Verify the input scale.
         *
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckScale(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.READ);
            return ValidationUtil.doCheckInteger(value);
        }

        /**
         * Verify the input scaleAdjustForOlder.
         *
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckScaleAdjustForOlder(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.READ);
            return ValidationUtil.doCheckInteger(value);
        }

        /**
         * Verify the input scoreForSuccess.
         *
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckScoreForSuccess(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.READ);
            return ValidationUtil.doCheckInteger(value);
        }

        /**
         * Verify the input scoreForUnstable.
         *
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckScoreForUnstable(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.READ);
            return ValidationUtil.doCheckInteger(value);
        }

        /**
         * Verify the input scoreForFailure.
         *
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckScoreForFailure(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.READ);
            return ValidationUtil.doCheckInteger(value);
        }
    }
}
