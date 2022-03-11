package com.davidepetilli.fdl.api;

import com.davidepetilli.fdl.internal.error.ErrorService;
import com.davidepetilli.fdl.internal.interpreter.Interpreter;
import com.davidepetilli.fdl.internal.interpreter.TypeService;
import com.davidepetilli.fdl.internal.interpreter.fhir.FhirInterpreter;
import com.davidepetilli.fdl.internal.interpreter.fhir.FhirReflectiveEngine;
import com.davidepetilli.fdl.internal.interpreter.fhir.StaticAnalyzer;
import com.davidepetilli.fdl.internal.lexer.Lexer;
import com.davidepetilli.fdl.internal.parser.Parser;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * This is a class of utility APIs used to process FDL statements. It does all the dependency injection
 * and runs the executions asynchronously.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class FhirDefinitionLanguageAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(FhirDefinitionLanguageAPI.class);

    private static final String THREAD_NAME = "fdl-thread";
    private static final int POOL_SIZE = 40;

    private final ExecutorService executorService;
    private final TypeService typeService = new TypeService();
    private final FhirReflectiveEngine fhirEngine = new FhirReflectiveEngine(typeService);

    public FhirDefinitionLanguageAPI() {
        executorService = createFdlThreadPool();
    }

    /**
     * This method is intended to be called by SpitFHIR which owns a map of key values, with the keys being
     * FDL expressions with a {@code $} sign as the value placeholder.
     * <p>
     * This traverses all the entries and hydrates the keys with the values, then it runs them through FDL and
     * produces the FHIR Bundle.
     *
     * @param entries a map in the form {@code Map<String, String>} where the key is the FDL expression and the value is
     *                the value to be used.
     * @return {@link CompletableFuture<Bundle>} the resulting Bundle.
     */
    public CompletableFuture<Bundle> processBundle(Map<String, String> entries) {
        var future = new CompletableFuture<Bundle>();
        executorService.submit(() -> {
            StringBuilder source = new StringBuilder();
            for (var entry : entries.entrySet()) {
                var key = entry.getKey();
                var stmt = key.replace("$", String.format("\"%s\"", entry.getValue()));
                stmt += '\n';
                source.append(stmt);
            }

            future.complete(run(source.toString()));
        });
        return future;
    }

    /**
     * This method processes a source string and generates the FHIR Bundle.
     *
     * @param source a string containing a FDL listing.
     * @return {@link CompletableFuture<Bundle>} the resulting Bundle.
     */
    public CompletableFuture<Bundle> processBundle(String source) {
        var future = new CompletableFuture<Bundle>();
        executorService.submit(() -> {
            try {
                future.complete(run(source));
            } catch (FDLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private Bundle run(String source) {
        var startTime = System.nanoTime();

        ErrorService errorService = new ErrorService();
        Lexer lexer = new Lexer(source, errorService);
        var tokens = lexer.scanTokens();
        checkError(errorService);

        Parser parser = new Parser(tokens, errorService);
        var statements = parser.parse();
        checkError(errorService);

        Interpreter<Bundle> interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        StaticAnalyzer staticAnalyzer = new StaticAnalyzer(interpreter);
        staticAnalyzer.resolve(statements);
        var bundle = interpreter.interpret();
        checkError(errorService);

        var endTime = System.nanoTime();
        var elapsed = endTime - startTime;
        LOGGER.info("Bundle generated in {} ms", TimeUnit.NANOSECONDS.toMillis(elapsed));

        return bundle;
    }

    private void checkError(ErrorService errorService) {
        if (errorService.hasError()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("\nStatic errors:\n");
            for (var error : errorService.staticErrors()) {
                errorMessage.append(error.getErrorReport()).append("\n");
            }
            errorMessage.append("\nRuntime errors:\n");
            for (var error : errorService.runtimeErrors()) {
                errorMessage.append(error.getErrorReport()).append("\n");
            }
            throw new FDLException(errorMessage.toString());
        }
    }

    @NotNull
    private ExecutorService createFdlThreadPool() {
        LOGGER.debug("Creating '{}' thread pool.", THREAD_NAME);
        var threadFactory = new ThreadFactoryBuilder().setNameFormat(THREAD_NAME + "-%d").build();
        return new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), threadFactory);
    }
}
