package com.pedidos360.kafkaadmin;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class KafkaAdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminExceptionHandler.class);

    @ExceptionHandler({
            ConfigException.class, InvalidConfigurationException.class, InvalidPartitionsException.class,
            InvalidReplicationFactorException.class, InvalidRequestException.class, InvalidTopicException.class
    })
    public ProblemDetail invalidRequest(KafkaException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(UnknownTopicOrPartitionException.class)
    public ProblemDetail topicNotFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "El tópico no existe");
    }

    @ExceptionHandler(GroupIdNotFoundException.class)
    public ProblemDetail groupNotFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "El consumer group no existe");
    }

    @ExceptionHandler({GroupNotEmptyException.class, UnknownMemberIdException.class})
    public ProblemDetail groupNotEmpty() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "El consumer group tiene consumidores activos: detenlos antes de esta operación");
    }

    @ExceptionHandler(TopicExistsException.class)
    public ProblemDetail topicExists() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El tópico ya existe");
    }

    @ExceptionHandler(AuthorizationException.class)
    public ProblemDetail forbidden() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Kafka rechazó la operación por permisos");
    }

    @ExceptionHandler(TimeoutException.class)
    public ProblemDetail timeout() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "Kafka no respondió a tiempo");
    }

    @ExceptionHandler(KafkaException.class)
    public ProblemDetail kafkaError(KafkaException error) {
        log.warn("Falló una operación de administración de Kafka", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Kafka rechazó la operación");
    }
}
