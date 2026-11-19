package com.ai.aigent.sandbox;

import com.ai.aigent.tools.sandbox.ArtifactUploadTool;
import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.File;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArtifactUploadSnapshotTest {
    @TempDir Path workspace;
    @Test void cosReceivesPrivateSnapshotAndItIsRemovedAfterSuccess() throws Exception {
        checkUpload(false);
    }
    @Test void failedCosUploadAlsoRemovesPrivateSnapshot() throws Exception {
        checkUpload(true);
    }
    private void checkUpload(boolean fail) throws Exception {
        SandboxManager sandbox = mock(SandboxManager.class);
        COSClient cos = mock(COSClient.class);
        Path original = Files.writeString(workspace.resolve("result"), "trusted copy");
        when(sandbox.snapshot("session", "user", "token", "result"))
            .thenAnswer(i -> ArtifactSnapshot.capture(workspace, "result"));
        AtomicReference<Path> uploaded = new AtomicReference<>();
        when(cos.putObject(eq("bucket"), anyString(), any(File.class))).thenAnswer(i -> {
            File file = i.getArgument(2); uploaded.set(file.toPath());
            Files.delete(original);
            Files.writeString(original, "replaced by writer");
            assertFalse(file.toPath().startsWith(workspace));
            assertEquals("trusted copy", Files.readString(file.toPath()));
            if (fail) throw new IllegalStateException("upload failed");
            return null;
        });
        when(cos.generatePresignedUrl(eq("bucket"), anyString(), any(Date.class)))
            .thenReturn(URI.create("https://example.com/result").toURL());
        ArtifactUploadTool tool = new ArtifactUploadTool(sandbox, cos);
        ReflectionTestUtils.setField(tool, "bucket", "bucket");
        ToolContext context = new ToolContext(Map.of("chatId", "session", "userId", "user", "sandboxRunToken", "token"));
        if (fail) assertThrows(IllegalStateException.class, () -> tool.publishArtifact("result", context));
        else assertEquals("https://example.com/result", tool.publishArtifact("result", context).get("url"));
        assertNotNull(uploaded.get());
        assertFalse(Files.exists(uploaded.get()));
        assertFalse(Files.exists(uploaded.get().getParent()));
    }
}
