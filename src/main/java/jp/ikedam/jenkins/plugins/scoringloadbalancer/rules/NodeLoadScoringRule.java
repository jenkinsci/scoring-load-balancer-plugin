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

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet.ExecutorChunk;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore;

/**
 * Score nodes depending on their loads.
 *
 */
public class NodeLoadScoringRule extends ScoringRule
{
    private int scale;
    
    /**
     * @return the scale
     */
    public int getScale()
    {
        return scale;
    }
    
    private int scoreForIdleExecutor;
    
    /**
     * @return the scoreForIdleExecutor
     */
    public int getScoreForIdleExecutor()
    {
        return scoreForIdleExecutor;
    }
    
    private int scoreForBusyExecutor;
    
    /**
     * @return the scoreForBusyExecutor
     */
    public int getScoreForBusyExecutor()
    {
        return scoreForBusyExecutor;
    }
    
    @DataBoundConstructor
    public NodeLoadScoringRule(
            int scale,
            int scoreForIdleExecutor,
            int scoreForBusyExecutor
    )
    {
        this.scale = scale;
        this.scoreForIdleExecutor = scoreForIdleExecutor;
        this.scoreForBusyExecutor = scoreForBusyExecutor;
    }
    
    /**
     * Score nodes depending on their loads.
     * 
     * @param task
     * @param wc
     * @param m
     * @param nodeScore
     * 
     * @return
     * 
     * @throws Exception
     * 
     * @see jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule#updateScores(hudson.model.Queue.Task, hudson.model.queue.MappingWorksheet.WorkChunk, hudson.model.queue.MappingWorksheet.Mapping, jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore)
     */
    @Override
    public boolean updateScores(Task task, WorkChunk wc, Mapping m, NodesScore nodesScore) throws Exception
    {
        for(ExecutorChunk ec: nodesScore.getExecutorChunks())
        {
            // There are cases that ec.computer.countBusy(), ec.computer.countIdle()
            // is not yet updated when builds are triggered consequently.
            int idle = ec.capacity();
            for(int i = 0; i < m.size(); ++i)
            {
                // count about to be assigned executors
                if(ec.equals(m.assigned(i)))
                {
                    idle -= m.get(i).size();
                }
            }
            int busy = ec.computer.countExecutors() - idle;
            
            int busyScore = busy * getScoreForBusyExecutor();
            int idleScore = idle * getScoreForIdleExecutor();
            nodesScore.addScore(ec, (busyScore + idleScore) * getScale());
        }
        
        return true;
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<ScoringRule>
    {
        @Override
        public String getDisplayName()
        {
            return Messages.NodeLoadScoringRule_DisplayName();
        }
    }
}
