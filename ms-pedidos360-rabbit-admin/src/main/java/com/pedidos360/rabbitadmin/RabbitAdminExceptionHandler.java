package com.pedidos360.rabbitadmin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class RabbitAdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminExceptionHandler.class);

    @ExceptionHandler(AmqpConnectException.class)
    public ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ no está disponible");
    }

    /** Declaraciones incompatibles (p. ej. misma cola con otros argumentos) o recursos inexistentes. */
    @ExceptionHandler(AmqpException.class)
    public ProblemDetail rejected(AmqpException error) {
        log.warn("RabbitMQ rechazó la operación", error);
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "RabbitMQ rechazó la operación: " + (cause != null ? cause.getMessage() : error.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail managementError(RestClientException error) {
        log.warn("Falló la API de administración de RabbitMQ", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "La API de administración de RabbitMQ no respondió");
    }
}
