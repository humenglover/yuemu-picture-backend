package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.config.RagConfig;
import com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagResponse;
import com.lumenglover.yuemupicturebackend.model.entity.RagSessionMessage;

import com.lumenglover.yuemupicturebackend.model.entity.RagMemory;
import com.lumenglover.yuemupicturebackend.model.entity.RagSessionSummary;
import com.meilisearch.sdk.Client;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.service.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private static final String KEY_CHAT_CONTEXT_SESSION = "rag:chat:context:session:%s";
    private static final String KEY_CHAT_CONTEXT_CLEARED = "rag:chat:context:session:cleared:%s";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role;
        private String content;
        private Long timestamp;

        public String toPromptString() {
            return (role.equals("user") ? "用户：" : "客服：") + content;
        }
    }

    @Autowired
    private RagConfig ragConfig;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RagSessionMessageService ragSessionMessageService;
    @Autowired
    private PythonRagService pythonRagService;
    @Autowired
    private RagSessionSummaryService ragSessionSummaryService;
    @Autowired
    private Client meiliSearchClient;
    @Autowired
    private SearchService searchService;
    @Autowired
    private UserService userService;

    private static final String KEY_USER_PERSONA = "rag:persona:%s";

    /**
     * 获取用户画像（Redis + MySQL）
     */
    private String getUserPersona(Long userId) {
        if (userId == null)
            return "";
        String redisKey = String.format(KEY_USER_PERSONA, userId);
        try {
            // 1. 查 Redis 缓存
            String personaStr = (String) redisTemplate.opsForValue().get(redisKey);
            if (StrUtil.isNotBlank(personaStr)) {
                return personaStr;
            }

            // 2. Redis 未命中，查 MySQL
            User user = userService.getById(userId);
            if (user == null) {
                return "";
            }

            // 3. 组装核心画像
            StringBuilder sb = new StringBuilder();
            sb.append("用户昵称: ").append(user.getUserName()).append(", ");
            sb.append("身份: ").append(user.getUserRole()).append(", ");
            if (StrUtil.isNotBlank(user.getUserProfile())) {
                sb.append("个人简介: ").append(user.getUserProfile());
            } else {
                sb.append("个人简介: 无");
            }

            String persona = sb.toString();

            // 4. 存入 Redis，设置 1 小时过期
            redisTemplate.opsForValue().set(redisKey, persona, 1, TimeUnit.HOURS);
            return persona;
        } catch (Exception e) {
            log.error("获取用户画像失败，降级为空 | 用户ID: {}", userId, e);
            return "";
        }
    }

    private List<ChatMessage> getSessionContext(Long sessionId) {
        if (sessionId == null)
            return new ArrayList<>();
        String contextRedisKey = String.format(KEY_CHAT_CONTEXT_SESSION, sessionId);
        String clearedRedisKey = String.format(KEY_CHAT_CONTEXT_CLEARED, sessionId);

        try {
            Object clearedMarker = redisTemplate.opsForValue().get(clearedRedisKey);
            if (clearedMarker != null) {
                log.info("【会话上下文】会话已清除，返回空上下文 | 会话ID: {}", sessionId);
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("【会话上下文】检查清除标记失败 | 会话ID: {}", sessionId, e);
        }

        try {
            String jsonStr = (String) redisTemplate.opsForValue().get(contextRedisKey);
            if (StrUtil.isNotBlank(jsonStr)) {
                List<ChatMessage> chatMessages = JSONUtil.toList(jsonStr, ChatMessage.class);
                log.info("【会话上下文】从Redis加载成功 | 会话ID: {} | 消息数: {}", sessionId, chatMessages.size());
                return chatMessages;
            }
        } catch (Exception e) {
            log.error("【会话上下文】从Redis加载失败 | 会话ID: {}", sessionId, e);
        }

        log.info("【会话上下文】Redis无缓存，从数据库加载 | 会话ID: {}", sessionId);
        List<ChatMessage> dbContext = getContextFromDatabase(sessionId, 4);
        if (dbContext != null && !dbContext.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(contextRedisKey, JSONUtil.toJsonStr(dbContext), 24, TimeUnit.HOURS);
                log.info("【会话上下文】数据库结果存入Redis | 会话ID: {} | 消息数: {}", sessionId, dbContext.size());
            } catch (Exception e) {
                log.error("【会话上下文】存入Redis失败 | 会话ID: {}", sessionId, e);
            }
        }
        return dbContext;
    }

    private void saveSessionContext(Long sessionId, List<ChatMessage> messageList) {
        if (sessionId == null || messageList == null || messageList.isEmpty())
            return;
        String contextRedisKey = String.format(KEY_CHAT_CONTEXT_SESSION, sessionId);
        String clearedRedisKey = String.format(KEY_CHAT_CONTEXT_CLEARED, sessionId);

        try {
            List<ChatMessage> filteredMessages = filterRecentRounds(messageList, 4);
            redisTemplate.opsForValue().set(contextRedisKey, JSONUtil.toJsonStr(filteredMessages), 24, TimeUnit.HOURS);
            redisTemplate.delete(clearedRedisKey);
            log.info("【会话上下文】保存Redis成功 | 会话ID: {} | 过滤后消息数: {}", sessionId, filteredMessages.size());
        } catch (Exception e) {
            log.error("【会话上下文】保存Redis失败 | 会话ID: {}", sessionId, e);
        }
    }

    @Override
    public void clearSessionContext(Long sessionId) {
        if (sessionId == null)
            return;
        String contextRedisKey = String.format(KEY_CHAT_CONTEXT_SESSION, sessionId);
        String clearedRedisKey = String.format(KEY_CHAT_CONTEXT_CLEARED, sessionId);
        try {
            redisTemplate.delete(contextRedisKey);
            redisTemplate.opsForValue().set(clearedRedisKey, String.valueOf(System.currentTimeMillis()), 24,
                    TimeUnit.HOURS);
            RagSessionMessage clearMessage = new RagSessionMessage();
            clearMessage.setSessionId(sessionId);
            clearMessage.setContent("上下文已清理");
            clearMessage.setMessageType(2);
            clearMessage.setUserId(0L);
            ragSessionMessageService.save(clearMessage);
            log.info("【会话上下文】清除成功 | 会话ID: {}", sessionId);
        } catch (Exception e) {
            log.error("【会话上下文】清除失败 | 会话ID: {}", sessionId, e);
        }
    }

    @Override
    public com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagResponse chat(Long userId, Long sessionId,
            String question, String saToken) {
        if (StrUtil.isBlank(question)) {
            log.warn("【RAG问答】问题为空 | 用户ID: {} | 会话ID: {}", userId, sessionId);
            return com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagResponse.builder().answer("请输入您想咨询的问题。")
                    .build();
        }

        try {
            List<ChatMessage> context = getSessionContext(sessionId);

            // 将ChatMessage转换为Map格式的历史记录
            List<Map<String, String>> history = convertChatMessagesToHistory(context);

            // 获取超长记忆（LTM）
            String ltmContext = getRelevantLongTermMemory(userId, question);

            // 获取用户画像
            String userPersona = getUserPersona(userId);

            // 调用Python RAG服务
            String sessionStr = sessionId != null ? sessionId.toString() : null;
            PythonRagResponse response = pythonRagService.callPythonRagSync(question, history, sessionStr, ltmContext,
                    saToken, userPersona);

            String answer = response.getAnswer();

            // 解析并处理搜索 Token
            answer = processSearchTokens(answer, saToken);

            List<ChatMessage> updatedContext = new ArrayList<>(context);
            updatedContext.add(new ChatMessage("user", question, System.currentTimeMillis()));
            updatedContext.add(new ChatMessage("assistant", answer, System.currentTimeMillis()));
            saveSessionContext(sessionId, updatedContext);

            log.info("【RAG问答】完成 | 用户ID: {} | 会话ID: {} | 问题: {}", userId, sessionId, question);
            return response;
        } catch (Exception e) {
            log.error("【RAG问答】失败 | 用户ID: {} | 会话ID: {} | 问题: {}", userId, sessionId, question, e);
            return PythonRagResponse.builder().answer("抱歉，客服暂时无法回答您的问题，请稍后再试。").build();
        }
    }

    @Override
    public void chatStream(Long userId, Long sessionId, String question, String saToken, String model,
            Consumer<Map<String, Object>> resultConsumer, Consumer<Integer> onComplete) {
        if (StrUtil.isBlank(question)) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("type", "token");
            resultMap.put("content", "请输入您想咨询的问题。");
            resultConsumer.accept(resultMap);
            onComplete.accept(0);
            return;
        }

        try {
            List<ChatMessage> context = getSessionContext(sessionId);

            // 将ChatMessage转换为Map格式的历史记录
            List<Map<String, String>> history = convertChatMessagesToHistory(context);

            // 调用Python RAG流式接口
            String sessionStr = sessionId != null ? sessionId.toString() : null;

            // 使用StringBuilder来收集完整的回答
            StringBuilder fullAnswer = new StringBuilder();
            final Integer[] finalTokens = new Integer[] { 0 };

            // 创建一个新的 consumer，用于收集完整答案和转发状态
            Consumer<Map<String, Object>> collectingResultConsumer = result -> {
                String type = (String) result.get("type");
                if ("total_tokens".equals(type)) {
                    Object contentObj = result.get("content");
                    if (contentObj instanceof Integer) {
                        finalTokens[0] = (Integer) contentObj;
                    }
                } else {
                    String messageChunk = (String) result.get("content");
                    if ("token".equals(type)) {
                        fullAnswer.append(messageChunk);
                    }
                    resultConsumer.accept(result);
                }
            };

            // 获取超长记忆 (LTM)
            String ltmContext = getRelevantLongTermMemory(userId, question);

            // 获取用户画像
            String userPersona = getUserPersona(userId);

            log.info("【RAG流式问答】发送上下文 | 问题: {} | 短期记忆轮数: {} | LTM长度: {} | 画像存在: {} | 模型: {}",
                    question, history.size() / 2, ltmContext.length(), StrUtil.isNotBlank(userPersona), model);
            if (StrUtil.isNotBlank(ltmContext)) {
                log.info("【RAG流式问答】LTM全文:\n{}", ltmContext);
            }

            // 使用新的流式处理方法
            pythonRagService.callPythonRagStream(question, history, sessionStr, ltmContext, saToken, userPersona, model,
                    collectingResultConsumer, () -> {
                        // 在流式处理完成后，保存完整的上下文
                        String finalAnswer = fullAnswer.toString();

                        // 处理搜索 Token（同步处理最后的结果）
                        finalAnswer = processSearchTokens(finalAnswer, saToken);

                        List<ChatMessage> currentContext = getSessionContext(sessionId);
                        List<ChatMessage> updatedContext = new ArrayList<>(currentContext);
                        updatedContext.add(new ChatMessage("user", question, System.currentTimeMillis()));
                        updatedContext.add(new ChatMessage("assistant", finalAnswer, System.currentTimeMillis()));
                        saveSessionContext(sessionId, updatedContext);

                        log.info("【RAG流式问答】流式处理完成 | 用户ID: {} | 会话ID: {} | 问题: {} | 最终答案长度: {}",
                                userId, sessionId, question, finalAnswer.length());
                        onComplete.accept(finalTokens[0]);
                    });
        } catch (Exception e) {
            log.error("【RAG流式问答】失败 | 用户ID: {} | 会话ID: {} | 问题: {}", userId, sessionId, question, e);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("type", "token");
            errorMap.put("content", "抱歉，客服暂时无法回答您的问题，请稍后再试。");
            resultConsumer.accept(errorMap);
            onComplete.accept(0);
        }
    }

    @Override
    public void clearUserContext(Long userId) {
        log.warn("【会话上下文】仅支持按sessionId清除，请调用clearSessionContext | 用户ID: {}", userId);
    }

    private List<ChatMessage> filterRecentRounds(List<ChatMessage> messageList, int maxRounds) {
        if (messageList == null || messageList.isEmpty() || maxRounds <= 0)
            return new ArrayList<>();
        List<ChatMessage> filtered = new ArrayList<>();
        List<ChatMessage> reversed = new ArrayList<>(messageList);
        Collections.reverse(reversed);

        int roundCount = 0;
        for (int i = 0; i < reversed.size() && roundCount < maxRounds; i++) {
            ChatMessage msg = reversed.get(i);
            if (msg.getRole().equals("assistant")) {
                filtered.add(msg);
                if (i + 1 < reversed.size() && reversed.get(i + 1).getRole().equals("user")) {
                    filtered.add(reversed.get(i + 1));
                    i++;
                }
                roundCount++;
            }
        }
        Collections.reverse(filtered);
        return filtered;
    }

    private List<ChatMessage> getContextFromDatabase(Long sessionId, int limit) {
        try {
            List<RagSessionMessage> dbMessages = ragSessionMessageService.getRecentMessagesBySessionId(sessionId,
                    limit);
            if (dbMessages == null || dbMessages.isEmpty()) {
                log.info("【会话上下文】数据库无历史消息 | 会话ID: {}", sessionId);
                return new ArrayList<>();
            }
            dbMessages.sort(Comparator.comparing(RagSessionMessage::getCreateTime));
            List<ChatMessage> chatMessages = new ArrayList<>();
            for (RagSessionMessage dbMessage : dbMessages) {
                String role = dbMessage.getMessageType() == 1 ? "user" : "assistant";
                chatMessages.add(new ChatMessage(role, dbMessage.getContent(), dbMessage.getCreateTime().getTime()));
            }
            return chatMessages;
        } catch (Exception e) {
            log.error("【会话上下文】数据库加载失败 | 会话ID: {}", sessionId, e);
            return new ArrayList<>();
        }
    }

    private String getRelevantLongTermMemory(Long userId, String question) {
        // 用户要求：直接不要插全局的这个搜索了，避免引入上下文噪声
        log.info("【超长记忆-检索】已根据系统设定全局关闭超长记忆检索功能。");
        return "";
    }

    @Override
    public String searchLongTermMemory(Long userId, String keyword) {
        try {
            // 使用数据库 LIKE 查询作为最稳定的后备方案，如果需要更高性能可以调用 meiliSearchClient
            List<RagSessionSummary> summaries = ragSessionSummaryService.list(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagSessionSummary>()
                            .eq("userId", userId)
                            .like(StrUtil.isNotBlank(keyword), "content", keyword)
                            .orderByDesc("summaryLevel") // 优先返回高级别摘要（大局观）
                            .orderByDesc("id")
                            .last("LIMIT 5"));

            if (summaries == null || summaries.isEmpty()) {
                return "未检索到相关历史记忆。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到以下历史记忆摘要：\n");
            for (RagSessionSummary summary : summaries) {
                sb.append("- ").append(summary.getContent()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("【超长记忆】搜索失败 | userId: {} | keyword: {}", userId, keyword, e);
            return "搜索失败。";
        }
    }

    private static final String KEY_RAG_MSG_COUNT = "rag:session:msg:count:%s";

    /**
     * 异步检查并生成会话摘要（公开给 Controller 调用）
     * 核心逻辑：用 Redis 计数，每轮对话+2，到 10 的倍数时总结最近 10 条
     */
    @Override
    @Async("ragSummaryExecutor")
    public void checkAndGenerateSummaryAsync(Long sessionId, Long userId) {
        if (sessionId == null)
            return;
        try {
            // 1. 等待数据库持久化完成
            Thread.sleep(1000);

            // 2. Redis 原子计数，每轮对话产生 2 条消息（用户+AI）
            String countKey = String.format(KEY_RAG_MSG_COUNT, sessionId);
            Long totalCount = redisTemplate.opsForValue().increment(countKey, 2);
            // 首次设置过期时间 7 天
            if (totalCount != null && totalCount == 2) {
                redisTemplate.expire(countKey, 7, TimeUnit.DAYS);
            }

            // 3. 只有总数是 10 的倍数时才触发总结
            if (totalCount == null || totalCount < 10 || totalCount % 10 != 0) {
                return;
            }

            log.info("【超长记忆】Redis计数达到 {} 条，触发一级摘要 | 会话ID: {}", totalCount, sessionId);

            // 4. 取最近 10 条消息进行总结
            List<RagSessionMessage> latestMessages = ragSessionMessageService.list(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagSessionMessage>()
                            .eq("sessionId", sessionId)
                            .eq("isDelete", 0)
                            .orderByDesc("id")
                            .last("LIMIT 10"));

            // 按时间正序排列
            java.util.Collections.reverse(latestMessages);

            // 5. 拼接待总结的对话文本
            StringBuilder toSummarize = new StringBuilder();
            for (RagSessionMessage msg : latestMessages) {
                toSummarize.append(msg.getMessageType() == 1 ? "用户: " : "助手: ")
                        .append(msg.getContent()).append("\n");
            }

            log.info("【超长记忆】触发一级摘要 | 会话ID: {} | 最近10条消息", sessionId);

            // 6. 调用 Python 专用摘要接口
            PythonRagResponse response = pythonRagService.callPythonSummarize(toSummarize.toString());
            String summaryContent = response.getAnswer();

            if (StrUtil.isNotBlank(summaryContent)) {
                // 保存摘要到数据库
                RagSessionSummary newSummary = new RagSessionSummary();
                newSummary.setSessionId(sessionId);
                newSummary.setUserId(userId);
                newSummary.setContent(summaryContent);
                newSummary.setSummaryLevel(0);
                newSummary.setLastMessageId(latestMessages.get(latestMessages.size() - 1).getId());
                ragSessionSummaryService.save(newSummary);

                // 同步到 Meilisearch
                saveToMeili(sessionId, userId, summaryContent, 0);
                log.info("【超长记忆】一级摘要保存成功 | 长度: {}", summaryContent.length());

                // 7. 检查一级摘要数量是否达到 10 条，触发二级摘要
                checkAndGenerateLevel1Summary(sessionId, userId);
            } else {
                log.warn("【超长记忆】一级摘要返回空，跳过本次总结");
            }
        } catch (Exception e) {
            log.error("【超长记忆】摘要生成任务异常", e);
        }
    }

    /**
     * 检查并生成二级摘要 (Level 1)
     * 当一级摘要数量达到 10 的倍数时，将最近 10 条一级摘要聚合为一条二级摘要
     */
    private void checkAndGenerateLevel1Summary(Long sessionId, Long userId) {
        try {
            long level0Count = ragSessionSummaryService.count(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagSessionSummary>()
                            .eq("sessionId", sessionId)
                            .eq("summaryLevel", 0));

            if (level0Count < 10 || level0Count % 10 != 0) {
                return;
            }

            log.info("【超长记忆】一级摘要达到 {} 条，触发二级摘要 | 会话ID: {}", level0Count, sessionId);

            // 取最近 10 条一级摘要
            List<RagSessionSummary> latestLevel0 = ragSessionSummaryService.list(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagSessionSummary>()
                            .eq("sessionId", sessionId)
                            .eq("summaryLevel", 0)
                            .orderByDesc("id")
                            .last("LIMIT 10"));

            StringBuilder toSummarize = new StringBuilder();
            for (RagSessionSummary s : latestLevel0) {
                toSummarize.append("- ").append(s.getContent()).append("\n");
            }

            PythonRagResponse response = pythonRagService.callPythonSummarize(
                    "这是几个阶段性总结，请进一步浓缩：\n" + toSummarize.toString());
            String megaSummary = response.getAnswer();

            if (StrUtil.isNotBlank(megaSummary)) {
                RagSessionSummary newMega = new RagSessionSummary();
                newMega.setSessionId(sessionId);
                newMega.setUserId(userId);
                newMega.setContent(megaSummary);
                newMega.setSummaryLevel(1);
                newMega.setLastMessageId(latestLevel0.get(0).getLastMessageId());
                ragSessionSummaryService.save(newMega);

                saveToMeili(sessionId, userId, megaSummary, 1);
                log.info("【超长记忆】二级摘要保存成功 | 深度覆盖已达 100+ 轮");
            }
        } catch (Exception e) {
            log.error("【超长记忆】二级摘要生成失败", e);
        }
    }

    private void saveToMeili(Long sessionId, Long userId, String content, Integer level) {
        try {
            RagMemory memory = new RagMemory();
            memory.setId(UUID.randomUUID().toString());
            memory.setUserId(userId);
            memory.setSessionId(sessionId);
            memory.setContent(content);
            memory.setMemoryType(0); // 摘要类型
            memory.setSummaryLevel(level);
            memory.setCreateTime(new java.util.Date());
            meiliSearchClient.index("rag_memory").addDocuments(cn.hutool.json.JSONUtil.toJsonStr(memory));
        } catch (Exception e) {
            log.error("【超长记忆】保存到 Meilisearch 失败", e);
        }
    }

    /**
     * 解析并处理搜索 Token [[SQTOKEN: 关键词]]
     */
    private String processSearchTokens(String content, String saToken) {
        if (StrUtil.isBlank(content) || !content.contains("[[SQTOKEN:")) {
            return content;
        }

        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[\\[SQTOKEN:\\s*(.*?)\\]\\]");
            java.util.regex.Matcher matcher = pattern.matcher(content);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String keyword = matcher.group(1);
                log.info("【RAG搜索插件】检测到搜索意图 | 关键词: {}", keyword);

                // 执行搜索
                com.lumenglover.yuemupicturebackend.model.dto.search.SearchRequest searchRequest = new com.lumenglover.yuemupicturebackend.model.dto.search.SearchRequest();
                searchRequest.setSearchText(keyword);
                searchRequest.setType("picture");
                searchRequest.setCurrent(1);
                searchRequest.setPageSize(5); // 默认返回5张

                try {
                    org.springframework.data.domain.Page<?> page = searchService.doSearch(searchRequest);
                    List<?> records = page.getContent();

                    if (records != null && !records.isEmpty()) {
                        StringBuilder imageList = new StringBuilder("\n\n为您找到以下相关图片：\n");
                        for (Object record : records) {
                            if (record instanceof com.lumenglover.yuemupicturebackend.model.vo.PictureVO) {
                                com.lumenglover.yuemupicturebackend.model.vo.PictureVO pic = (com.lumenglover.yuemupicturebackend.model.vo.PictureVO) record;
                                imageList.append("[附图: ").append(pic.getUrl()).append("]\n");
                            }
                        }
                        matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(imageList.toString()));
                    } else {
                        matcher.appendReplacement(sb, "（未找到相关图片）");
                    }
                } catch (Exception e) {
                    log.error("【RAG搜索插件】搜索执行失败", e);
                    matcher.appendReplacement(sb, "（搜索功能暂时不可用）");
                }
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            log.error("【RAG搜索插件】解析 Token 失败", e);
            return content;
        }
    }

    /**
     * 将ChatMessage列表转换为Map格式的历史记录
     */
    private List<Map<String, String>> convertChatMessagesToHistory(List<ChatMessage> chatMessages) {
        if (chatMessages == null || chatMessages.isEmpty()) {
            return CollUtil.newArrayList();
        }

        List<Map<String, String>> history = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessages) {
            Map<String, String> message = new HashMap<>();
            message.put("role", chatMessage.getRole());
            message.put("content", chatMessage.getContent());
            history.add(message);
        }
        return history;
    }
}
