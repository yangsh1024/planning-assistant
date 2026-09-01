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

/**
 * 暴露 Web Agent 对话、历史会话和确认操作接口。
 * 流式接口只发送可见回答与确认卡片，不暴露模型内部工具信息。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService service;
    private final AgentActionService actionService;

    /**
     * 发起一轮 SSE 对话。
     * <ol><li>校验请求</li><li>启动推流</li></ol>
     *
     * @param request 本轮消息和会话选项
     * @return 公开对话事件的 SSE 通道
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentChatRequest request) {
        return service.chat(request);
    }

    /**
     * 查询当前 Web 用户的会话摘要。
     * <ol><li>限定身份</li><li>返回会话</li></ol>
     *
     * @return 当前用户可见的会话列表
     */
    @GetMapping("/sessions")
    public Result<List<AgentSessionDto>> sessions() {
        return Result.ok(service.sessions());
    }

    /**
     * 查询指定会话的可见消息。
     * <ol><li>校验归属</li><li>返回消息</li></ol>
     *
     * @param id 会话标识
     * @return 会话的可见消息列表
     */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AgentMessageDto>> messages(@PathVariable String id) {
        return Result.ok(service.messages(id));
    }

    /**
     * 确认一项待执行写操作。
     * <ol><li>校验归属</li><li>执行操作</li></ol>
     *
     * @param id 待确认操作标识
     * @return 操作的最终状态
     */
    @PostMapping("/actions/{id}/confirm")
    public Result<AgentActionDto> confirm(@PathVariable String id) {
        return Result.ok(actionService.confirm(id));
    }

    /**
     * 取消一项待执行写操作。
     * <ol><li>校验归属</li><li>更新状态</li></ol>
     *
     * @param id 待取消操作标识
     * @return 操作的最终状态
     */
    @PostMapping("/actions/{id}/cancel")
    public Result<AgentActionDto> cancel(@PathVariable String id) {
        return Result.ok(actionService.cancel(id));
    }
}
