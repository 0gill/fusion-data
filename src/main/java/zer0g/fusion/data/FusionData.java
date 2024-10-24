package zer0g.fusion.data;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Marker interface implemented by all the different fusion data-types.
 */
public interface FusionData
{
    FusionDataType type();

    default void saveTo(File file) throws IOException {
        try (var writer = new OutputStreamWriter(Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE_NEW))) {
            writeTo(writer);
        }
    }

    void writeTo(Writer writer) throws IOException;
}
