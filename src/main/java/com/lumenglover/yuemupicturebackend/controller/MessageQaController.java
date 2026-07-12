package com.lumenglover.yuemupicturebackend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumenglover.yuemupicturebackend.annotation.AuthCheck;
import com.lumenglover.yuemupicturebackend.annotation.RateLimiter;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.DeleteRequest;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.model.dto.rag.QaMessageAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.rag.QaMessageQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.RagSessionMessage;
import com.lumenglover.yuemupicturebackend.model.entity.RagUserSession;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.RagMessageVO;
import com.lumenglover.yuemupicturebackend.service.RagSessionMessageService;
import com.lumenglover.yuemupicturebackend.service.RagUserSessionService;
import com.lumenglover.yuemupicturebackend.service.RagService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import com.lumenglover.yuemupicturebackend.service.AiTokenRecordService;
import com.lumenglover.yuemupicturebackend.service.AiResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.CollectionUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 消息问答控制层
 */
@Slf4j
@RestController
@RequestMapping("/rag/qa/message")
public class MessageQaController {

    @Resource
    private RagSessionMessageService ragSessionMessageService;

    @Resource
    private RagUserSessionService ragUserSessionService;

    @Resource
    private RagService ragService;

    @Resource
    private com.lumenglover.yuemupicturebackend.service.PythonRagService pythonRagService;

    @Resource
    private UserService userService;

    @Resource
    private AiTokenRecordService aiTokenRecordService;

    @Resource
    private AiResourceService aiResourceService;

    /**
     * 会话 ID -> 活跃的 SSE 发射器列表
     */
    private static final Map<Long, CopyOnWriteArrayList<SseEmitter>> sessionIdToEmitters = new ConcurrentHashMap<>();

    /**
     * 会话 ID -> 当前已生成的内容缓存
     */
    private static final Map<Long, StringBuilder> sessionIdToCache = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(4);

