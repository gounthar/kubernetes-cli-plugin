package org.jenkinsci.plugins.kubernetes.cli.kubeconfig;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.utils.Serialization;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuth;
import org.jenkinsci.plugins.kubernetes.auth.impl.KubernetesAuthKubeconfig;
import org.jenkinsci.plugins.kubernetes.auth.impl.KubernetesAuthUsernamePassword;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import io.fabric8.kubernetes.api.model.ConfigBuilder;

public class KubeConfigWriterBuilderTest {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    FilePath workspace;
    Launcher mockLauncher;
    AbstractBuild<?, ?> build;

    private static String dumpBuilder(ConfigBuilder configBuilder) throws JsonProcessingException {
        return Serialization.asYaml(configBuilder.build());
    }

    private static KubernetesAuthKubeconfig dummyKubeConfigAuth() {
        return new KubernetesAuthKubeconfig(
                """
                ---
                clusters:
                - name: "existing-cluster"
                  cluster:
                    server: https://existing-cluster
                contexts:
                - context:
                    cluster: "existing-cluster"
                    namespace: "existing-namespace"
                  name: "existing-context"
                - context:
                    cluster: "existing-cluster"
                    namespace: "unused-namespace"
                  name: "unused-context"
                current-context: "existing-context"
                users:
                - name: "existing-credential"
                  user:
                    password: "existing-password"
                    username: "existing-user"
                """);
    }

    @Before
    public void init() throws IOException, InterruptedException {
        workspace = new FilePath(tempFolder.newFolder("workspace"));

        mockLauncher = Mockito.mock(Launcher.class);
        VirtualChannel mockChannel = Mockito.mock(VirtualChannel.class);
        when(mockLauncher.getChannel()).thenReturn(mockChannel);

        TaskListener mockListener = Mockito.mock(TaskListener.class);
        when(mockListener.getLogger()).thenReturn(new PrintStream(output, true, "UTF-8"));

        when(mockLauncher.getListener()).thenReturn(mockListener);

        build = Mockito.mock(AbstractBuild.class);
        EnvVars env = new EnvVars();
        when(build.getEnvironment(any())).thenReturn(env);
    }

