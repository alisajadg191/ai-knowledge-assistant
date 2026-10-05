package com.sajad.knowledge.api;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.dao.DataAccessException;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class Errors extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail known(ApiException e) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status()), e.getMessage());
        p.setProperty("code", e.code());
        return p;
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request value. Check the question and document identifier.");
    }
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail database(DataAccessException e) {
        LoggerFactory.getLogger(Errors.class).error("Database operation failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Document storage is unavailable. Check PostgreSQL.");
    }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        LoggerFactory.getLogger(Errors.class).error("Request failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not complete. Check the backend logs.");
    }
}
