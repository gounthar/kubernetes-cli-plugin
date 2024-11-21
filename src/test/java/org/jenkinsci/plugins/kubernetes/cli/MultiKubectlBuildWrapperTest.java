package org.jenkinsci.plugins.kubernetes.cli;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;

import org.jenkinsci.plugins.kubernetes.cli.helpers.DummyCredentials;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleProject;

/**
 * @author Max Laverse
 */
public class MultiKubectlBuildWrapperTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testConfigurationPersistedOnSave() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
                DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        KubectlCredential kc = new KubectlCredential();
        kc.credentialsId = "test-credentials";
        List<KubectlCredential> l = new ArrayList<KubectlCredential>();
        l.add(kc);
        MultiKubectlBuildWrapper bw = new MultiKubectlBuildWrapper(l, false);
        p.getBuildWrappersList().add(bw);

        assertEquals("""
                <?xml version='1.1' encoding='UTF-8'?>
                <project>
                  <keepDependencies>false</keepDependencies>
                  <properties/>
                  <scm class="hudson.scm.NullSCM"/>
                  <canRoam>false</canRoam>
                  <disabled>false</disabled>
                  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
                  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
                  <triggers/>
                  <concurrentBuild>false</concurrentBuild>
                  <builders/>
                  <publishers/>
                  <buildWrappers>
                    <org.jenkinsci.plugins.kubernetes.cli.MultiKubectlBuildWrapper>
                      <kubectlCredentials>
                        <org.jenkinsci.plugins.kubernetes.cli.KubectlCredential>
                          <credentialsId>test-credentials</credentialsId>
                        </org.jenkinsci.plugins.kubernetes.cli.KubectlCredential>
                      </kubectlCredentials>
                      <restrictKubeConfigAccess>false</restrictKubeConfigAccess>
                    </org.jenkinsci.plugins.kubernetes.cli.MultiKubectlBuildWrapper>
                  </buildWrappers>
                </project>""", p.getConfigFile().asString());
    }
}
