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
package jp.ikedam.jenkins.plugins.scoringloadbalancer;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.LoadBalancer;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet;
import hudson.model.queue.MappingWorksheet.ExecutorChunk;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import hudson.model.queue.SubTask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * LoadBalancer using scores of nodes.
 *
 * Decides the nodes to execute tasks depending on scores of nodes.
 * Scoring is performed by {@link ScoringRule}s enabled in System Configuration page.
 */
public class ScoringLoadBalancer extends LoadBalancer implements Describable<ScoringLoadBalancer> {
    private static final Logger LOGGER = Logger.getLogger(ScoringLoadBalancer.class.getName());

    /**
     * Replaces {@link LoadBalancer} registered to Jenkins to ScoringLoadBalancer.
     *
     * The {@link LoadBalancer} originally registered are used as one to fall back.
     */
    @Initializer(after = InitMilestone.PLUGINS_STARTED, fatal = false)
    public static void installLoadBalancer() {
        LOGGER.info("Replace LoadBalancer to ScoringLoadBalancer");
        Queue q = Jenkins.get().getQueue();
        LoadBalancer fallback = q.getLoadBalancer();
        q.setLoadBalancer(new ScoringLoadBalancer(fallback));
    }

    private LoadBalancer fallback;

    /**
     * @return {@link LoadBalancer} to fall back
     */
    public LoadBalancer getFallback() {
        return fallback;
    }

    /**
     * @return list of {@link ScoringRule}s
     */
    public List<ScoringRule> getScoringRuleList() {
        return getDescriptor().getScoringRuleList();
    }

    /**
     * Return whether this LoadBalancer is enabled.
     *
     * If disabled, simply call LoadBalancer to fall back.
     *
     * @return whether this LoadBalancer is enabled.
     */
    public boolean isEnabled() {
        return getDescriptor().isEnabled();
    }

    /**
     * Returns whether to log calculated scores.
     *
     * @return whether to log calculated scores.
     */
    public boolean isReportScoresEnabled() {
        return getDescriptor().isReportScoresEnabled();
    }

    /**
     * Constructor.
     *
     * @param fallback LoadBalancer to fall back. Specify originally registered LoadBalancer.
     */
    public ScoringLoadBalancer(LoadBalancer fallback) {
        this.fallback = fallback;
    }

    private long lastEvaluation = System.currentTimeMillis();

    /**
     * Decides nodes to run tasks on.
     *
     * @param task the root task.
     * @param worksheet an object containing information of subtasks to execute and nodes tasks can execute on.
     *
     * @return mapping from subtasks to nodes.
     *
     * @see hudson.model.LoadBalancer#map(hudson.model.Queue.Task, hudson.model.queue.MappingWorksheet)
     */
    @Override
    public Mapping map(Task task, MappingWorksheet worksheet) {
        // Jenkins provides incomplete executors for simultaneous builds - throttle build starts:
        // abort if last call isn't that long ago:
        if (lastEvaluation > System.currentTimeMillis() - 1000) {
            return null;
        }
        this.lastEvaluation = System.currentTimeMillis();

        Mapping m = worksheet.new Mapping();

        // retrieve scoringRuleList not to behave inconsistently when configuration is updated.
        List<ScoringRule> scoringRuleList = getScoringRuleList();

        if (isEnabled()) {
            try {
                if (assignGreedily(m, task, worksheet, scoringRuleList)) {
                    return m;
                } else {
                    return null;
                }
            } catch (Exception e) {
                LOGGER.log(
                        Level.SEVERE, "Failed to load balance with scores: fallback to preconfigured LoadBalancer", e);
            }
        }

        if (getFallback() != null) {
            return getFallback().map(task, worksheet);
        }

        LOGGER.severe("No LoadBalancer to fall back is defined: Builds are NEVER launched.");
        return null;
    }

    private boolean assignGreedily(Mapping m, Task task, MappingWorksheet worksheet, List<ScoringRule> scoringRuleList)
            throws Exception {
        return assignGreedily(m, task, worksheet, scoringRuleList, 0);
    }