    @Test
    public void inClusterServiceAccountToken() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                workspace, mockLauncher, build);

        ConfigBuilder configBuilder = configWriter.getConfigBuilderInCluster();
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                contexts:
                - context: {}
                  name: "k8s"
                current-context: "k8s"
                """, configDumpContent);
    }

    @Test
    public void inClusterServiceAccountTokenWithNamespace() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "",
                "",
                "",
                "",
                "",
                "test-namespace",
                false,
                workspace, mockLauncher, build);

        ConfigBuilder configBuilder = configWriter.getConfigBuilderInCluster();
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                contexts:
                - context:
                    namespace: "test-namespace"
                  name: "k8s"
                current-context: "k8s"
                """, configDumpContent);
    }

    @Test
    public void inClusterServiceAccountTokeninClusterServiceAccountTokenWithContextAndNamespace() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "",
                "",
                "",
                "",
                "test-context",
                "test-namespace",
                false,
                workspace, mockLauncher, build);

        ConfigBuilder configBuilder = configWriter.getConfigBuilderInCluster();
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                contexts:
                - context:
                    namespace: "test-namespace"
                  name: "test-context"
                current-context: "test-context"
                """, configDumpContent);
    }

    @Test
    public void basicConfigMinimum() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "",
                "",
                "",
                false,
                workspace, mockLauncher, build);

        KubernetesAuth auth = new KubernetesAuthUsernamePassword("test-user", "test-password");

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                clusters:
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "k8s"
                    user: "test-credential"
                  name: "k8s"
                current-context: "k8s"
                users:
                - name: "test-credential"
                  user:
                    password: "test-password"
                    username: "test-user"
                """, configDumpContent);
    }

    @Test
    public void basicConfigWithCa() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "test-certificate",
                "",
                "",
                "",
                false,
                workspace, mockLauncher, build);

        KubernetesAuth auth = new KubernetesAuthUsernamePassword("test-user", "test-password");

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                clusters:
                - cluster:
                    certificate-authority-data: "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCnRlc3QtY2VydGlmaWNhdGUKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ=="
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "k8s"
                    user: "test-credential"
                  name: "k8s"
                current-context: "k8s"
                users:
                - name: "test-credential"
                  user:
                    password: "test-password"
                    username: "test-user"
                """, configDumpContent);
    }

    @Test
    public void basicConfigWithCluster() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "test-cluster",
                "",
                "",
                false,
                workspace, mockLauncher, build);

        KubernetesAuth auth = new KubernetesAuthUsernamePassword("test-user", "test-password");

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                clusters:
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "test-cluster"
                contexts:
                - context:
                    cluster: "test-cluster"
                    user: "test-credential"
                  name: "k8s"
                current-context: "k8s"
                users:
                - name: "test-credential"
                  user:
                    password: "test-password"
                    username: "test-user"
                """, configDumpContent);
    }

    @Test
    public void basicConfigWithNamespace() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "",
                "",
                "test-namespace",
                false,
                workspace, mockLauncher, build);

        KubernetesAuth auth = new KubernetesAuthUsernamePassword("test-user", "test-password");

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                clusters:
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "k8s"
                    namespace: "test-namespace"
                    user: "test-credential"
                  name: "k8s"
                current-context: "k8s"
                users:
                - name: "test-credential"
                  user:
                    password: "test-password"
                    username: "test-user"
                """, configDumpContent);
    }

    @Test
    public void basicConfigWithContext() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "",
                "test-context",
                "",
                false,
                workspace, mockLauncher, build);

        KubernetesAuth auth = new KubernetesAuthUsernamePassword("test-user", "test-password");

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                clusters:
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "k8s"
                    user: "test-credential"
                  name: "test-context"
                current-context: "test-context"
                users:
                - name: "test-credential"
                  user:
                    password: "test-password"
                    username: "test-user"
                """, configDumpContent);
    }

    @Test
    public void kubeConfigMinimum() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "",
                "test-credential",
                "",
                "",
                "",
                "",
                false,
                workspace, mockLauncher, build);

        KubernetesAuthKubeconfig auth = dummyKubeConfigAuth();

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        // asserts that:
        // * kubeconfig is simply imported
        assertEquals("""
                ---
                clusters:
                - cluster:
                    server: "https://existing-cluster"
                  name: "existing-cluster"
                contexts:
                - context:
                    cluster: "existing-cluster"
                    namespace: "existing-namespace"
                  name: "existing-context"
                - context:
                    cluster: "existing-cluster"
                    namespace: "unused-namespace"
                  name: "unused-context"
                current-context: "existing-context"
                users:
                - name: "existing-credential"
                  user:
                    password: "existing-password"
                    username: "existing-user"
                """, configDumpContent);
    }

    @Test
    public void kubeConfigWithCaCertificate() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "test-certificate",
                "",
                "",
                "",
                false,
                workspace, mockLauncher, build);

        KubernetesAuthKubeconfig auth = dummyKubeConfigAuth();

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        // asserts that:
        // * kubeconfig is imported
        // * a new cluster is created with the CA and serverURL
        // * the cluster is used by the existing context
        assertEquals("""
                ---
                clusters:
                - cluster:
                    server: "https://existing-cluster"
                  name: "existing-cluster"
                - cluster:
                    certificate-authority-data: "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCnRlc3QtY2VydGlmaWNhdGUKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ=="
                    insecure-skip-tls-verify: false
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "k8s"
                    namespace: "existing-namespace"
                  name: "existing-context"
                - context:
                    cluster: "existing-cluster"
                    namespace: "unused-namespace"
                  name: "unused-context"
                current-context: "existing-context"
                users:
                - name: "existing-credential"
                  user:
                    password: "existing-password"
                    username: "existing-user"
                """, configDumpContent);
    }

    @Test
    public void kubeConfigWithNamespace() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "",
                "",
                "new-namespace",
                false,
                workspace, mockLauncher, build);

        KubernetesAuthKubeconfig auth = dummyKubeConfigAuth();

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        // asserts that:
        // * kubeconfig is imported
        // * a new cluster is created with the serverURL
        // * the cluster is used by the existing context
        // * the namespace is set for the existing context
        assertEquals("""
                ---
                clusters:
                - cluster:
                    server: "https://existing-cluster"
                  name: "existing-cluster"
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "k8s"
                    namespace: "new-namespace"
                  name: "existing-context"
                - context:
                    cluster: "existing-cluster"
                    namespace: "unused-namespace"
                  name: "unused-context"
                current-context: "existing-context"
                users:
                - name: "existing-credential"
                  user:
                    password: "existing-password"
                    username: "existing-user"
                """, configDumpContent);
    }

    @Test
    public void kubeConfigWithClusterName() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "new-cluster",
                "",
                "",
                false,
                workspace, mockLauncher, build);

        KubernetesAuthKubeconfig auth = dummyKubeConfigAuth();

        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        // asserts that:
        // * kubeconfig is imported
        // * a new cluster is created with the serverURL and the clusterName
        // * the cluster is used by the existing context
        assertEquals("""
                ---
                clusters:
                - cluster:
                    server: "https://existing-cluster"
                  name: "existing-cluster"
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "new-cluster"
                contexts:
                - context:
                    cluster: "new-cluster"
                    namespace: "existing-namespace"
                  name: "existing-context"
                - context:
                    cluster: "existing-cluster"
                    namespace: "unused-namespace"
                  name: "unused-context"
                current-context: "existing-context"
                users:
                - name: "existing-credential"
                  user:
                    password: "existing-password"
                    username: "existing-user"
                """, configDumpContent);
    }

    @Test
    public void kubeConfigWithContextSwitch() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "",
                "unused-context",
                "new-namespace",
                false,
                workspace, mockLauncher, build);

        KubernetesAuthKubeconfig auth = dummyKubeConfigAuth();
        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        // asserts that:
        // * kubeconfig is imported
        // * context is switched
        // * a new cluster is created with the serverURL
        // * the cluster is used by the context we switched to
        assertEquals("""
                ---
                clusters:
                - cluster:
                    server: "https://existing-cluster"
                  name: "existing-cluster"
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "existing-cluster"
                    namespace: "existing-namespace"
                  name: "existing-context"
                - context:
                    cluster: "k8s"
                    namespace: "new-namespace"
                  name: "unused-context"
                current-context: "unused-context"
                users:
                - name: "existing-credential"
                  user:
                    password: "existing-password"
                    username: "existing-user"
                """, configDumpContent);
    }

    @Test
    public void kubeConfigWithSwitchToNonExistentContext() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "",
                "non-existent-context",
                "new-namespace",
                false,
                workspace, mockLauncher, build);

        KubernetesAuthKubeconfig auth = dummyKubeConfigAuth();
        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        // asserts that:
        // * kubeconfig is imported
        // * non-existent context is created
        // * a new cluster is created with the serverURL
        // * the cluster is used by the context we created
        // * the namespace is set in the context we created
        assertEquals("""
                ---
                clusters:
                - cluster:
                    server: "https://existing-cluster"
                  name: "existing-cluster"
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "existing-cluster"
                    namespace: "existing-namespace"
                  name: "existing-context"
                - context:
                    cluster: "existing-cluster"
                    namespace: "unused-namespace"
                  name: "unused-context"
                - context:
                    cluster: "k8s"
                    namespace: "new-namespace"
                  name: "non-existent-context"
                current-context: "non-existent-context"
                users:
                - name: "existing-credential"
                  user:
                    password: "existing-password"
                    username: "existing-user"
                """, configDumpContent);
        assertEquals("[kubernetes-cli] context 'non-existent-context' doesn't exist in kubeconfig",
                output.toString());
    }

    @Test
    public void kubeConfigWithExistingContext() throws Exception {
        KubeConfigWriter configWriter = new KubeConfigWriter(
                "https://localhost:6443",
                "test-credential",
                "",
                "",
                "existing-context",
                "",
                false,
                workspace, mockLauncher, build);

        KubernetesAuthKubeconfig auth = dummyKubeConfigAuth();
        ConfigBuilder configBuilder = configWriter.getConfigBuilderWithAuth("test-credential", auth);
        String configDumpContent = dumpBuilder(configBuilder);

        assertEquals("""
                ---
                clusters:
                - cluster:
                    server: "https://existing-cluster"
                  name: "existing-cluster"
                - cluster:
                    insecure-skip-tls-verify: true
                    server: "https://localhost:6443"
                  name: "k8s"
                contexts:
                - context:
                    cluster: "k8s"
                    namespace: "existing-namespace"
                  name: "existing-context"
                - context:
                    cluster: "existing-cluster"
                    namespace: "unused-namespace"
                  name: "unused-context"
                current-context: "existing-context"
                users:
                - name: "existing-credential"
                  user:
                    password: "existing-password"
                    username: "existing-user"
                """, configDumpContent);
    }
}
