package com.pedidos360.rabbitadmin;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RabbitAdminController.class, properties = AdminJwtTestConfig.API_CLIENT_ID)
@Import({SecurityConfig.class, AdminJwtTestConfig.class})
class RabbitAdminControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AmqpAdmin admin;

    @MockBean
    private RestClient management;

    @Test
    void createsQuorumQueueWithDeadLetterExchange() throws Exception {
        mvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"pruebas.cola","type":"quorum","deadLetterExchange":"pruebas.dlx","deadLetterRoutingKey":"dlq"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/queues/pruebas.cola"));

        ArgumentCaptor<Queue> queue = ArgumentCaptor.forClass(Queue.class);
        verify(admin).declareQueue(queue.capture());
        assertEquals("pruebas.cola", queue.getValue().getName());
        assertEquals("quorum", queue.getValue().getArguments().get("x-queue-type"));
        assertEquals("pruebas.dlx", queue.getValue().getArguments().get("x-dead-letter-exchange"));
    }

    @Test
    void createsAndDeletesExchangesAndBindings() throws Exception {
        mvc.perform(post("/api/exchanges").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"pruebas.ex\",\"type\":\"topic\"}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<Exchange> exchange = ArgumentCaptor.forClass(Exchange.class);
        verify(admin).declareExchange(exchange.capture());
        assertEquals("topic", exchange.getValue().getType());

        mvc.perform(post("/api/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"pruebas.ex\",\"queue\":\"pruebas.cola\",\"routingKey\":\"order.*\"}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(admin).declareBinding(binding.capture());
        assertEquals("order.*", binding.getValue().getRoutingKey());

        mvc.perform(delete("/api/bindings").param("exchange", "pruebas.ex").param("queue", "pruebas.cola")
                        .param("routingKey", "order.*"))
                .andExpect(status().isNoContent());
        verify(admin).removeBinding(any(Binding.class));

        mvc.perform(delete("/api/exchanges/pruebas.ex")).andExpect(status().isNoContent());
        verify(admin).deleteExchange("pruebas.ex");
        mvc.perform(delete("/api/queues/pruebas.cola")).andExpect(status().isNoContent());
        verify(admin).deleteQueue("pruebas.cola");
    }

    @Test
    void rejectsInvalidRequestsAndReportsBrokerConflicts() throws Exception {
        mvc.perform(post("/api/exchanges").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"type\":\"inventado\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"con espacios\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(admin);

        doThrow(new AmqpIOException(new IOException("PRECONDITION_FAILED"))).when(admin).declareQueue(any());
        mvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"existente\"}"))
                .andExpect(status().isConflict());
    }
}
