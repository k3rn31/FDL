package com.davidepetilli.fdl.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a Maven plugin and is hooked at 'generate-sources' phase. Based on some YAML files defining the grammar
 * of FDL, it generates the classes to store the Abstract Syntax Tree (AST), which are tedious to write by hand.
 * This is known as 'metaprogramming'.
 * <p>
 * TODO: Arguably, this is not a piece of art and should be refactored/made more robust.
 *       Most noticeably, the package name and the packages of lexer classes such as {@code Token} should
 *       be handled dynamically.
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE)
public class AstGenerator extends AbstractMojo {

    private Log logger;

    @Parameter(defaultValue = "${basedir}/src/main/ast")
    private File sourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources")
    private File outputDirectory;

    @Parameter(defaultValue = "com.davidepetilli.fdl")
    private String packageName;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        logger = getLog();

        if (!sourceDirectory.isDirectory()) {
            logger.info("No FDL AST definitions to compile in " + sourceDirectory.getAbsolutePath());
            return;
        }

        try {
            var astDefinitions = new ArrayList<AstDefinition>();
            String[] extensions = new String[]{"yaml", "yml"};
            String[] filesNames = FileUtils.getFilesFromExtension(sourceDirectory.getPath(), extensions);

            for (var fileName : filesNames) {
                logger.info("Generating from: " + fileName);
                var file = new FileInputStream(fileName);
                Yaml yaml = new Yaml(new Constructor(AstDefinition.class));
                var astIterable = yaml.loadAll(file);
                for (var ast : astIterable) {
                    astDefinitions.add((AstDefinition) ast);
                }
            }

            for (var astDefinition : astDefinitions) {
                defineAst(astDefinition);
            }

        } catch (IOException exception) {
            throw new MojoExecutionException(exception.getMessage());
        }
    }

    private void defineAst(AstDefinition astDefinition) throws IOException {
        var packagePath = packageName.replace('.', '/');
        var absolutePath = new File(outputDirectory.getPath() + "/" + packagePath);
        if (!absolutePath.exists()) {
            absolutePath.mkdirs();
        }
        var sourceFilePath = absolutePath.getPath() + "/" + astDefinition.baseName + ".java";
        logger.info("Generating sources: " + sourceFilePath);

        PrintWriter writer = new PrintWriter(sourceFilePath, StandardCharsets.UTF_8);

        writer.println("package " + packageName + ";");
        writer.println();
        writer.println("import " + packageName + ".internal.lexer.Token;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("public abstract class " + astDefinition.baseName + " {");

        defineVisitor(writer, astDefinition.baseName, astDefinition.types);

        // The base accept() method.
        writer.println("  public abstract <T> T accept(Visitor<T> visitor);");
        writer.println();

        for (var type : astDefinition.types) {
            String className = type.name;
            List<AstDefinition.Type.Field> fields = type.values;
            defineType(writer, astDefinition.baseName, className, fields);
        }

        writer.println("}");
        writer.close();
    }

    private void defineVisitor(PrintWriter writer, String baseName, List<AstDefinition.Type> types) {
        writer.println("  public interface Visitor<T> {");

        for (var type : types) {
            String typeName = type.name;
            writer.println("    T visit" + typeName + baseName + "(" + typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("  }");
        writer.println();
    }

    private void defineType(PrintWriter writer, String baseName, String className, List<AstDefinition.Type.Field> fields) {
        writer.println("  static public class " + className + " extends " + baseName + " {");

        // Fields.
        for (AstDefinition.Type.Field field : fields) {
            writer.println("    public final " + field.getType() + " " + field.getName() + ";");
        }
        writer.println();

        // Constructor.
        var builder = new StringBuilder();
        for (AstDefinition.Type.Field field : fields) {
            builder.append(field.getType()).append(" ").append(field.getName()).append(", ");
        }
        var fieldList = builder.substring(0, builder.length() - 2);

        writer.println("    public " + className + "(" + fieldList + ") {");
        // Store parameters in fields.
        for (AstDefinition.Type.Field field : fields) {
            writer.println("      this." + field.getName() + " = " + field.getName() + ";");
        }
        writer.println("    }");

        // Visitor pattern.
        writer.println();
        writer.println("    @Override");
        writer.println("    public <T> T accept(Visitor<T> visitor) {");
        writer.println("      return visitor.visit" + className + baseName + "(this);");
        writer.println("    }");

        writer.println("  }");
        writer.println();
    }
}
