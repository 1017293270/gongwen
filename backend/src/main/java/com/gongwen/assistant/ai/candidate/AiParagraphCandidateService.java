package com.gongwen.assistant.ai.candidate;

import com.gongwen.assistant.ai.AiNodeContext;
import com.gongwen.assistant.ai.AiParagraphModelResponse;
import com.gongwen.assistant.ai.AiParagraphRequest;
import com.gongwen.assistant.ai.MaterialPromptSummary;
import com.gongwen.assistant.ai.ModelAdapter;
import com.gongwen.assistant.ai.ModelAdapterException;
import com.gongwen.assistant.ai.ParagraphPrompt;
import com.gongwen.assistant.ai.PromptBuilder;
import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.draft.UpdateDraftBlocksRequest;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeDto;
import com.gongwen.assistant.draft.node.DraftNodeFormattingResolver;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.material.MaterialRepository;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AiParagraphCandidateService {
    private static final Set<String> ACCEPTABLE_STATUSES = Set.of("READY", "EDITED");
    private static final Set<String> TEXT_EDIT_PROTECTED_STATUSES = Set.of("ACCEPTED", "DISCARDED");
    private static final Set<String> TARGET_BLOCKED_STATUSES = Set.of("LOCKED", "DELETED");

    private final DraftService draftService;
    private final AiParagraphCandidateRepository candidateRepository;
    private final DraftNodeRepository draftNodeRepository;
    private final DraftNodeFormattingResolver formattingResolver;
    private final CurrentUserProvider currentUserProvider;
    private final MaterialRepository materialRepository;
    private final PromptBuilder promptBuilder;
    private final ModelAdapter modelAdapter;

    public AiParagraphCandidateService(
            DraftService draftService,
            AiParagraphCandidateRepository candidateRepository,
            DraftNodeRepository draftNodeRepository,
            DraftNodeFormattingResolver formattingResolver,
            CurrentUserProvider currentUserProvider,
            MaterialRepository materialRepository,
            PromptBuilder promptBuilder,
            ModelAdapter modelAdapter
    ) {
        this.draftService = draftService;
        this.candidateRepository = candidateRepository;
        this.draftNodeRepository = draftNodeRepository;
        this.formattingResolver = formattingResolver;
        this.currentUserProvider = currentUserProvider;
        this.materialRepository = materialRepository;
        this.promptBuilder = promptBuilder;
        this.modelAdapter = modelAdapter;
    }

    public List<AiParagraphCandidateDto> listCandidates(long draftId) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        return candidateRepository.findByDraftId(draft.id()).stream()
                .map(AiParagraphCandidateDto::from)
                .toList();
    }

    @Transactional
    public List<AiParagraphCandidateDto> createBatch(long draftId, CreateParagraphCandidateBatchRequest request) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        CreateParagraphCandidateBatchRequest normalizedRequest = request == null
                ? new CreateParagraphCandidateBatchRequest(null, "", List.of())
                : request;
        return normalizedRequest.sections().stream()
                .map(section -> candidateRepository.insert(toPendingCandidate(draft.id(), normalizedRequest, section)))
                .map(AiParagraphCandidateDto::from)
                .toList();
    }

    @Transactional
    public AiParagraphCandidateDto updateText(
            long draftId,
            long candidateId,
            UpdateParagraphCandidateRequest request
    ) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        AiParagraphCandidate candidate = ownedCandidate(draft.id(), candidateId);
        if (TEXT_EDIT_PROTECTED_STATUSES.contains(candidate.status())) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_STATUS_PROTECTED",
                    "Accepted or discarded candidate text cannot be edited"
            );
        }
        String text = request == null ? "" : request.candidateText();
        return candidateRepository.updateTextAndStatus(candidate.id(), text, "EDITED", digest(text))
                .map(AiParagraphCandidateDto::from)
                .orElseThrow(() -> candidateMissing(candidate.id()));
    }

    @Transactional
    public AiParagraphCandidateDto discard(long draftId, long candidateId) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        AiParagraphCandidate candidate = ownedCandidate(draft.id(), candidateId);
        if ("ACCEPTED".equals(candidate.status())) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_STATUS_PROTECTED",
                    "Accepted candidate cannot be discarded"
            );
        }
        return candidateRepository.updateStatusAndError(candidate.id(), "DISCARDED", "", "")
                .map(AiParagraphCandidateDto::from)
                .orElseThrow(() -> candidateMissing(candidate.id()));
    }

    @Transactional
    public AiParagraphCandidateDto retry(long draftId, long candidateId) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        AiParagraphCandidate candidate = ownedCandidate(draft.id(), candidateId);
        candidateRepository.updateStatusAndError(candidate.id(), "RETRYING", "", "")
                .orElseThrow(() -> candidateMissing(candidate.id()));

        ParagraphPrompt prompt = buildCandidatePrompt(draft, candidate);
        try {
            AiParagraphModelResponse modelResponse = modelAdapter.generateParagraphCandidate(prompt);
            String paragraphContent = normalizeCandidateContent(modelResponse.content(), prompt.heading());
            return candidateRepository.updateTextAndStatus(
                            candidate.id(),
                            paragraphContent,
                            "READY",
                            digest(paragraphContent)
                    )
                    .map(AiParagraphCandidateDto::from)
                    .orElseThrow(() -> candidateMissing(candidate.id()));
        } catch (ModelAdapterException exception) {
            return candidateRepository.updateStatusAndError(
                            candidate.id(),
                            "ERROR",
                            exception.errorCode(),
                            exception.getMessage()
                    )
                    .map(AiParagraphCandidateDto::from)
                    .orElseThrow(() -> candidateMissing(candidate.id()));
        } catch (IllegalArgumentException exception) {
            return candidateRepository.updateStatusAndError(
                            candidate.id(),
                            "ERROR",
                            "AI_RESPONSE_INVALID",
                            exception.getMessage()
                    )
                    .map(AiParagraphCandidateDto::from)
                    .orElseThrow(() -> candidateMissing(candidate.id()));
        }
    }

    @Transactional
    public AiParagraphCandidateDto retryStreaming(
            long draftId,
            long candidateId,
            Consumer<String> onDelta
    ) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        AiParagraphCandidate candidate = ownedCandidate(draft.id(), candidateId);
        candidateRepository.updateStatusAndError(candidate.id(), "RETRYING", "", "")
                .orElseThrow(() -> candidateMissing(candidate.id()));

        ParagraphPrompt prompt = buildCandidatePrompt(draft, candidate);
        try {
            AiParagraphModelResponse modelResponse = modelAdapter.streamParagraphCandidate(prompt, onDelta);
            String paragraphContent = normalizeCandidateContent(modelResponse.content(), prompt.heading());
            return candidateRepository.updateTextAndStatus(
                            candidate.id(),
                            paragraphContent,
                            "READY",
                            digest(paragraphContent)
                    )
                    .map(AiParagraphCandidateDto::from)
                    .orElseThrow(() -> candidateMissing(candidate.id()));
        } catch (ModelAdapterException exception) {
            return candidateRepository.updateStatusAndError(
                            candidate.id(),
                            "ERROR",
                            exception.errorCode(),
                            exception.getMessage()
                    )
                    .map(AiParagraphCandidateDto::from)
                    .orElseThrow(() -> candidateMissing(candidate.id()));
        } catch (IllegalArgumentException exception) {
            return candidateRepository.updateStatusAndError(
                            candidate.id(),
                            "ERROR",
                            "AI_RESPONSE_INVALID",
                            exception.getMessage()
                    )
                    .map(AiParagraphCandidateDto::from)
                    .orElseThrow(() -> candidateMissing(candidate.id()));
        }
    }

    @Transactional
    public AiParagraphCandidateAcceptResponse accept(long draftId, long candidateId) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        return acceptCandidate(draft, ownedCandidate(draft.id(), candidateId));
    }

    @Transactional
    public AiParagraphCandidateBatchAcceptResponse acceptBatch(
            long draftId,
            AcceptParagraphCandidateBatchRequest request
    ) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        List<AiParagraphCandidate> candidates = batchCandidates(draft.id(), request);
        List<Long> acceptedIds = new ArrayList<>();
        List<Long> skippedIds = new ArrayList<>();
        DraftDetailDto updatedDraft = draft;

        for (AiParagraphCandidate candidate : candidates) {
            if (!ACCEPTABLE_STATUSES.contains(candidate.status())) {
                skippedIds.add(candidate.id());
                continue;
            }
            try {
                AiParagraphCandidateAcceptResponse response = acceptCandidate(updatedDraft, candidate);
                acceptedIds.add(response.candidate().id());
                updatedDraft = response.draft();
            } catch (AiParagraphCandidateException exception) {
                skippedIds.add(candidate.id());
            }
        }

        return new AiParagraphCandidateBatchAcceptResponse(acceptedIds, skippedIds, updatedDraft);
    }

    private List<AiParagraphCandidate> batchCandidates(long draftId, AcceptParagraphCandidateBatchRequest request) {
        List<Long> requestedIds = request == null ? List.of() : request.candidateIds();
        if (requestedIds.isEmpty()) {
            return candidateRepository.findByDraftId(draftId);
        }
        Set<Long> seen = new HashSet<>();
        List<AiParagraphCandidate> candidates = new ArrayList<>();
        for (Long candidateId : requestedIds) {
            if (candidateId == null || !seen.add(candidateId)) {
                continue;
            }
            candidateRepository.findById(candidateId)
                    .filter(candidate -> candidate.draftId() == draftId)
                    .ifPresent(candidates::add);
        }
        return candidates;
    }

    private AiParagraphCandidateAcceptResponse acceptCandidate(
            DraftDetailDto draft,
            AiParagraphCandidate candidate
    ) {
        if (!ACCEPTABLE_STATUSES.contains(candidate.status())) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_NOT_ACCEPTABLE",
                    "Only READY or EDITED candidates can be accepted"
            );
        }
        if (candidate.candidateText().isBlank()) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_TEXT_REQUIRED",
                    "Candidate text is required before accept"
            );
        }
        DraftNode targetNode = targetNode(draft.id(), candidate);
        String nodeStatus = "EDITED".equals(candidate.status()) ? "USER_MODIFIED_AFTER_AI" : "AI_GENERATED";
        String acceptedContent = contentForTargetNode(candidate, targetNode);
        if (!hasSubstantiveBodyText(acceptedContent, candidate.heading())) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_BODY_REQUIRED",
                    "候选正文只有标题，请重新生成或补充正文后再确认"
            );
        }
        DraftNode updatedNode = draftNodeRepository.updateContent(
                        draft.id(),
                        targetNode.id(),
                        acceptedContent,
                        nodeStatus
                )
                .orElseThrow(() -> targetMissing(targetNode.id()));
        updatePairedHeadingIfNeeded(draft.id(), targetNode, candidate.heading(), nodeStatus);
        DraftDetailDto updatedDraft = draft;
        if (candidate.targetNodeId() == null) {
            updatedDraft = draftService.updateBlocks(
                    draft.id(),
                    new UpdateDraftBlocksRequest(upsertParagraphBlock(draft, acceptedContent, updatedNode.sortOrder()))
            );
        }
        AiParagraphCandidate accepted = candidateRepository.markAccepted(
                        candidate.id(),
                        candidate.paragraphTraceId() == null ? UUID.randomUUID() : candidate.paragraphTraceId(),
                        currentUserId()
                )
                .orElseThrow(() -> candidateMissing(candidate.id()));
        return new AiParagraphCandidateAcceptResponse(
                AiParagraphCandidateDto.from(accepted),
                updatedDraft,
                hydratedNodeDto(draft, updatedNode)
        );
    }

    private String contentForTargetNode(AiParagraphCandidate candidate, DraftNode targetNode) {
        if (!"BODY".equals(targetNode.role())) {
            return candidate.candidateText();
        }
        return stripHeadingPrefix(candidate.candidateText(), candidate.heading());
    }

    private void updatePairedHeadingIfNeeded(long draftId, DraftNode bodyNode, String heading, String status) {
        if (!"BODY".equals(bodyNode.role()) || heading == null || heading.isBlank()) {
            return;
        }
        pairedHeadingForBody(draftNodeRepository.findByDraftId(draftId), bodyNode)
                .filter(headingNode -> !headingMatches(headingNode.content(), heading))
                .ifPresent(headingNode -> draftNodeRepository.updateContent(
                        draftId,
                        headingNode.id(),
                        heading.strip(),
                        status
                ));
    }

    private DraftNodeDto hydratedNodeDto(DraftDetailDto draft, DraftNode updatedNode) {
        Map<Long, DraftNodeFormattingResolver.ResolvedDraftNodeFormatting> formattingByNodeId =
                formattingResolver.resolve(draft.templateVersionId(), List.of(updatedNode));
        DraftNodeFormattingResolver.ResolvedDraftNodeFormatting formatting = formattingByNodeId.get(updatedNode.id());
        return DraftNodeDto.from(
                updatedNode,
                formatting == null ? null : formatting.baseFormatting(),
                formatting == null ? null : formatting.effectiveFormatting()
        );
    }

    private AiParagraphCandidate toPendingCandidate(
            long draftId,
            CreateParagraphCandidateBatchRequest request,
            ParagraphCandidateSectionRequest section
    ) {
        ParagraphCandidateSectionRequest normalizedSection = section == null
                ? new ParagraphCandidateSectionRequest(null, "", "", 0, "", List.of(), "")
                : section;
        String text = normalizedSection.candidateText();
        return new AiParagraphCandidate(
                0L,
                draftId,
                normalizedSection.targetNodeId(),
                normalizedSection.targetNodeRole(),
                normalizedSection.targetNodeTitle(),
                request.outlineTraceId(),
                null,
                normalizedSection.sectionIndex(),
                normalizedSection.heading(),
                normalizedSection.points(),
                request.instructionSummary(),
                text,
                digest(text),
                "PENDING",
                "",
                "",
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private ParagraphPrompt buildCandidatePrompt(DraftDetailDto draft, AiParagraphCandidate candidate) {
        DraftNode targetNode = candidate.targetNodeId() == null
                ? null
                : draftNodeRepository.findByDraftId(draft.id()).stream()
                .filter(node -> node.id() == candidate.targetNodeId())
                .findFirst()
                .orElseThrow(() -> targetMissing(candidate.targetNodeId()));
        AiNodeContext nodeContext = new AiNodeContext(
                candidate.targetNodeId(),
                firstNonBlank(targetNode == null ? null : targetNode.role(), candidate.targetNodeRole()),
                firstNonBlank(targetNode == null ? null : targetNode.title(), candidate.targetNodeTitle()),
                targetNode == null ? "" : targetNode.content()
        );
        AiParagraphRequest request = new AiParagraphRequest(
                candidate.heading(),
                candidate.points(),
                candidate.instructionSummary(),
                targetNode == null ? null : targetNode.sortOrder(),
                candidate.targetNodeId(),
                nodeContext.nodeRole(),
                nodeContext.nodeTitle(),
                nodeContext.nodeContext()
        );
        List<MaterialPromptSummary> materials = materialRepository.findReadyTextSummariesByDraftId(draft.id());
        return promptBuilder.buildParagraphPrompt(draft, materials, request, nodeContext);
    }

    private AiParagraphCandidate ownedCandidate(long draftId, long candidateId) {
        AiParagraphCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> candidateMissing(candidateId));
        if (candidate.draftId() != draftId) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_DRAFT_MISMATCH",
                    "Candidate does not belong to this draft"
            );
        }
        return candidate;
    }

    private DraftNode targetNode(long draftId, AiParagraphCandidate candidate) {
        List<DraftNode> draftNodes = draftNodeRepository.findByDraftId(draftId);
        DraftNode targetNode = candidate.targetNodeId() == null
                ? resolveTargetNode(draftNodes, candidate)
                : draftNodes.stream()
                .filter(node -> node.id() == candidate.targetNodeId())
                .findFirst()
                .orElseThrow(() -> targetMissing(candidate.targetNodeId()));
        if (!isAcceptableCandidateTarget(targetNode)) {
            targetNode = resolveTargetNode(draftNodes, candidate);
        }
        if (TARGET_BLOCKED_STATUSES.contains(targetNode.status())) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_TARGET_BLOCKED",
                    "Target node cannot be accepted because it is locked or deleted"
            );
        }
        return targetNode;
    }

    private boolean isAcceptableCandidateTarget(DraftNode node) {
        return node != null && "BODY".equals(node.role()) && looksLikeBodyContent(node.content());
    }

    private DraftNode resolveTargetNode(List<DraftNode> draftNodes, AiParagraphCandidate candidate) {
        List<DraftNode> sortedNodes = draftNodes.stream()
                .filter(node -> !"DELETED".equalsIgnoreCase(node.status()))
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .toList();
        return targetNodeByHeading(sortedNodes, candidate.heading())
                .or(() -> targetNodeByHeading(sortedNodes, candidate.targetNodeTitle()))
                .or(() -> targetNodeBySectionIndex(sortedNodes, candidate.sectionIndex()))
                .orElseThrow(() -> new AiParagraphCandidateException(
                        "AI_CANDIDATE_TARGET_REQUIRED",
                        "候选正文未匹配到可替换的正文结构，请先重新生成提纲或调整结构映射"
                ));
    }

    private Optional<DraftNode> targetNodeByHeading(List<DraftNode> sortedNodes, String heading) {
        if (heading == null || heading.isBlank()) {
            return Optional.empty();
        }
        for (int index = 0; index < sortedNodes.size(); index++) {
            DraftNode node = sortedNodes.get(index);
            if (!node.role().startsWith("BODY_HEADING_LEVEL_")) {
                continue;
            }
            if (!headingMatches(node.content(), heading) && !headingMatches(node.title(), heading)) {
                continue;
            }
            Optional<DraftNode> bodyNode = firstBodyNodeAfterHeading(sortedNodes, index);
            if (bodyNode.isPresent()) {
                return bodyNode;
            }
        }
        return Optional.empty();
    }

    private Optional<DraftNode> targetNodeBySectionIndex(List<DraftNode> sortedNodes, int sectionIndex) {
        if (sectionIndex < 0) {
            return Optional.empty();
        }
        List<DraftNode> sectionBodyNodes = new ArrayList<>();
        for (int index = 0; index < sortedNodes.size(); index++) {
            DraftNode node = sortedNodes.get(index);
            if (node.role().startsWith("BODY_HEADING_LEVEL_")) {
                firstBodyNodeAfterHeading(sortedNodes, index).ifPresent(sectionBodyNodes::add);
            }
        }
        return sectionIndex < sectionBodyNodes.size()
                ? Optional.of(sectionBodyNodes.get(sectionIndex))
                : Optional.empty();
    }

    private Optional<DraftNode> firstBodyNodeAfterHeading(List<DraftNode> sortedNodes, int headingIndex) {
        for (int index = headingIndex + 1; index < sortedNodes.size(); index++) {
            DraftNode node = sortedNodes.get(index);
            if (node.role().startsWith("BODY_HEADING_LEVEL_")) {
                return Optional.empty();
            }
            if ("BODY".equals(node.role()) && looksLikeBodyContent(node.content())) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private Optional<DraftNode> pairedHeadingForBody(List<DraftNode> draftNodes, DraftNode bodyNode) {
        List<DraftNode> sortedNodes = draftNodes.stream()
                .filter(node -> !"DELETED".equalsIgnoreCase(node.status()))
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .toList();
        for (int index = 0; index < sortedNodes.size(); index++) {
            DraftNode node = sortedNodes.get(index);
            if (node.id() != bodyNode.id()) {
                continue;
            }
            if (index == 0) {
                return Optional.empty();
            }
            DraftNode previous = sortedNodes.get(index - 1);
            return previous.role().startsWith("BODY_HEADING_LEVEL_")
                    ? Optional.of(previous)
                    : Optional.empty();
        }
        return Optional.empty();
    }

    private boolean headingMatches(String left, String right) {
        String normalizedLeft = normalizeHeading(left);
        String normalizedRight = normalizeHeading(right);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalizedRight);
    }

    private String normalizeHeading(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "")
                .replaceAll("[：:。；;，,]", "")
                .strip();
    }

    private boolean looksLikeBodyContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isBlank()) {
            return true;
        }
        String compact = normalized.replaceAll("\\s+", "");
        if (compact.startsWith("附件") || compact.startsWith("联系人") || compact.startsWith("（联系人") || compact.startsWith("(联系人")) {
            return false;
        }
        if (compact.contains("印发") || compact.contains("抄送")) {
            return false;
        }
        return compact.length() > 32 || !compact.matches("^[0-9Xx]{2,4}年[0-9Xx]{1,2}月[0-9Xx]{1,2}日$");
    }

    private List<DraftBlockUpdateRequest> upsertParagraphBlock(DraftDetailDto draft, String content, int sortOrder) {
        List<DraftBlockUpdateRequest> blocks = new ArrayList<>();
        boolean replaced = false;
        for (DraftBlockDto block : draft.blocks()) {
            if ("BODY_PARAGRAPH".equals(block.blockType())
                    && (block.sortOrder() == sortOrder || (!replaced && isBlank(block.content())))) {
                blocks.add(new DraftBlockUpdateRequest("BODY_PARAGRAPH", content, sortOrder));
                replaced = true;
            } else {
                blocks.add(new DraftBlockUpdateRequest(block.blockType(), block.content(), block.sortOrder()));
            }
        }
        if (!replaced) {
            blocks.add(new DraftBlockUpdateRequest("BODY_PARAGRAPH", content, sortOrder));
        }
        return blocks.stream()
                .sorted(Comparator.comparing(DraftBlockUpdateRequest::sortOrder))
                .toList();
    }

    private long currentUserId() {
        CurrentUser currentUser = currentUserProvider == null ? null : currentUserProvider.currentUser();
        return currentUser == null ? 0L : currentUser.id();
    }

    private AiParagraphCandidateException candidateMissing(long candidateId) {
        return new AiParagraphCandidateException(
                "AI_CANDIDATE_NOT_FOUND",
                "Candidate " + candidateId + " was not found"
        );
    }

    private AiParagraphCandidateException targetMissing(Long targetNodeId) {
        return new AiParagraphCandidateException(
                "AI_CANDIDATE_TARGET_NOT_FOUND",
                "Target node " + targetNodeId + " was not found"
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeCandidateContent(String content, String heading) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        String normalized = content.strip();
        String bodyText = stripHeadingPrefix(normalized, heading);
        if (!hasSubstantiveBodyText(bodyText, heading)) {
            throw new IllegalArgumentException("模型只返回了标题，未生成正文，请重试");
        }
        return bodyText;
    }

    private String stripHeadingPrefix(String content, String heading) {
        String normalized = content == null ? "" : content.strip();
        String normalizedHeading = heading == null ? "" : heading.strip();
        if (normalized.isBlank() || normalizedHeading.isBlank()) {
            return normalized;
        }
        String stripped = normalized;
        while (!stripped.isBlank() && stripped.startsWith(normalizedHeading)) {
            stripped = stripped.substring(normalizedHeading.length()).stripLeading();
            while (!stripped.isBlank() && isLeadingHeadingSeparator(stripped.charAt(0))) {
                stripped = stripped.substring(1).stripLeading();
            }
        }
        return stripped.strip();
    }

    private boolean hasSubstantiveBodyText(String content, String heading) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isBlank()) {
            return false;
        }
        return !headingMatches(normalized, heading);
    }

    private boolean isLeadingHeadingSeparator(char value) {
        return value == ':'
                || value == '：'
                || value == '。'
                || value == '.'
                || value == '；'
                || value == ';'
                || value == '，'
                || value == ',';
    }

    private boolean startsWithPunctuation(String value) {
        return value.startsWith(":")
                || value.startsWith(",")
                || value.startsWith(";")
                || value.startsWith("!")
                || value.startsWith("?")
                || value.startsWith(".")
                || value.startsWith(")")
                || value.startsWith("]");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second == null ? "" : second.strip();
    }

    private String digest(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
