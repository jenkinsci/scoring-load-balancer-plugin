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
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.LoadBalancer;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import jenkins.model.Jenkins;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore;

/**
 * Scores nodes to determine which is proper to have a task build on.
 */
public abstract class ScoringRule extends AbstractDescribableImpl<ScoringRule> implements ExtensionPoint {
    /**
     * Score the nodes.
     *
     * Update scores by calling methods of nodesScore.
     * A node with a larger score is preferred to use.
     *
     * If you want not to have scores updated with subsequent {@link ScoringRule}s, return false.
     *
     * @param task the root task to build.
     * @param wc Current work chunk (a set of subtasks that must run on the same node).
     * @param m currently mapping status. there may be nodes already assigned.
     * @param nodesScore a map from nodes to their scores
     *
     * @return whether to score with subsequent {@link ScoringRule}.
     *
     * @throws Exception if any exception occurs, {@link ScoringLoadBalancer} falls back to a {@link LoadBalancer} registered originally.
     */
    public abstract boolean updateScores(Task task, WorkChunk wc, Mapping m, NodesScore nodesScore) throws Exception;

    /**
     * Returns all {@link ScoringRule}s registered to Jenkins.
     *
     * @return list of {@link Descriptor} of {@link ScoringRule}s.
     */
    public static DescriptorExtensionList<ScoringRule, Descriptor<ScoringRule>> all() {
        return Jenkins.get().getDescriptorList(ScoringRule.class);
    }
}
