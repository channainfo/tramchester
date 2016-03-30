package com.tramchester;

import com.tramchester.config.AppConfiguration;
import io.dropwizard.Application;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;

public class AcceptanceTestRun extends DropwizardAppRule<AppConfiguration> {

    private String serverUrl;

    public AcceptanceTestRun(Class<? extends Application<AppConfiguration>> applicationClass, String configPath, ConfigOverride... configOverrides) {
        super(applicationClass, configPath, configOverrides);
        serverUrl = System.getenv("SERVER_URL");
    }

    @Override
    protected void before() {
        if (localRun()) {
            super.before();
        }
    }

    private boolean localRun() {
        return serverUrl==null;
    }

    @Override
    protected void after() {
        if (localRun()) {
            super.after();
        }
    }

    public String getUrl() {
        if (localRun()) {
            return "http://localhost:"+getLocalPort();
        }
        return serverUrl;
    }
}