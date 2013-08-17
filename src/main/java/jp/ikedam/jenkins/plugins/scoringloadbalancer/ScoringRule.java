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
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;

import java.util.Map;

import jenkins.model.Jenkins;

/**
 * Scores the nodes to determine which is proper to have a task build on.
 */
public abstract class ScoringRule extends AbstractDescribableImpl<ScoringRule> implements ExtensionPoint, Describable<ScoringRule>
{
    /**
     * Score the nodes.
     * 
     * Update nodeScoreMap to score.
     * A node with the largest score are used primarily.
     * 
     * @param task the root task to build.
     * @param wc
     * @param m currently mapping status. there may be nodes already assigned.
     * @param nodeScoreMap a map from nodes to their scores
     */
    public abstract void updateScores(Task task, WorkChunk wc, Mapping m, Map<Node, Integer> nodeScoreMap);
    
    /**
     * Returns all {@link ScoringRule}s registered to Jenkins.
     * 
     * @return list of {@link Descriptor} of {@link ScoringRule}s.
     */
    public static DescriptorExtensionList<ScoringRule, Descriptor<ScoringRule>> all()
    {
        return Jenkins.getInstance().getDescriptorList(ScoringRule.class);
    }
}