    /**
     * Decide nodes to execute subtasks on.
     *
     * Decides in a　greedy and recursive way as following steps:
     * <ol>
     *   <li>Pick the first subtask</li>
     *   <li>Score all nodes by calling all enabled {@link ScoringRule}</li>
     *   <li>Pick the node with the highest score. Assign that node to the current subtask.</li>
     *   <li>Pick the next subtask, and back to 2. Scoring is performed for each subtasks,
     *     for the case scores differ for each subtask.</li>
     *   <li>If assignment is failed (e.g. some constrains is broken), pick the next node, and back to 3.</li>
     *   <li>If assignment is succeeded, return that assignment.</li>
     * </ol>
     *
     * @param m
     * @param task
     * @param worksheet
     * @param scoringRuleList
     * @param targetWorkChunk
     * @return　whether an proper assignment is found.
     *
     * @throws Exception
     */
    private boolean assignGreedily(
            Mapping m, Task task, MappingWorksheet worksheet, List<ScoringRule> scoringRuleList, int targetWorkChunk)
            throws Exception {
        if (targetWorkChunk >= worksheet.works.size()) {
            return m.isCompletelyValid();
        }

        // Current target work chunk (subtask).
        WorkChunk wc = worksheet.works(targetWorkChunk);

        // Initialize nodes-to-scores map.
        List<ExecutorChunk> executors = new ArrayList<ExecutorChunk>(wc.applicableExecutorChunks());
        NodesScore nodesScore = new NodesScore(executors);

        // Score nodes by calling enabled ScoringRules.
        for (ScoringRule scoringRule : getScoringRuleList()) {
            if (!scoringRule.updateScores(task, wc, m, nodesScore)) {
                break;
            }
        }

        sortExecutors(executors, nodesScore);

        if (isReportScoresEnabled()) {
            reportScores(wc, executors, nodesScore);
        }

        for (ExecutorChunk ec : executors) {
            if (nodesScore.isInvalid(ec)) {
                continue;
            }
            m.assign(targetWorkChunk, ec);
            if (m.isPartiallyValid() && assignGreedily(m, task, worksheet, scoringRuleList, targetWorkChunk + 1)) {
                return true;
            }
        }

        m.assign(targetWorkChunk, null); // Reset assignment

        return false;
    }

    /**
     * sort {@link ExecutorChunk}s (that is, nodes) by scores.
     *
     * @param executors
     * @param nodesScore
     */
    protected void sortExecutors(List<ExecutorChunk> executors, NodesScore nodesScore) {
        Collections.shuffle(executors);
        Collections.sort(executors, nodesScore.new ExecutorComparator());
    }

    /**
     * Log scores. For diagnostics purpose.
     *
     * @param executors
     * @param nodesScore
     */
    protected void reportScores(WorkChunk wc, List<ExecutorChunk> executors, NodesScore nodesScore) {
        List<String> lines = new ArrayList<String>();
        List<String> wcs = wc.stream().map(SubTask::toString).collect(Collectors.toList());
        lines.add(String.format("Scoring for %s:", String.join(",", wcs)));
        for (ExecutorChunk ec : executors) {
            lines.add(String.format("  %20s: %4d", ec.getName(), nodesScore.getScore(ec)));
        }
        LOGGER.info(String.join(System.getProperty("line.separator"), lines));
    }

    /**
     * Returns the instance of {@link DescriptorImpl}.
     *
     * @return the instance of {@link DescriptorImpl}.
     * @see hudson.model.Describable#getDescriptor()
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Descriptor for {@link ScoringLoadBalancer}
     *
     * Manages views and holds configuration for {@link ScoringLoadBalancer}.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<ScoringLoadBalancer> {
        private boolean enabled = true;

        /**
         * Returns whether ScoringLoadBalancer is enabled
         *
         * @return whether ScoringLoadBalancer is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        private boolean reportScoresEnabled = false;

        /**
         * Returns whether to log scores of nodes in each load balancing calculation.
         *
         * Enabling this makes many outputs, and not good for production environments.
         *
         * @return whether to log scores of nodes
         */
        public boolean isReportScoresEnabled() {
            return reportScoresEnabled;
        }

        private List<ScoringRule> scoringRuleList = Collections.emptyList();

        /**
         * Returns the list of enabled scoring rules.
         *
         * @return the enabled scoring rules.
         */
        public List<ScoringRule> getScoringRuleList() {
            return scoringRuleList;
        }

        /**
         * Constructor.
         *
         * Restore configurations.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Update and store configuration.
         *
         * @param req
         * @param json
         *
         * @return
         *
         * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
            enabled = json.getBoolean("enabled");
            reportScoresEnabled = json.getBoolean("reportScoresEnabled");
            scoringRuleList = req.bindJSONToList(ScoringRule.class, json.get("scoringRuleList"));
            save();
            return super.configure(req, json);
        }

        /**
         * Used for testing purpose.
         *
         * @param enabled
         * @param reportScoresEnabled
         * @param scoringRuleList
         * @return
         */
        public boolean configure(boolean enabled, boolean reportScoresEnabled, List<ScoringRule> scoringRuleList) {
            this.enabled = enabled;
            this.reportScoresEnabled = reportScoresEnabled;
            this.scoringRuleList = scoringRuleList;
            save();
            return true;
        }

        /**
         * Used for testing purpose.
         *
         * @param enabled
         * @param reportScoresEnabled
         * @param scoringRules
         * @return
         */
        public boolean configure(boolean enabled, boolean reportScoresEnabled, ScoringRule... scoringRules) {
            return configure(enabled, reportScoresEnabled, Arrays.asList(scoringRules));
        }

