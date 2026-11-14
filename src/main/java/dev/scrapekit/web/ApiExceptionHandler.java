package dev.scrapekit.web;

import java.net.URI;

import dev.scrapekit.export.SheetsExporter;
import dev.scrapekit.job.NotFoundException;
import dev.scrapekit.run.JobAlreadyRunningException;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/** RFC 9457 problem responses for the REST API. */
@RestControllerAdvice(basePackages = "dev.scrapekit.web")
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).sorted().reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", msg);
    }

    @ExceptionHandler(JobAlreadyRunningException.class)
    ProblemDetail running(JobAlreadyRunningException e) {
        return problem(HttpStatus.CONFLICT, "job-running", e.getMessage());
    }

    @ExceptionHandler(SheetsExporter.SheetsNotConfiguredException.class)
    ProblemDetail sheetsOff(RuntimeException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "sheets-not-configured", e.getMessage());
    }

    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail upstream(RestClientResponseException e) {
        return problem(HttpStatus.BAD_GATEWAY, "upstream-error", "Google Sheets API returned " + e.getStatusCode().value());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(URI.create("https://github.com/solace202/scrapekit#" + type));
        return p;
    }
}
