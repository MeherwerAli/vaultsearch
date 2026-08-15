package dev.meherwerali.vaultsearch.api;

import dev.meherwerali.vaultsearch.search.NoteNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    ProblemDetail invalidInput(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(NoteNotFoundException.class)
    ProblemDetail noteNotFound(NoteNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested note does not exist");
        problem.setTitle("Note not found");
        return problem;
    }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class,
            org.springframework.data.elasticsearch.UncategorizedElasticsearchException.class})
    ProblemDetail dependencyUnavailable(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "A required search dependency is unavailable");
        problem.setTitle("Dependency unavailable");
        return problem;
    }
}
