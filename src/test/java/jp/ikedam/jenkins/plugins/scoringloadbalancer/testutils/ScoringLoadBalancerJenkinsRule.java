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

import static org.junit.Assert.assertTrue;

import hudson.Extension;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 */
public class ScoringLoadBalancerJenkinsRule extends JenkinsRule {
    public MatrixProject createMatrixProject() throws IOException {
        return super.createProject(MatrixProject.class);
    }

    public DumbSlave createOnlineSlave(String label, int numExecutor) throws Exception {
        DumbSlave slave = null;
        synchronized (jenkins) {
            String nodeName = String.format("slave%d", jenkins.getNodes().size());
            slave = new DumbSlave(
                    nodeName,
                    new File(jenkins.getRootDir(), "agent-work-dirs/" + nodeName).getAbsolutePath(),
                    createComputerLauncher(null));
            slave.setLabelString(label);
            slave.setNumExecutors(numExecutor);
            slave.setRetentionStrategy(RetentionStrategy.NOOP);
            jenkins.addNode(slave);
        }

        waitOnline(slave);

        return slave;
    }

    public void startBuild(AbstractProject<?, ?> p, int timeoutsecs) throws InterruptedException {
        buildStartLock.lock();
        try {
            p.scheduleBuild2(0);
            assertTrue(buildStartCondition.await(timeoutsecs, TimeUnit.SECONDS));
        } finally {
            buildStartLock.unlock();
        }
    }

    private static Lock buildStartLock = new ReentrantLock();
    private static Condition buildStartCondition = buildStartLock.newCondition();

    @Extension
    public static final RunListener<Run<?, ?>> buildStartListener = new RunListener<Run<?, ?>>() {
        @Override
        public void onStarted(hudson.model.Run<?, ?> r, TaskListener listener) {
            buildStartLock.lock();
            try {
                buildStartCondition.signalAll();
            } finally {
                buildStartLock.unlock();
            }
        }
        ;
    };
}
