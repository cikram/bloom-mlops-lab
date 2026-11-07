package com.example.mlops;

import org.mlflow.tracking.ActiveRun;
import org.mlflow.tracking.MlflowClient;
import org.mlflow.tracking.MlflowContext;

import java.util.Map;

public class MlflowTracker {

    private final MlflowContext context; // null when tracking is disabled

    public MlflowTracker(String trackingUri, String experimentName) {
        MlflowContext ctx = null;
        if (trackingUri != null && !trackingUri.isBlank()) {
            try {
                ctx = new MlflowContext(trackingUri);
                MlflowClient client = ctx.getClient();
                var existing = client.getExperimentByName(experimentName);
                String experimentId = existing.isPresent()
                        ? existing.get().getExperimentId()
                        : client.createExperiment(experimentName);
                ctx.setExperimentId(experimentId);
            } catch (Exception e) {
                System.err.println("MLflow tracking disabled: " + e.getMessage());
                ctx = null;
            }
        }
        this.context = ctx;
    }

    public boolean isEnabled() {
        return context != null;
    }

    /** Logs one run: its parameters (inputs) and its metrics (results). */
    public void logRun(String runName, Map<String, String> params, Map<String, Double> metrics) {
        if (context == null) {
            return;
        }
        try {
            ActiveRun run = context.startRun(runName);
            params.forEach((key, value) -> run.logParam(key, value));
            metrics.forEach((key, value) -> run.logMetric(key, value));
            run.endRun();
        } catch (Exception e) {
            System.err.println("MLflow logging failed: " + e.getMessage());
        }
    }
}