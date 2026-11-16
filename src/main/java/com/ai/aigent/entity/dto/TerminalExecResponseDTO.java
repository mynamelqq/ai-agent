package com.ai.aigent.entity.dto;

import com.ai.aigent.constant.ExecErrorType;
import lombok.Data;

@Data
public class TerminalExecResponseDTO {
    private boolean success;
    private String command;
    private String stdout;
    private String stderr;
    private int exitCode;
    private ExecErrorType errorType;
    public String cwd;
    public static TerminalExecResponseDTO ok(
            String command, String stdout, String stderr, int exitCode,ExecErrorType errorType) {
        TerminalExecResponseDTO dto = new TerminalExecResponseDTO();
        dto.success = true;
        dto.command = command;
        dto.stdout = stdout;
        dto.stderr = stderr;
        dto.exitCode = exitCode;
        dto.errorType=errorType;
        return dto;
    }

    public static TerminalExecResponseDTO error(String command, String error,ExecErrorType errorType) {
        TerminalExecResponseDTO dto = new TerminalExecResponseDTO();
        dto.success = false;
        dto.stderr=error;
        dto.command = command;
        dto.errorType=errorType;
        return dto;
    }
}
