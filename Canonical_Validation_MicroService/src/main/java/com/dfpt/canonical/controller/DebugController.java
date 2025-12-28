package com.dfpt.canonical.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/debug")
public class DebugController {
    private static final Logger logger = LoggerFactory.getLogger(DebugController.class);

    @Autowired
    @Qualifier("mqExecutor")
    private Executor mqExecutor;

    @Autowired
    @Qualifier("s3Executor")
    private Executor s3Executor;

    @GetMapping("/mq-thread")
    public String mqThread() throws Exception {
        CompletableFuture<String> f = new CompletableFuture<>();
        mqExecutor.execute(() -> {
            String name = Thread.currentThread().getName();
            logger.info("Debug: mqExecutor running on thread {}", name);
            f.complete(name);
        });
        return f.get(3, TimeUnit.SECONDS);
    }

    @GetMapping("/s3-thread")
    public String s3Thread() throws Exception {
        CompletableFuture<String> f = new CompletableFuture<>();
        s3Executor.execute(() -> {
            String name = Thread.currentThread().getName();
            logger.info("Debug: s3Executor running on thread {}", name);
            f.complete(name);
        });
        return f.get(3, TimeUnit.SECONDS);
    }
}