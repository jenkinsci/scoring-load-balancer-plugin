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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import antlr.ANTLRException;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue.Task;
import hudson.model.labels.LabelExpression;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import hudson.model.queue.SubTask;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreference;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreferenceJobProperty;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreferenceNodeProperty;

/**
 * Scores nodes depending on preferences of nodes and projects.
 */
public class NodePreferenceScoringRule extends ScoringRule
{
    private static Logger LOGGER = Logger.getLogger(NodePreferenceScoringRule.class.getName());
    
    private int nodesPreferenceScale;
    /**
     * @return the nodesPreferenceScale
     */
    public int getNodesPreferenceScale()
    {
        return nodesPreferenceScale;
    }
    
    private int projectPreferenceScale;
    
    /**
     * @return the projectPreferenceScale
     */
    public int getProjectPreferenceScale()
    {
        return projectPreferenceScale;
    }
    
    @DataBoundConstructor
    public NodePreferenceScoringRule(int nodesPreferenceScale, int projectPreferenceScale)
    {
        this.nodesPreferenceScale = nodesPreferenceScale;
        this.projectPreferenceScale = projectPreferenceScale;
    }
    
    /**
     * Scores nodes depending on preferences of nodes and projects.
     * 
     * @param task
     * @param wc
     * @param m
     * @param nodesScore
     * @return
     * @see jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule#updateScores(hudson.model.Queue.Task, hudson.model.queue.MappingWorksheet.WorkChunk, hudson.model.queue.MappingWorksheet.Mapping, jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore)
     */
    @Override
    public boolean updateScores(Task task, WorkChunk wc, Mapping m,
            NodesScore nodesScore)
    {
        // scores by preference configured in nodes.
        for(Node node: nodesScore.getNodes())
        {
            List<BuildPreferenceNodeProperty> prefList = 
                    Util.filter(node.getNodeProperties(), BuildPreferenceNodeProperty.class);
            if(prefList == null || prefList.isEmpty())
            {
                continue;
            }
            BuildPreferenceNodeProperty pref = prefList.get(0);
            nodesScore.addScore(node, pref.getPreference() * getNodesPreferenceScale());
        }
        
        // scores by preference configured in projects.
        for(SubTask subtask: wc)
        {
            if(!(subtask instanceof Job))
            {
                continue;
            }
            
            Job<?,?> job = (Job<?,?>)subtask;
            BuildPreferenceJobProperty prefs = job.getProperty(BuildPreferenceJobProperty.class);
            
            if(prefs == null)
            {
                continue;
            }
            
            for(BuildPreference pref: prefs.getBuildPreferenceList())
            {
                try
                {
                    Label l = LabelExpression.parseExpression(pref.getLabelExpression());
                    for(Node node: nodesScore.getNodes())
                    {
                        if(!l.contains(node))
                        {
                            continue;
                        }
                        nodesScore.addScore(node, pref.getPreference() * getProjectPreferenceScale());
                    }
                }
                catch(ANTLRException e)
                {
                    LOGGER.log(Level.WARNING, String.format("Skipped invalid label: %s (configured in %s)", pref.getLabelExpression(), job.getFullName()), e);
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
            return Messages.NodePreferenceScoringRule_DisplayName();
        }
    }
}
