package com.parvnigam.careerbridge;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request failed":e.getReason()));}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {return ResponseEntity.badRequest().body(Map.of("message",e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).findFirst().orElse("Invalid request")));}
    @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class})
    ResponseEntity<?> invalid(Exception e) {return ResponseEntity.badRequest().body(Map.of("message","Invalid input. Check the submitted values."));}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception e) {return ResponseEntity.status(409).body(Map.of("message","This record already exists or conflicts with another update."));}
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<?> stale(Exception e) {return ResponseEntity.status(409).body(Map.of("message","The record changed. Refresh and try again."));}
}
