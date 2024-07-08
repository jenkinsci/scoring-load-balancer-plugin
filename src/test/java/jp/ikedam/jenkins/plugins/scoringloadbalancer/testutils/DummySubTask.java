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

import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue.Task;
import hudson.model.queue.SubTask;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class DummySubTask implements SubTask {
    private static Logger LOGGER = Logger.getLogger(DummySubTask.class.getName());
    private String name;
    private Task owner;
    private Object sameNodeConstraint;
    private Node lastBuiltOn;
    private long duration = 5;
    private Label assignedLabel = null;

    public DummySubTask(String name, Task owner, long duration) {
        this.name = name;
        this.owner = owner;
        this.duration = duration;
        this.sameNodeConstraint = this;
    }

    @Override
    public Executable createExecutable() throws IOException {
        return new Executable();
    }

    @Override
    public Task getOwnerTask() {
        return owner;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public Object getSameNodeConstraint() {
        return sameNodeConstraint;
    }

    public void setSameNodeConstraint(Object sameNodeConstraint) {
        this.sameNodeConstraint = sameNodeConstraint;
    }

    @Override
    public Label getAssignedLabel() {
        return assignedLabel;
    }

    public void setAssignedLabel(Label assignedLabel) {
        this.assignedLabel = assignedLabel;
    }

    @Override
    public Node getLastBuiltOn() {
        return lastBuiltOn;
    }

    public void resetLastBuiltOn() {
        lastBuiltOn = null;
    }

    public class Executable implements hudson.model.Queue.Executable {
        @Override
        public DummySubTask getParent() {
            return DummySubTask.this;
        }

        @Override
        public void run() {
            Node node = Computer.currentComputer().getNode();
            LOGGER.info(String.format("%s was run on %s", DummySubTask.this.getDisplayName(), node.getNodeName()));

            try {
                Thread.sleep(DummySubTask.this.duration);
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "", e);
                return;
            }

            DummySubTask.this.lastBuiltOn = node;
        }

        @Override
        public long getEstimatedDuration() {
            return DummySubTask.this.duration;
        }
    }
}
