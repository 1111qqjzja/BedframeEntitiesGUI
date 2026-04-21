package lol.sylvie.bedframe.util;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipHelper {
    // https://stackoverflow.com/questions/57997257/how-can-i-zip-a-complete-directory-with-all-subfolders-in-java
    public static void zipFolder(Path source, File destination) {
        try {
            var parent = Path.of(destination.getParent());
            if (!Files.exists(parent)) {
                Files.createDirectory(parent); // this is just quickly hacked together, the dir has to exist in order to write the file
            }

            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destination));
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    zos.putNextEntry(new ZipEntry(source.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
            zos.close();
        } catch (IOException e) { BedframeConstants.LOGGER.error("Couldn't zip resource pack", e); }
    }
}
