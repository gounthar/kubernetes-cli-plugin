package org.jenkinsci.plugins.kubernetes.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;

import org.jenkinsci.plugins.envinject.EnvInjectBuildWrapper;
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo;
import org.jenkinsci.plugins.kubernetes.cli.helpers.DummyCredentials;
import org.jenkinsci.plugins.matrixauth.AuthorizationType;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

/**
 * @author Max Laverse
 */
public class KubectlBuildWrapperTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testEnvVariablePresent() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
                DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        KubectlBuildWrapper bw = new KubectlBuildWrapper();
        bw.credentialsId = "test-credentials";
        p.getBuildWrappersList().add(bw);

        if (isPlatformWindow()) {
            p.getBuildersList().add(new BatchFile("SET K"));
        } else {
            p.getBuildersList().add(new Shell("env"));
        }

        FreeStyleBuild b = p.scheduleBuild2(0).waitForStart();

        assertNotNull(b);
        r.assertBuildStatus(Result.SUCCESS, r.waitForCompletion(b));
        r.assertLogContains("KUBECONFIG=", b);
    }

    @Test
    public void testEnvInterpolation() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
                DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        EnvInjectJobPropertyInfo info = new EnvInjectJobPropertyInfo(null,
                "NAMESPACE=testNamespace\nSERVER_URL=http://my-server\nCLUSTER_NAME=testClusterName\nCONTEXT_NAME=testContext",
                null,
                null,
                true,
                null);
        p.getBuildWrappersList().add(new EnvInjectBuildWrapper(info));

        KubectlBuildWrapper bw = new KubectlBuildWrapper();
        bw.credentialsId = "test-credentials";
        bw.serverUrl = "${SERVER_URL}";
        bw.clusterName = "${CLUSTER_NAME}";
        bw.contextName = "${CONTEXT_NAME}";
        bw.namespace = "${NAMESPACE}";
        p.getBuildWrappersList().add(bw);

        if (isPlatformWindow()) {
            p.getBuildersList().add(new BatchFile("type \"%KUBECONFIG%\""));
        } else {
            p.getBuildersList().add(new Shell("cat \"$KUBECONFIG\""));
        }

        FreeStyleBuild b = p.scheduleBuild2(0).waitForStart();

        assertNotNull(b);
        r.assertBuildStatus(Result.SUCCESS, r.waitForCompletion(b));
        r.assertLogContains("server: \"http://my-server\"", b);
        r.assertLogContains("name: \"testClusterName\"", b);
        r.assertLogContains("current-context: \"testContext\"", b);
        r.assertLogContains("namespace: \"testNamespace\"", b);
    }

    @Test
    public void testKubeConfigDisposed() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
                DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        KubectlBuildWrapper bw = new KubectlBuildWrapper();
        bw.credentialsId = "test-credentials";
        p.getBuildWrappersList().add(bw);

        FreeStyleBuild b = p.scheduleBuild2(0).waitForStart();

        assertNotNull(b);
        r.assertBuildStatus(Result.SUCCESS, r.waitForCompletion(b));
        r.assertLogContains("kubectl configuration cleaned up", b);
    }

    @Test
    public void testListedCredentials() throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        store.addCredentials(Domain.global(), DummyCredentials.usernamePasswordCredential("1"));
        store.addCredentials(Domain.global(), DummyCredentials.secretCredential("2"));
        store.addCredentials(Domain.global(), DummyCredentials.fileCredential("3"));
        store.addCredentials(Domain.global(), DummyCredentials.certificateCredential("4"));
        store.addCredentials(Domain.global(), DummyCredentials.tokenCredential("5"));

        KubectlBuildWrapper.DescriptorImpl d = new KubectlBuildWrapper.DescriptorImpl();
        FreeStyleProject p = r.createFreeStyleProject();
        ListBoxModel s = d.doFillCredentialsIdItems(p.asItem(), "", "1");

        assertEquals(6, s.size());
    }

    @Test
    public void testListingCredentialsWithoutAncestorAndMissingPermissions() throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        store.addCredentials(Domain.global(), DummyCredentials.usernamePasswordCredential("1"));
        store.addCredentials(Domain.global(), DummyCredentials.secretCredential("2"));
        store.addCredentials(Domain.global(), DummyCredentials.fileCredential("3"));
        KubectlBuildWrapper.DescriptorImpl d = new KubectlBuildWrapper.DescriptorImpl();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy as = new ProjectMatrixAuthorizationStrategy();
        as.add(Jenkins.READ, new PermissionEntry(AuthorizationType.EITHER, "user-not-enough-permissions"));
        r.jenkins.setAuthorizationStrategy(as);

        try (ACLContext unused = ACL.as2(User.get("user-not-enough-permissions", true, null).impersonate2())) {
            ListBoxModel options = d.doFillCredentialsIdItems(null, "", "1");
            assertEquals("- current -", options.get(0).name);
            assertEquals(1, options.size());
        }
    }

    @Test
    public void testConfigurationPersistedOnSave() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
                DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        KubectlBuildWrapper bw = new KubectlBuildWrapper();
        bw.credentialsId = "test-credentials";
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
                    <org.jenkinsci.plugins.kubernetes.cli.KubectlBuildWrapper>
                      <credentialsId>test-credentials</credentialsId>
                    </org.jenkinsci.plugins.kubernetes.cli.KubectlBuildWrapper>
                  </buildWrappers>
                </project>""", p.getConfigFile().asString());
    }

    private boolean isPlatformWindow() {
        String OS = System.getProperty("os.name");
        if (OS != null && OS.startsWith("Windows")) {
            return true;
        }
        return false;
    }
}
