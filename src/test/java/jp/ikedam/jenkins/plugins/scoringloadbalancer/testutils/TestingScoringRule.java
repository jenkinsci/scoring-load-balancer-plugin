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

package jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hudson.model.Node;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore;

/**
 * ScoringRule used for Test.
 */
public class TestingScoringRule extends ScoringRule
{
    public List<WorkChunk> calledWorkChunkList = new ArrayList<WorkChunk>();
    public List<NodesScore> nodesScoreList = new ArrayList<NodesScore>();
    public boolean result = true;
    public boolean reset = false;
    public boolean reject = false;
    public Map<Node,Integer> scoreMap = new HashMap<Node,Integer>();
    public Exception e = null;
    
    public void clear()
    {
        calledWorkChunkList.clear();
        nodesScoreList.clear();
    }
    
    /**
     * @param task
     * @param wc
     * @param m
     * @param nodesScore
     * @return
     * @throws Exception
     * @see jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule#updateScores(hudson.model.Queue.Task, hudson.model.queue.MappingWorksheet.WorkChunk, hudson.model.queue.MappingWorksheet.Mapping, jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore)
     */
    @Override
    public synchronized boolean updateScores(Task task, WorkChunk wc, Mapping m,
            NodesScore nodesScore) throws Exception
    {
        if(e != null)
        {
            throw e;
        }
        calledWorkChunkList.add(wc);
        if(!reject)
        {
            for(Node node: nodesScore.getNodes())
            {
                if(reset)
                {
                    nodesScore.resetScore(node);
                }
                if(scoreMap.containsKey(node))
                {
                    nodesScore.addScore(node, scoreMap.get(node));
                }
            }
        }
        else
        {
            nodesScore.markAllInvalid();
        }
        nodesScoreList.add(nodesScore);
        return result;
    }
}
