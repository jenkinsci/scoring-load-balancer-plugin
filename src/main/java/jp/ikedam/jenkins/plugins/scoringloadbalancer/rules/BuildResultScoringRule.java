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
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import hudson.model.queue.SubTask;

import java.util.HashSet;
import java.util.Set;

import org.kohsuke.stapler.DataBoundConstructor;

import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore;

/**
 * A score keeper depends on build results on each nodes.
 */
public class BuildResultScoringRule extends ScoringRule
{
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
    public int getNumberOfBuilds()
    {
        return numberOfBuilds;
    }
    
    /**
     * @return the scale
     */
    public int getScale()
    {
        return scale;
    }
    
    /**
     * @return the scaleAdjustForOlder
     */
    public int getScaleAdjustForOlder()
    {
        return scaleAdjustForOlder;
    }
    
    /**
     * @return the scoreForSuccess
     */
    public int getScoreForSuccess()
    {
        return scoreForSuccess;
    }
    
    /**
     * @return the scoreForUnstable
     */
    public int getScoreForUnstable()
    {
        return scoreForUnstable;
    }
    
    /**
     * @return the scoreForFailure
     */
    public int getScoreForFailure()
    {
        return scoreForFailure;
    }
    
    @DataBoundConstructor
    public BuildResultScoringRule(
            int numberOfBuilds,
            int scale,
            int scaleAdjustForOlder,
            int scoreForSuccess,
            int scoreForUnstable,
            int scoreForFailure
    )
    {
        this.numberOfBuilds = numberOfBuilds;
        this.scale = scale;
        this.scaleAdjustForOlder = scaleAdjustForOlder;
        this.scoreForSuccess = scoreForSuccess;
        this.scoreForUnstable = scoreForUnstable;
        this.scoreForFailure = scoreForFailure;
    }
    
    /**
     * Scores the nodes depends on build results on those nodes.
     * 
     * @param task
     * @param wc
     * @param m
     * @param nodesScore
     */
    @Override
    public boolean updateScores(Task task, WorkChunk wc, Mapping m,
            NodesScore nodesScore)
    {
        for(SubTask subtask: wc)
        {
            if(!(subtask instanceof AbstractProject))
            {
                return true;
            }
            
            AbstractProject<?,?> project = (AbstractProject<?, ?>)subtask;
            
            Set<Node> nodeSet = new HashSet<Node>(nodesScore.getNodes());
            AbstractBuild<?,?> build = project.getLastBuild();
            for(
                    int pastNum = 0;
                    pastNum < getNumberOfBuilds() && build != null;
                    ++pastNum, build = build.getPreviousBuild())
            {
                Node node = build.getBuiltOn();
                if(!nodeSet.contains(node))
                {
                    continue;
                }
                
                int scale = getScale() + getScaleAdjustForOlder() * pastNum;
                
                if(Result.SUCCESS == build.getResult())
                {
                    nodesScore.addScore(node, getScoreForSuccess() * scale);
                    nodeSet.remove(node);
                }
                else if(Result.FAILURE == build.getResult())
                {
                    nodesScore.addScore(node, getScoreForFailure() * scale);
                    nodeSet.remove(node);
                }
                else if(Result.UNSTABLE == build.getResult())
                {
                    nodesScore.addScore(node, getScoreForUnstable() * scale);
                    nodeSet.remove(node);
                }
            }
        }
        
        return true;
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<ScoringRule>
    {
        @Override
        public String getDisplayName()
        {
            return Messages.BuildResultScoringRule_DisplayName();
        }
    }
}
