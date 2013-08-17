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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Describable;
import hudson.model.LoadBalancer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet;
import hudson.model.queue.MappingWorksheet.ExecutorChunk;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;

/**
 * LoadBalancer using scores of nodes.
 * 
 * Decides the nodes to execute tasks depending on scores of nodes.
 * Scoring is performed by {@link ScoringRule}s enabled in System Configuration page.
 */
public class ScoringLoadBalancer extends LoadBalancer implements Describable<ScoringLoadBalancer>
{
    private static final Logger LOGGER = Logger.getLogger(ScoringLoadBalancer.class.getName());
    
    @Initializer(after=InitMilestone.PLUGINS_STARTED)
    public static void onPluginStart()
    {
        LOGGER.info("Replaced LoadBalancer to ScoringLoadBalancer");
        Queue q = Jenkins.getInstance().getQueue();
        LoadBalancer fallback = q.getLoadBalancer();
        q.setLoadBalancer(new ScoringLoadBalancer(fallback));
    }
    
    private LoadBalancer fallback;
    
    /**
     * @return LoadBalancer to fall back
     */
    public LoadBalancer getFallback()
    {
        return fallback;
    }
    
    /**
     * @return list of {@link ScoringRule}s
     */
    public List<ScoringRule> getScoringRuleList()
    {
        return getDescriptor().getScoringRuleList();
    }
    
    /**
     * Return whether this LoadBalancer is enabled.
     * 
     * If disabled, simply call LoadBalancer to fall back.
     * 
     * @return whether this LoadBalancer is enabled.
     */
    public boolean isEnabled()
    {
        return getDescriptor().isEnabled();
    }
    
    /**
     * Returns whether to log calculated scores.
     * 
     * @return whether to log calculated scores.
     */
    public boolean isReportScoresEnabled()
    {
        return getDescriptor().isReportScoresEnabled();
    }
    
    /**
     * Constructor.
     * 
     * @param fallback LoadBalancer to fall back. Specify originally registered LoadBalancer.
     */
    public ScoringLoadBalancer(LoadBalancer fallback)
    {
        this.fallback = fallback;
    }
    
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
    public Mapping map(Task task, MappingWorksheet worksheet)
    {
        Mapping m = worksheet.new Mapping();
        
        // retrieve scoringRuleList not to behave inconsistently when configuration is updated.
        List<ScoringRule> scoringRuleList = getScoringRuleList();
        
        if(isEnabled() && assignGreedily(m, task, worksheet, scoringRuleList))
        {
            return m;
        }
        else
        {
            if(getFallback() != null)
            {
                return getFallback().map(task, worksheet);
            }
            else
            {
                return null;
            }
        }
    }
    
    private boolean assignGreedily(Mapping m, Task task, MappingWorksheet worksheet,
            List<ScoringRule> scoringRuleList)
    {
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
     */
    private boolean assignGreedily(Mapping m, Task task, MappingWorksheet worksheet,
            List<ScoringRule> scoringRuleList, int targetWorkChunk)
    {
        if(targetWorkChunk >= worksheet.works.size())
        {
            return m.isCompletelyValid();
        }
        
        // Current target work chunk (subtask).
        WorkChunk wc = worksheet.works(targetWorkChunk);
        
        // Initialize nodes-to-scores map.
        List<ExecutorChunk> executors = new ArrayList<ExecutorChunk>(wc.applicableExecutorChunks());
        Map<Node,Integer> nodeScoreMap = new HashMap<Node,Integer>();
        for(ExecutorChunk ec: executors)
        {
            nodeScoreMap.put(ec.computer.getNode(), 0);
        }
        
        // Score nodes by calling enabled ScoringRules.
        for(ScoringRule scoringRule: getScoringRuleList())
        {
            scoringRule.updateScores(task, wc, m, nodeScoreMap);
        }
        
        sortExecutors(executors, nodeScoreMap);
        
        if(isReportScoresEnabled())
        {
            reportScores(executors, nodeScoreMap);
        }
        
        for(ExecutorChunk ec: executors)
        {
            m.assign(targetWorkChunk, ec);
            if(
                    m.isPartiallyValid()
                    && assignGreedily(m, task, worksheet, scoringRuleList, targetWorkChunk + 1)
            )
            {
                return true;
            }
        }
        
        m.assign(targetWorkChunk,null); // Reset assignment
        
        return false;
    }
    
    /**
     * sort ExecuterChunks (that is, nodes) by scores.
     * 
     * @param executors
     * @param nodeScoreMap
     */
    protected void sortExecutors(List<ExecutorChunk> executors,
            final Map<Node, Integer> nodeScoreMap)
    {
        Collections.shuffle(executors);
        Collections.sort(executors, new Comparator<ExecutorChunk>(){
            @Override
            public int compare(ExecutorChunk o1, ExecutorChunk o2)
            {
                return nodeScoreMap.get(o2.computer.getNode()) - nodeScoreMap.get(o1.computer.getNode());
            }
        });
    }
    
    /**
     * Log scores. For diagnostics purpose.
     * 
     * @param executors
     * @param nodeScoreMap
     */
    protected void reportScores(List<ExecutorChunk> executors,
            final Map<Node, Integer> nodeScoreMap)
    {
        LOGGER.info("Scoring:");
        for(ExecutorChunk ec: executors)
        {
            LOGGER.info(String.format("%s: %d", ec.computer.getNode().getNodeName(), nodeScoreMap.get(ec.computer.getNode())));
        }
    }
    
    /**
     * Returns the instance of {@link DescriptorImpl}.
     * 
     * @return the instance of {@link DescriptorImpl}.
     * @see hudson.model.Describable#getDescriptor()
     */
    @Override
    public DescriptorImpl getDescriptor()
    {
        return DESCRIPTOR;
    }
    
    private static DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    /**
     * Descriptor for {@link ScoringLoadBalancer}
     * 
     * Manages views and holds configuration for {@link ScoringLoadBalancer}.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<ScoringLoadBalancer>
    {
        private boolean enabled = true;
        
        /**
         * Returns whether ScoringLoadBalancer is enabled
         * 
         * @return whether ScoringLoadBalancer is enabled
         */
        public boolean isEnabled()
        {
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
        public boolean isReportScoresEnabled()
        {
            return reportScoresEnabled;
        }
        
        private List<ScoringRule> scoringRuleList = Collections.emptyList();
        
        /**
         * Returns the list of enabled scoring rules.
         * 
         * @return the enabled scoring rules.
         */
        public List<ScoringRule> getScoringRuleList()
        {
            return scoringRuleList;
        }
        
        /**
         * Constructor.
         * 
         * Restore configurations.
         */
        public DescriptorImpl()
        {
            load();
        }
        
        /**
         * Returns the name to display.
         * 
         * @return the name to display
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName()
        {
            return Messages.ScoringLoadBalancer_DisplayName();
        }
        
        /**
         * Returns all {@link ScoringRule}s registered to Jenkins.
         * 
         * @return list of {@link Descriptor} of {@link ScoringRule}s.
         */
        public DescriptorExtensionList<ScoringRule, Descriptor<ScoringRule>> getAllScoringRuleList()
        {
            return ScoringRule.all();
        }
    }
    
}
