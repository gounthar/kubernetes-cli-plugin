package org.jenkinsci.plugins.kubernetes.cli.helpers;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;

import java.io.Serial;

public class DummyTokenCredentialImpl extends UsernamePasswordCredentialsImpl implements TokenProducer {

    /**
     * 
     */
    @Serial
    private static final long serialVersionUID = 1L;

	public DummyTokenCredentialImpl(CredentialsScope scope, String id, String description, String username,
            String password) {
        super(scope, id, description, username, password);
    }

    @Override
    public String getToken(String serviceAddress, String caCertData, boolean skipTlsVerify) {
        return "faketoken:" + this.getUsername() + ":" + this.getPassword();
    }
}