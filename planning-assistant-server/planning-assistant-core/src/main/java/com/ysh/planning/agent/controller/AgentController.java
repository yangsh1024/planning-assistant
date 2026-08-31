package com.ysh.planning.agent.controller;

import com.ysh.planning.agent.dto.*;
import com.ysh.planning.agent.service.AgentService;
import com.ysh.planning.agent.service.AgentActionService;
import com.ysh.planning.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService service;
    private final AgentActionService actionService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentChatRequest request) {
        return service.chat(request);
    }

    @GetMapping("/sessions")
    public Result<List<AgentSessionDto>> sessions() {
        return Result.ok(service.sessions());
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<List<AgentMessageDto>> messages(@PathVariable String id) {
        return Result.ok(service.messages(id));
    }

    @PostMapping("/actions/{id}/confirm")
    public Result<AgentActionDto> confirm(@PathVariable String id) {
        return Result.ok(actionService.confirm(id));
    }

    @PostMapping("/actions/{id}/cancel")
    public Result<AgentActionDto> cancel(@PathVariable String id) {
        return Result.ok(actionService.cancel(id));
    }
}
