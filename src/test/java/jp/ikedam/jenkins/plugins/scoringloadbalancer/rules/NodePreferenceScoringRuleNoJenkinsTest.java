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
public class NodePreferenceScoringRuleNoJenkinsTest {
    @Test
    public void testNodePreferenceScoringRule() {
        {
            NodePreferenceScoringRule target = new NodePreferenceScoringRule(1, 10);
            assertEquals(1, target.getNodesPreferenceScale());
            assertEquals(10, target.getProjectPreferenceScale());
        }
        {
            NodePreferenceScoringRule target = new NodePreferenceScoringRule(3, 2);
            assertEquals(3, target.getNodesPreferenceScale());
            assertEquals(2, target.getProjectPreferenceScale());
        }
    }
}