        /**
         * Returns the name to display.
         *
         * Displayed in System Configuration page as a section title.
         *
         * @return the name to display
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return Messages.ScoringLoadBalancer_DisplayName();
        }

        /**
         * Returns all {@link ScoringRule}s registered to Jenkins.
         *
         * @return list of {@link Descriptor} of {@link ScoringRule}s.
         */
        public DescriptorExtensionList<ScoringRule, Descriptor<ScoringRule>> getAllScoringRuleList() {
            return ScoringRule.all();
        }
    }

    /**
     * Holds scores of nodes.
     *
     * A node with a larger score is preferred to use.
     */
    public static class NodesScore {
        private Map<Node, ExecutorChunk> nodeExecutorMap;
        private Map<ExecutorChunk, Integer> executorScoreMap;
        private Set<ExecutorChunk> invalidExecutors;

        /**
         * Constructor
         *
         * Initialize scores for each nodes to 0.
         *
         * @param executors
         */
        public NodesScore(Collection<ExecutorChunk> executors) {
            nodeExecutorMap = new HashMap<Node, ExecutorChunk>(executors.size());
            executorScoreMap = new HashMap<ExecutorChunk, Integer>(executors.size());
            invalidExecutors = new HashSet<ExecutorChunk>();

            for (ExecutorChunk executor : executors) {
                nodeExecutorMap.put(executor.node, executor);
                executorScoreMap.put(executor, 0);
            }
        }

        /**
         * Get nodes to score.
         *
         * same to retrieve {@link ExecutorChunk#node} for {@link NodesScore#getExecutorChunks()}
         *
         * @return nodes to score.
         */
        public Collection<Node> getNodes() {
            return nodeExecutorMap.keySet();
        }

        /**
         * Get executors to score.
         *
         * @return executors to score.
         */
        public Collection<ExecutorChunk> getExecutorChunks() {
            return executorScoreMap.keySet();
        }

        /**
         * Add score to the node.
         *
         * @param node
         * @param score
         */
        public void addScore(Node node, int score) {
            addScore(nodeExecutorMap.get(node), score);
        }

        /**
         * Add score to the node.
         *
         * Same to call {@link NodesScore#addScore(Node, int)} for executor.node
         *
         * @param executor
         * @param score
         */
        public void addScore(ExecutorChunk executor, int score) {
            executorScoreMap.put(executor, executorScoreMap.get(executor) + score);
        }

        /**
         * Reset the score of the node to 0.
         *
         * @param node
         */
        public void resetScore(Node node) {
            resetScore(nodeExecutorMap.get(node));
        }

        /**
         * Reset the score of the node to 0.
         *
         * Same to call {@link NodesScore#resetScore(Node)} for executor.node
         *
         * @param executor
         */
        public void resetScore(ExecutorChunk executor) {
            executorScoreMap.put(executor, 0);
        }

        /**
         * Get the score of the node.
         *
         * @param node
         * @return
         */
        public int getScore(Node node) {
            return getScore(nodeExecutorMap.get(node));
        }

        /**
         * Get the score of the node.
         *
         * Same to call {@link NodesScore#getScore(Node)} for executor.node.
         *
         * @param executor
         * @return
         */
        public int getScore(ExecutorChunk executor) {
            return executorScoreMap.get(executor);
        }

        /**
         * Make the task not run on this node.
         *
         * Same to call {@link NodesScore#markInvalid(Node)} for executor.node.
         *
         * @param executor
         */
        public void markInvalid(ExecutorChunk executor) {
            invalidExecutors.add(executor);
        }

        /**
         * Make the task not run on this node.
         *
         * @param node
         */
        public void markInvalid(Node node) {
            markInvalid(nodeExecutorMap.get(node));
        }

        /**
         * Reset invalid marks on all nodes.
         */
        public void resetInvalid() {
            invalidExecutors.clear();
        }

        /**
         * Mark all nodes invalid.
         */
        public void markAllInvalid() {
            invalidExecutors.addAll(executorScoreMap.keySet());
        }

        /**
         * Same to call {@link NodesScore#isInvalid(Node)} for executor.node.
         *
         * @param executor
         * @return
         */
        public boolean isInvalid(ExecutorChunk executor) {
            return invalidExecutors.contains(executor);
        }

        /**
         * @param node
         * @return
         */
        public boolean isInvalid(Node node) {
            return isInvalid(nodeExecutorMap.get(node));
        }

        /**
         * Comparator for sorting {@link ExecutorChunk}
         */
        public class ExecutorComparator implements Comparator<ExecutorChunk> {
            @Override
            public int compare(ExecutorChunk o1, ExecutorChunk o2) {
                return getScore(o2) - getScore(o1);
            }
        }
    }
}
