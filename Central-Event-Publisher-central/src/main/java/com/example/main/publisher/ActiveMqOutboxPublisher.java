package com.example.main.publisher;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import com.example.main.core.OutboxMessagePublisher;

@Component
public class ActiveMqOutboxPublisher implements OutboxMessagePublisher {

    private final JmsTemplate jmsTemplate;
    private final String queueName;

    public ActiveMqOutboxPublisher(JmsTemplate jmsTemplate,
                                   @Value("${outbox.queue-name}") String queueName) {
        this.jmsTemplate = jmsTemplate;
        this.queueName = queueName;
    }

    @Override
    public void publish(String payload) {
        // synchronous send; exception thrown on failure
        jmsTemplate.convertAndSend(queueName, payload);
    }
}
