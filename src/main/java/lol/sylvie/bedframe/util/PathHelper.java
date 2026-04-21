package lol.sylvie.bedframe.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathHelper {
    public static Path createDirectoryOrThrow(Path path) {
        try {
            return Files.notExists(path) ? Files.createDirectory(path) : path;
        } catch (IOException e) { throw new RuntimeException(e); }
    }
}
