package com.davidepetilli.fdl;

import ca.uhn.fhir.context.FhirContext;
import com.davidepetilli.fdl.api.FhirDefinitionLanguageAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FDL {
    private static final Logger LOGGER = LoggerFactory.getLogger(FDL.class);

    private static final FhirDefinitionLanguageAPI FDL_API = new FhirDefinitionLanguageAPI();

    public static void main(String[] args) throws IOException {
        LOGGER.info("FDL (FHIR Definition Language) - Starting Up");
        if (args.length > 1) {
            System.out.println("Usage: fdl [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        }
    }

    private static void runFile(String path) throws IOException {
        FhirContext ctx = FhirContext.forR4();

        byte[] bytes = Files.readAllBytes(Paths.get(path));

        LOGGER.info("Processing file: {}", path);

        var future = FDL_API.processBundle(new String(bytes, Charset.defaultCharset()));
        future.thenAccept(bundle -> {
            var parser = ctx.newJsonParser();
            parser.setPrettyPrint(true);
            var serialized = parser.encodeResourceToString(bundle);
            LOGGER.info("Generated Bundle:\n{}", serialized);
            System.exit(0);
        }).exceptionally(throwable -> {
            LOGGER.error(throwable.getMessage());
            System.exit(-1);
            return null;
        });
    }
}
