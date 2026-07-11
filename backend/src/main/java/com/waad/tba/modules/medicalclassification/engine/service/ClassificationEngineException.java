package com.waad.tba.modules.medicalclassification.engine.service;

/**
 * Failure of a classification engine run (configuration, process, timeout,
 * or result parsing). Import jobs catch it and mark the import FAILED —
 * engine failures never affect claims or any other workflow.
 */
public class ClassificationEngineException extends RuntimeException {

    public ClassificationEngineException(String message) {
        super(message);
    }

    public ClassificationEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
