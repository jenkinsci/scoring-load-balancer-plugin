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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.LoadBalancer;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet;
import hudson.model.queue.MappingWorksheet.ExecutorChunk;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;

/**
 * @author yasuke
 *
 */
public class ScoringLoadBalancer extends LoadBalancer
{
    private static final Logger LOGGER = Logger.getLogger(ScoringLoadBalancer.class.getName());
    private static ScoringLoadBalancer CURRENT_BALANCER;
    
    @Initializer(after=InitMilestone.PLUGINS_STARTED)
    public static void onPluginStart()
    {
        LOGGER.info("Replaced LoadBalancer to ScoringLoadBalancer");
        Queue q = Jenkins.getInstance().getQueue();
        LoadBalancer fallback = q.getLoadBalancer();
        CURRENT_BALANCER = new ScoringLoadBalancer(fallback);
        q.setLoadBalancer(CURRENT_BALANCER);
    }
    
    private LoadBalancer fallback;
    
    /**
     * @return the fallback
     */
    public LoadBalancer getFallback()
    {
        return fallback;
    }
    
    private List<NodeScoreKeeper> nodeScoreKeeperList = Arrays.asList((NodeScoreKeeper)new BuildResultNodeScoreKeeper());
    
    /**
     * @return the nodeScoreKeeperList
     */
    public List<NodeScoreKeeper> getNodeScoreKeeperList()
    {
        return nodeScoreKeeperList;
    }
    
    /**
     * @param nodeScoreKeeperList the nodeScoreKeeperList to set
     */
    protected void setNodeScoreKeeperList(List<NodeScoreKeeper> nodeScoreKeeperList)
    {
        this.nodeScoreKeeperList = nodeScoreKeeperList;
    }

    public ScoringLoadBalancer(LoadBalancer fallback)
    {
        this.fallback = fallback;
    }
    
    /**
     * (non-Javadoc)
     * @see hudson.model.LoadBalancer#map(hudson.model.Queue.Task, hudson.model.queue.MappingWorksheet)
     */
    @Override
    public Mapping map(Task task, MappingWorksheet worksheet)
    {
        Mapping m = worksheet.new Mapping();
        
        if (assignGreedily(m, task, worksheet, 0))
        {
            return m;
        }
        else
        {
            return fallback.map(task, worksheet);
        }
    }

    private boolean assignGreedily(Mapping m, Task task,
            MappingWorksheet worksheet, int i)
    {
        if(i >= worksheet.works.size())
        {
            return m.isCompletelyValid();
        }
        
        WorkChunk wc = worksheet.works(i);
        
        // Initialize nodes to scores map.
        List<ExecutorChunk> executors = new ArrayList<ExecutorChunk>(wc.applicableExecutorChunks());
        final Map<Node,Integer> nodeScoreMap = new HashMap<Node,Integer>();
        for(ExecutorChunk ec: executors)
        {
            nodeScoreMap.put(ec.computer.getNode(), 0);
        }
        
        // Score nodes
        for(NodeScoreKeeper scoreKeeper: getNodeScoreKeeperList())
        {
            scoreKeeper.updateScores(task, wc, m, nodeScoreMap);
        }
        
        Collections.shuffle(executors);
        Collections.sort(executors, new Comparator<ExecutorChunk>(){
            @Override
            public int compare(ExecutorChunk o1, ExecutorChunk o2)
            {
                return nodeScoreMap.get(o2.computer.getNode()) - nodeScoreMap.get(o1.computer.getNode());
            }
        });
        
        LOGGER.info("Scoring:");
        for(ExecutorChunk ec: executors)
        {
            LOGGER.info(String.format("%s: %d", ec.computer.getNode().getNodeName(), nodeScoreMap.get(ec.computer.getNode())));
        }
        
        for(ExecutorChunk ec: executors)
        {
            m.assign(i, ec);
            if(m.isPartiallyValid() && assignGreedily(m, task, worksheet, i+1))
            {
                return true;
            }
        }
        
        m.assign(i,null);
        return false;
    }
    
}
