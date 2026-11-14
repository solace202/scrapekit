package dev.scrapekit.run;

public class JobAlreadyRunningException extends RuntimeException {

    public JobAlreadyRunningException(long jobId) {
        super("Job " + jobId + " is already running");
    }
}
