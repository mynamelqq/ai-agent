package com.ai.aigent.service;

import com.ai.aigent.utils.AttachmentReaderUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentContextServiceTest {
    @Test
    void latestBoundaryKeepsMessagesAfterCoveredIdEvenWhenTheyPrecedeBoundaryRow() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForList(startsWith("SELECT id FROM agent_session"), eq(String.class),
                eq("42"), eq("7"))).thenReturn(List.of("42"));
        when(jdbc.queryForObject(contains("MAX(id)"), eq(Long.class), eq("42")))
                .thenReturn(4L, 3L);
        when(jdbc.query(startsWith("SELECT id, role, payload, status, covered_through_id"),
                any(RowMapper.class), eq("42"))).thenAnswer(invocation -> List.of(mapRow(
                invocation.getArgument(1), 4L, null,
                "{\"schemaVersion\":1,\"summary\":\"summary 1-2\"}", 2L)));
        when(jdbc.query(contains("kind='message' AND id>?"), any(RowMapper.class),
                eq("42"), eq(2L))).thenAnswer(invocation -> List.of(mapRow(
                invocation.getArgument(1), 3L, "user",
                "{\"schemaVersion\":1,\"text\":\"retained\",\"imageIds\":[]}", 0L)));

        AgentContextService service = new AgentContextService(jdbc, new ObjectMapper(),
                mock(AiAttachmentService.class), mock(AttachmentReaderUtil.class), transactions);
        var snapshot = service.loadSnapshot("42", "7");

        assertEquals(4L, snapshot.headId());
        assertEquals(2L, snapshot.coveredThroughId());
        assertEquals(2, snapshot.messages().size());
        assertEquals("retained", ((UserMessage) snapshot.messages().get(1)).getText());
    }

    private static Object mapRow(RowMapper<?> mapper, long id, String role,
                                 String payload, long coveredThroughId) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(id);
        when(row.getLong("covered_through_id")).thenReturn(coveredThroughId);
        when(row.getString("role")).thenReturn(role);
        when(row.getString("payload")).thenReturn(payload);
        when(row.getString("status")).thenReturn("complete");
        return mapper.mapRow(row, 0);
    }
}
