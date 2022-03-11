package com.davidepetilli.fdl.integration;

import com.davidepetilli.fdl.internal.error.ErrorService;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.fail;

public abstract class BaseIntegrationTest {

    protected String getSourceFromFile(String fileName) {
        try {
            URL resource = getClass().getClassLoader().getResource(fileName);
            assert resource != null;
            var bytes = Files.readAllBytes(Paths.get(resource.toURI()));
            return new String(bytes);
        } catch (IOException | URISyntaxException e) {
            fail(e.getMessage());
        }
        return null;
    }

    protected void assertError(ErrorService errorService) {
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
            fail(errorMessage.toString());
        }
    }
}
