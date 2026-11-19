package com.ai.aigent.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactSnapshotTest {
    @TempDir Path workspace;
    @Test void replacingSourceWithSymlinkCannotChangeSnapshot() throws Exception {
        Path source = Files.writeString(workspace.resolve("result"), "original");
        Path outside = Files.writeString(workspace.resolve("secret"), "must not upload");
        Path copy;
        try (var snapshot = ArtifactSnapshot.capture(workspace, "result")) {
            copy = snapshot.file();
            Files.delete(source); Files.createSymbolicLink(source, outside);
            assertEquals("original", Files.readString(copy));
            assertFalse(copy.startsWith(workspace));
        }
        assertFalse(Files.exists(copy));
        assertFalse(Files.exists(copy.getParent()));
    }
    @Test void rejectsTraversalSymlinkFilesAndSymlinkParents() throws Exception {
        Files.createDirectories(workspace.resolve("dir"));
        Files.writeString(workspace.resolve("dir/file"), "ok");
        Files.createSymbolicLink(workspace.resolve("link"), workspace.resolve("dir"));
        Files.createSymbolicLink(workspace.resolve("filelink"), workspace.resolve("dir/file"));
        assertThrows(Exception.class, () -> ArtifactSnapshot.capture(workspace, "link/file"));
        assertThrows(Exception.class, () -> ArtifactSnapshot.capture(workspace, "filelink"));
        assertThrows(IllegalArgumentException.class, () -> ArtifactSnapshot.capture(workspace, "../secret"));
        assertThrows(IllegalArgumentException.class, () -> ArtifactSnapshot.capture(workspace, "/etc/passwd"));
        assertThrows(Exception.class, () -> ArtifactSnapshot.capture(workspace, "dir"));
    }
    @Test void rejectsOversizedFiles() throws Exception {
        try (var file = new java.io.RandomAccessFile(workspace.resolve("large").toFile(), "rw")) {
            file.setLength(20L * 1024 * 1024 + 1);
        }
        assertThrows(java.io.IOException.class, () -> ArtifactSnapshot.capture(workspace, "large"));
    }
}
