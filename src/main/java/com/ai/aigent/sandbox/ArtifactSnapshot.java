package com.ai.aigent.sandbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Set;

/** Private Java-owned copy; never mounted into a sandbox. */
public final class ArtifactSnapshot implements AutoCloseable {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private final Path directory;
    private final Path file;
    private final String name;

    private ArtifactSnapshot(Path directory, String name) {
        this.directory = directory;
        this.file = directory.resolve("content");
        this.name = name;
    }
    public Path file() { return file; }
    public String name() { return name; }

    public static ArtifactSnapshot capture(Path workspace, String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("需要相对文件路径");
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) throw new IllegalArgumentException("需要相对文件路径");
        for (Path part : relative) {
            if (part.toString().equals("..") || part.toString().equals(".") || part.toString().isEmpty())
                throw new IllegalArgumentException("非法文件路径");
        }
        if (Files.isSymbolicLink(workspace)) throw new IOException("工作目录不能是链接");
        Path temp = Files.createTempDirectory("aigent-artifact-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        ArtifactSnapshot snapshot = new ArtifactSnapshot(temp, relative.getFileName().toString());
        try (DirectoryStream<Path> root = Files.newDirectoryStream(workspace)) {
            if (temp.toRealPath().startsWith(workspace.toRealPath()))
                throw new IOException("快照目录不能位于共享工作区内");
            if (!(root instanceof SecureDirectoryStream<Path> secure))
                throw new IOException("文件系统不支持安全目录操作，拒绝上传");
            copy(secure, relative, 0, snapshot.file);
            // Validate the independent file actually uploaded, not only the source metadata.
            if (!Files.isRegularFile(snapshot.file, LinkOption.NOFOLLOW_LINKS) || Files.size(snapshot.file) > MAX_BYTES)
                throw new IOException("快照类型或大小不合法");
            Files.setPosixFilePermissions(snapshot.file, PosixFilePermissions.fromString("r--------"));
            return snapshot;
        } catch (Exception e) {
            try { snapshot.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    private static void copy(SecureDirectoryStream<Path> parent, Path relative, int index, Path destination)
            throws IOException {
        Path name = relative.getName(index);
        if (index < relative.getNameCount() - 1) {
            try (SecureDirectoryStream<Path> child = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                copy(child, relative, index + 1, destination);
            }
            return;
        }
        BasicFileAttributeView view = parent.getFileAttributeView(name, BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes before = view.readAttributes();
        if (!before.isRegularFile() || before.size() > MAX_BYTES) throw new IOException("文件类型或大小不合法");
        try (SeekableByteChannel input = parent.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             SeekableByteChannel output = Files.newByteChannel(destination,
                     Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                     PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer buffer = ByteBuffer.allocate(16384);
            long total = 0;
            while (input.read(buffer) != -1) {
                buffer.flip();
                total += buffer.remaining();
                if (total > MAX_BYTES) throw new IOException("文件超过20MiB上限");
                while (buffer.hasRemaining()) output.write(buffer);
                buffer.clear();
            }
            BasicFileAttributes after = view.readAttributes();
            if (!after.isRegularFile() || !java.util.Objects.equals(before.fileKey(), after.fileKey()) ||
                total != before.size() || before.size() != after.size() ||
                !before.lastModifiedTime().equals(after.lastModifiedTime()))
                throw new IOException("文件复制期间发生变化");
        }
    }
    @Override public void close() throws IOException {
        try { Files.deleteIfExists(file); }
        finally { Files.deleteIfExists(directory); }
    }
}
