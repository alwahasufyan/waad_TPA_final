package com.waad.tba.modules.medicalclassification.engine.service;

import com.waad.tba.modules.medicalclassification.engine.dto.ClassificationRequest;
import com.waad.tba.modules.medicalclassification.engine.dto.ClassificationResult;

/**
 * Transport-agnostic contract to the Medical Classification Engine.
 *
 * The JSON contract is the architecture; the transport is a detail (A1):
 * Phase 1 = {@link CliClassificationEngineClient} (ProcessBuilder → Python).
 * A future HTTP/sidecar implementation is a drop-in swap — callers never change.
 */
public interface ClassificationEngineClient {

    /**
     * Runs one classification pass over the given input file and returns the
     * parsed result. Synchronous and potentially long-running — callers are
     * expected to invoke it from an async import job, never from a request thread.
     *
     * @throws ClassificationEngineException on configuration, process, or parse failure
     */
    ClassificationResult classify(ClassificationRequest request);

    /**
     * Cheap availability probe (configured paths exist / interpreter responds).
     * Never throws; returns a human-readable problem description or null when healthy.
     */
    String healthProblem();
}