    /**
     * 安全获取用户ID，避免异常
     */
    private Long getUserIdSafely(HttpServletRequest request) {
        try {
            User loginUser = userService.getLoginUser(request);
            return loginUser != null ? loginUser.getId() : null;
        } catch (Exception e) {
            log.warn("无法获取登录用户ID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 发送消息（用户提问 + 自动生成 AI 回答，一体化）
     *
     * @param messageAddRequest 消息添加请求
     * @param request           HTTP请求
     * @return 用户消息和AI消息
     */
    @PostMapping("/send")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @RateLimiter(key = "message_qa_send", time = 60, count = 15, message = "发送消息过于频繁，请稍后再试")
    public BaseResponse<RagMessageVO> sendQaMessage(@RequestBody QaMessageAddRequest messageAddRequest,
                                                    HttpServletRequest request) {
        if (messageAddRequest == null || messageAddRequest.getContent() == null
                || messageAddRequest.getContent().trim().isEmpty()) {
            log.warn("RAG消息发送收到空内容");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }

        try {
            User loginUser = userService.getLoginUser(request);
            Long userId = loginUser.getId();
            String content = messageAddRequest.getContent().trim();
            Long sessionId = messageAddRequest.getSessionId();

            log.info("开始处理RAG消息发送请求，用户ID: {}, 会话ID: {}", userId, sessionId);

            // 验证或创建会话
            RagUserSession session = validateOrCreateSession(userId, sessionId);
            Long actualSessionId = session.getId();

            // 持久化用户提问
            Long userMessageId = ragSessionMessageService.sendMessage(actualSessionId, userId, content, 1); // 1-用户提问
            RagMessageVO userMessage = RagMessageVO.objToVo(ragSessionMessageService.getById(userMessageId));

            // 更新会话时间
            ragUserSessionService.updateSessionTime(actualSessionId);

            log.info("RAG消息发送请求处理完成，用户ID: {}, 会话ID: {}, 消息ID: {}", userId, actualSessionId, userMessageId);

            return ResultUtils.success(userMessage);
        } catch (BusinessException e) {
            log.error("RAG消息发送业务异常 - 用户ID: {}, 会话ID: {}, 错误码: {}, 错误信息: {}",
                    getUserIdSafely(request), messageAddRequest != null ? messageAddRequest.getSessionId() : null,
                    e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("RAG消息发送系统异常 - 用户ID: {}, 会话ID: {}, 异常信息: {}",
                    getUserIdSafely(request), messageAddRequest != null ? messageAddRequest.getSessionId() : null,
                    e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "发送消息失败");
        }
    }

    /**
     * 获取AI回答
     *
     * @param messageQueryRequest 查询条件
     * @param request             HTTP请求
     * @return AI回答消息
     */
    @PostMapping("/answer")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @RateLimiter(key = "message_qa_answer", time = 60, count = 15, message = "获取AI回答过于频繁，请稍后再试")
    public BaseResponse<RagMessageVO> getQaAnswer(@RequestBody QaMessageQueryRequest messageQueryRequest,
                                                  HttpServletRequest request) {
        if (messageQueryRequest == null || messageQueryRequest.getSessionId() == null
                || messageQueryRequest.getContent() == null) {
            log.warn("RAG获取AI回答收到无效参数");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID和消息内容不能为空");
        }

        try {
            User loginUser = userService.getLoginUser(request);
            Long userId = loginUser.getId();
            Long sessionId = messageQueryRequest.getSessionId();
            String content = messageQueryRequest.getContent();

            log.info("开始处理RAG获取AI回答请求，用户ID: {}, 会话ID: {}", userId, sessionId);

            // 验证会话权限
            RagUserSession session = ragUserSessionService.getById(sessionId);
            if (session == null || !session.getUserId().equals(userId) || session.getIsDelete() == 1) {
                log.warn("RAG获取AI回答 - 会话不存在或无权限访问，sessionId: {}, userId: {}", sessionId, userId);
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在或无权限访问");
            }

            // 验证并扣减 AI Token 额度预检
            aiTokenRecordService.checkTokenQuota(userId);

            // 调用AI生成回答
            log.debug("RAG获取AI回答 - 开始调用ragService.chat，userId: {}, sessionId: {}, content: {}", userId, sessionId,
                    content);
            String saToken = cn.dev33.satoken.stp.StpUtil.getTokenValue();
            com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagResponse response = ragService.chat(userId,
                    sessionId, content, saToken);
            String aiAnswer = response.getAnswer();
            Integer tokenUsage = response.getTotalTokens();
            log.debug("RAG获取AI回答 - AI回答生成完成，回答长度: {}", aiAnswer != null ? aiAnswer.length() : 0);

            // 统计消耗的真实 Token 数量
            int consumeToken = (tokenUsage != null && tokenUsage > 0) ? tokenUsage
                    : (content.length() + (aiAnswer != null ? aiAnswer.length() : 0));
            aiTokenRecordService.recordTokenUsage(userId, consumeToken);

            // 持久化AI回答
            Long aiMessageId = ragSessionMessageService.sendMessage(sessionId, userId, aiAnswer, 2); // 2-AI回答
            RagMessageVO aiMessage = RagMessageVO.objToVo(ragSessionMessageService.getById(aiMessageId));

            // 更新会话时间
            ragUserSessionService.updateSessionTime(sessionId);

            // 触发异步摘要逻辑
            ragService.checkAndGenerateSummaryAsync(sessionId, userId);

            log.info("RAG获取AI回答请求处理完成，用户ID: {}, 会话ID: {}, 消息ID: {}", userId, sessionId, aiMessageId);

            return ResultUtils.success(aiMessage);
        } catch (BusinessException e) {
            log.error("RAG获取AI回答业务异常 - 用户ID: {}, 会话ID: {}, 错误码: {}, 错误信息: {}",
                    getUserIdSafely(request), messageQueryRequest != null ? messageQueryRequest.getSessionId() : null,
                    e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("RAG获取AI回答系统异常 - 用户ID: {}, 会话ID: {}, 异常信息: {}",
                    getUserIdSafely(request), messageQueryRequest != null ? messageQueryRequest.getSessionId() : null,
                    e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取AI回答失败");
        }
    }

    /**
     * 会话消息列表查询
     */
    @GetMapping("/list")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @RateLimiter(key = "message_qa_list", time = 60, count = 30, message = "消息列表查询过于频繁，请稍后再试")
    public BaseResponse<IPage<RagMessageVO>> listQaMessages(QaMessageQueryRequest messageQueryRequest,
                                                            HttpServletRequest request) {
        if (messageQueryRequest == null || messageQueryRequest.getSessionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }

        try {
            User loginUser = userService.getLoginUser(request);
            Long userId = loginUser.getId();
            Long sessionId = messageQueryRequest.getSessionId();

            // 验证会话权限
            RagUserSession session = ragUserSessionService.getById(sessionId);
            if (session == null || !session.getUserId().equals(userId) || session.getIsDelete() == 1) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在或无权限访问");
            }

            messageQueryRequest.setSortField("createTime");
            messageQueryRequest.setSortOrder("descend");

            IPage<RagSessionMessage> page = ragSessionMessageService.getMessagePage(messageQueryRequest);
            IPage<RagMessageVO> messagePage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                    page.getCurrent(), page.getSize(), page.getTotal());

            List<RagMessageVO> voRecords = page.getRecords().stream()
                    .map(RagMessageVO::objToVo)
                    .collect(Collectors.toList());
            // 反转记录，保证界面展示为时间升序（从旧到新），从而适配前端瀑布流上拉加载历史的体验
            java.util.Collections.reverse(voRecords);
            messagePage.setRecords(voRecords);

            return ResultUtils.success(messagePage);
        } catch (Exception e) {
            log.error("RAG消息列表查询异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "查询消息列表失败");
        }
    }

    @GetMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @RateLimiter(key = "message_qa_stream", time = 60, count = 15, message = "发送流式消息过于频繁，请稍后再试")
    public SseEmitter sendQaStreamMessage(
            @RequestParam(value = "message", required = true) String content,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "model", required = false, defaultValue = "Qwen3.5-Flash") String model,
            HttpServletRequest request,
            javax.servlet.http.HttpServletResponse response) {

        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");

        String reqContent = content.trim();
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();

        RagUserSession session = validateOrCreateSession(userId, sessionId);
        Long actualSessionId = session.getId();

        SseEmitter emitter = new SseEmitter(180000L);

        // 接力逻辑
        if (sessionIdToEmitters.containsKey(actualSessionId)) {
            CopyOnWriteArrayList<SseEmitter> emitters = sessionIdToEmitters.get(actualSessionId);
            emitters.add(emitter);

            emitter.onCompletion(() -> emitters.remove(emitter));
            emitter.onError((ex) -> emitters.remove(emitter));
            emitter.onTimeout(() -> {
                emitters.remove(emitter);
                emitter.complete();
            });

            StringBuilder cache = sessionIdToCache.get(actualSessionId);
            if (cache != null && cache.length() > 0) {
                try {
                    Map<String, Object> tokenMap = new HashMap<>();
                    tokenMap.put("token", cache.toString());
                    tokenMap.put("isSync", true);
                    emitter.send(SseEmitter.event().name("aiAnswerChunk").data(tokenMap));
                } catch (Exception ignored) {
                }
            }
            return emitter;
        }

        // 验证 AI Token 额度预检（如果超出直接结束流）
        try {
            aiTokenRecordService.checkTokenQuota(userId);
        } catch (BusinessException e) {
            // 发送系统提示消息并正常结束流
            try {
                Map<String, Object> tokenMap = new HashMap<>();
                tokenMap.put("token", "系统提示：您的 AI Token 额度已耗尽，请升级会员后再试。");
                tokenMap.put("isSystem", true);
                tokenMap.put("isSync", true);
                emitter.send(SseEmitter.event().name("aiAnswerChunk").data(tokenMap));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        // 新生成逻辑
        CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        emitters.add(emitter);
        sessionIdToEmitters.put(actualSessionId, emitters);

        StringBuilder fullAnswer = new StringBuilder();
        sessionIdToCache.put(actualSessionId, fullAnswer);

        ScheduledFuture<?> heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception ignored) {
            }
        }, 5, 5, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {
            heartbeatTask.cancel(true);
            CopyOnWriteArrayList<SseEmitter> list = sessionIdToEmitters.get(actualSessionId);
            if (list != null)
                list.remove(emitter);
        });

        emitter.onTimeout(() -> {
            heartbeatTask.cancel(true);
            CopyOnWriteArrayList<SseEmitter> list = sessionIdToEmitters.get(actualSessionId);
            if (list != null)
                list.remove(emitter);
        });

        emitter.onError((e) -> {
            heartbeatTask.cancel(true);
            CopyOnWriteArrayList<SseEmitter> list = sessionIdToEmitters.get(actualSessionId);
            if (list != null)
                list.remove(emitter);
        });

        try {
            Long userMessageId = ragSessionMessageService.sendMessage(actualSessionId, userId, reqContent, 1);
            RagMessageVO userMessage = RagMessageVO.objToVo(ragSessionMessageService.getById(userMessageId));
            emitter.send(SseEmitter.event().name("userMsg").data(userMessage));

            // 立即创建占位符消息
            Long aiMessageId = ragSessionMessageService.sendMessage(actualSessionId, userId, "...", 2);
            ragUserSessionService.updateSessionTime(actualSessionId);

            String saToken = cn.dev33.satoken.stp.StpUtil.getTokenValue();

            ragService.chatStream(userId, actualSessionId, reqContent, saToken, model, result -> {
                String type = (String) result.get("type");
                String chunkContent = (String) result.get("content");
                if ("token".equals(type)) {
                    fullAnswer.append(chunkContent);
                    Map<String, String> data = new HashMap<>();
                    data.put("token", chunkContent);
                    distributeEvent(actualSessionId, "aiAnswerChunk", data);
                } else if ("status".equals(type)) {
                    Map<String, String> data = new HashMap<>();
                    data.put("status", chunkContent);
                    distributeEvent(actualSessionId, "aiStatusUpdate", data);
                }
            }, (totalTokens) -> {
                try {
                    String finalContent = fullAnswer.toString();

                    // 流式传输结束后，异步统计真实 Token 消耗数量
                    int consumeToken = (totalTokens != null && totalTokens > 0) ? totalTokens
                            : (reqContent.length() + finalContent.length());
                    int finalConsumeToken = (int) Math.ceil(consumeToken * getModelTokenMultiplier(model));
                    aiTokenRecordService.recordTokenUsage(userId, finalConsumeToken);

                    RagSessionMessage finalMsg = new RagSessionMessage();
                    finalMsg.setId(aiMessageId);
                    finalMsg.setContent(finalContent);
                    ragSessionMessageService.updateById(finalMsg);

                    // 提取并保存资源（图片、音频等）
                    aiResourceService.extractAndSaveResources(finalContent, aiMessageId, userId);

                    Map<String, Object> doneData = new HashMap<>();
                    doneData.put("aiMsgId", aiMessageId);
                    distributeEvent(actualSessionId, "done", doneData);
                } finally {
                    heartbeatTask.cancel(true);
                    CopyOnWriteArrayList<SseEmitter> active = sessionIdToEmitters.remove(actualSessionId);
                    sessionIdToCache.remove(actualSessionId);
                    if (active != null)
                        active.forEach(SseEmitter::complete);
                }
            });
        } catch (Exception e) {
            heartbeatTask.cancel(true);
            sessionIdToEmitters.remove(actualSessionId);
            sessionIdToCache.remove(actualSessionId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private double getModelTokenMultiplier(String model) {
        if (model == null)
            return 1.0;
        String lowerModel = model.toLowerCase();
        if (lowerModel.contains("deepseek-v4-pro"))
            return 16.36;
        if (lowerModel.contains("qwen3.5-plus"))
            return 2.55;
        if (lowerModel.contains("deepseek-v4-flash"))
            return 1.36;
        return 1.0;
    }

    private void distributeEvent(Long sessionId, String name, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = sessionIdToEmitters.get(sessionId);
        if (emitters == null)
            return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    @PostMapping("/clearContext")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<RagMessageVO> clearSessionContext(@RequestParam Long sessionId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        RagUserSession session = ragUserSessionService.getById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        RagSessionMessage clearMessage = new RagSessionMessage();
        clearMessage.setSessionId(sessionId);
        clearMessage.setContent("上下文已清理");
        clearMessage.setMessageType(2);
        clearMessage.setUserId(userId);
        ragSessionMessageService.save(clearMessage);
        ragService.clearSessionContext(sessionId);

        return ResultUtils.success(RagMessageVO.objToVo(clearMessage));
    }

    private RagUserSession validateOrCreateSession(Long userId, Long sessionId) {
        if (sessionId != null) {
            RagUserSession session = ragUserSessionService.getById(sessionId);
            if (session != null && session.getUserId().equals(userId) && session.getIsDelete() == 0) {
                return session;
            }
        }
        RagUserSession active = ragUserSessionService.getActiveSession(userId);
        if (active == null) {
            Long id = ragUserSessionService.createSession(userId);
            return ragUserSessionService.getById(id);
        }
        return active;
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<IPage<RagMessageVO>> listAllMessagesByPage(
            @RequestBody(required = false) QaMessageQueryRequest messageQueryRequest) {
        if (messageQueryRequest == null)
            messageQueryRequest = new QaMessageQueryRequest();
        IPage<RagSessionMessage> page = ragSessionMessageService.getMessagePage(messageQueryRequest);
        IPage<RagMessageVO> messagePage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                page.getCurrent(), page.getSize(), page.getTotal());
        messagePage.setRecords(page.getRecords().stream().map(RagMessageVO::objToVo).collect(Collectors.toList()));
        return ResultUtils.success(messagePage);
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<RagMessageVO> getMessageById(long id) {
        RagSessionMessage message = ragSessionMessageService.getById(id);
        ThrowUtils.throwIf(message == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(RagMessageVO.objToVo(message));
    }

    @PostMapping("/delete/admin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteMessageById(@RequestBody DeleteRequest deleteRequest) {
        return ResultUtils.success(ragSessionMessageService.removeById(deleteRequest.getId()));
    }

    /**
     * 获取TTS语音流
     */
    @GetMapping(value = "/tts", produces = "audio/mpeg")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public byte[] getTtsAudio(@RequestParam(value = "text") String text,
                              @RequestParam(value = "voiceType", required = false, defaultValue = "female_gentle") String voiceType) {
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文本不能为空");
        }
        try {
            // 调用Python RAG服务获取TTS音频流
            return pythonRagService.generateTts(text, voiceType);
        } catch (Exception e) {
            log.error("获取TTS音频流异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取TTS音频失败");
        }
    }

    /**
     * 获取用户 AI Token 使用情况（5小时内及本周）
     */
    @GetMapping("/ai_token/usage")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<com.lumenglover.yuemupicturebackend.model.vo.AiTokenUsageVO> getAiTokenUsage(
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiTokenRecordService.getTokenUsage(loginUser.getId()));
    }

    /**
     * 校验并扣减每周生图额度
     */
    @PostMapping("/image_gen/quota/deduct")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deductImageGenQuota(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean success = aiTokenRecordService.checkAndDeductImageGenQuota(loginUser.getId());
        if (!success) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "本周图片生成额度已耗尽");
        }
        return ResultUtils.success(true);
    }
}
