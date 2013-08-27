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

import static org.junit.Assert.*;

import org.junit.Test;

/**
 *
 */
public class NodeLoadScoringRuleNoJenkinsTest
{
    @Test
    public void testNodeLoadScoringRule()
    {
        {
            NodeLoadScoringRule target = new NodeLoadScoringRule(
                    10,
                    1,
                    -1
            );
            assertEquals(10, target.getScale());
            assertEquals(1, target.getScoreForIdleExecutor());
            assertEquals(-1, target.getScoreForBusyExecutor());
        }
        {
            NodeLoadScoringRule target = new NodeLoadScoringRule(
                    100,
                    3,
                    -5
            );
            assertEquals(100, target.getScale());
            assertEquals(3, target.getScoreForIdleExecutor());
            assertEquals(-5, target.getScoreForBusyExecutor());
        }
    }
}
