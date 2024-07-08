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

package jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences;

import static org.junit.Assert.*;

import hudson.model.FreeStyleProject;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.ScoringLoadBalancerJenkinsRule;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

/**
 *
 */
public class BuildPreferenceJobPropertyTest {
    @Rule
    public ScoringLoadBalancerJenkinsRule j = new ScoringLoadBalancerJenkinsRule();

    @Test
    public void testConfiguration() throws Exception {
        WebClient wc = j.createWebClient();
        FreeStyleProject p = j.createFreeStyleProject();

        assertNull(p.getProperty(BuildPreferenceJobProperty.class));

        // enable BuildPreferenceJobProperty
        {
            HtmlPage configPage = wc.getPage(p, "configure");
            HtmlForm configForm = configPage.getFormByName("config");
            HtmlCheckBoxInput checkbox = configForm.getInputByName(BuildPreferenceJobProperty.PROPERTYNAME);
            assertFalse(checkbox.isChecked());
            checkbox.click();
            assertTrue(checkbox.isChecked());

            j.submit(configForm);
        }

        p = (FreeStyleProject) j.jenkins.getItem(p.getName());
        assertNotNull(p.getProperty(BuildPreferenceJobProperty.class));

        // disable BuildPreferenceJobProperty
        {
            HtmlPage configPage = wc.getPage(p, "configure");
            HtmlForm configForm = configPage.getFormByName("config");
            HtmlCheckBoxInput checkbox = configForm.getInputByName(BuildPreferenceJobProperty.PROPERTYNAME);
            assertTrue(checkbox.isChecked());
            checkbox.click();
            assertFalse(checkbox.isChecked());

            j.submit(configForm);
        }

        p = (FreeStyleProject) j.jenkins.getItem(p.getName());
        assertNull(p.getProperty(BuildPreferenceJobProperty.class));
    }
}
