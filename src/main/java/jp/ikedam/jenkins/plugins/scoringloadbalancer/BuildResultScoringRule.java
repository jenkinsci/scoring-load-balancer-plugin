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

import hudson.Extension;
import hudson.model.Build;
import hudson.model.Result;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A score keeper depends on build results on each nodes.
 */
public class BuildResultScoringRule extends ScoringRule
{
    private int numberOfBuilds = 10;
    private int scale = 10;
    private int scaleAdjustForOlder = -1;
    private int scoreForSuccess = 1;
    private int scoreForUnstable = -1;
    private int scoreForFailure = -1;
    
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
    
    public BuildResultScoringRule()
    {
        // TODO Auto-generated constructor stub
    }
    
    /**
     * Scores the nodes depends on build results on those nodes.
     * 
     * @param task
     * @param wc
     * @param m
     * @param nodeScoreMap
     */
    @Override
    public void updateScores(Task task, WorkChunk wc, Mapping m,
            Map<Node, Integer> nodeScoreMap)
    {
        if(!(task instanceof Project))
        {
            return;
        }
        
        Project<?,?> project = (Project<?, ?>)task;
        
        Set<Node> nodeSet = new HashSet<Node>(nodeScoreMap.keySet());
        Build<?,?> build = project.getLastBuild();
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
                nodeScoreMap.put(node, nodeScoreMap.get(node) + getScoreForSuccess() * scale);
                nodeSet.remove(node);
            }
            else if(Result.FAILURE == build.getResult())
            {
                nodeScoreMap.put(node, nodeScoreMap.get(node) + getScoreForFailure() * scale);
                nodeSet.remove(node);
            }
            else if(Result.UNSTABLE == build.getResult())
            {
                nodeScoreMap.put(node, nodeScoreMap.get(node) + getScoreForUnstable() * scale);
                nodeSet.remove(node);
            }
        }
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
