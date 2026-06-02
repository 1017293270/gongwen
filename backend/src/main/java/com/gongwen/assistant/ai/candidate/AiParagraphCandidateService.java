package com.gongwen.assistant.ai.candidate;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.draft.UpdateDraftBlocksRequest;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeDto;
import com.gongwen.assistant.draft.node.DraftNodeFormattingResolver;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
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
import java.util.Set;
import java.util.UUID;

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

    public AiParagraphCandidateService(
            DraftService draftService,
            AiParagraphCandidateRepository candidateRepository,
            DraftNodeRepository draftNodeRepository,
            DraftNodeFormattingResolver formattingResolver,
            CurrentUserProvider currentUserProvider
    ) {
        this.draftService = draftService;
        this.candidateRepository = candidateRepository;
        this.draftNodeRepository = draftNodeRepository;
        this.formattingResolver = formattingResolver;
        this.currentUserProvider = currentUserProvider;
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
        DraftNode updatedNode = draftNodeRepository.updateContent(
                        draft.id(),
                        targetNode.id(),
                        candidate.candidateText(),
                        nodeStatus
                )
                .orElseThrow(() -> targetMissing(candidate.targetNodeId()));
        DraftDetailDto updatedDraft = draftService.updateBlocks(
                draft.id(),
                new UpdateDraftBlocksRequest(upsertParagraphBlock(draft, candidate.candidateText(), updatedNode.sortOrder()))
        );
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
        if (candidate.targetNodeId() == null) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_TARGET_REQUIRED",
                    "Target node is required before accept"
            );
        }
        DraftNode targetNode = draftNodeRepository.findByDraftId(draftId).stream()
                .filter(node -> node.id() == candidate.targetNodeId())
                .findFirst()
                .orElseThrow(() -> targetMissing(candidate.targetNodeId()));
        if (TARGET_BLOCKED_STATUSES.contains(targetNode.status())) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_TARGET_BLOCKED",
                    "Target node cannot be accepted because it is locked or deleted"
            );
        }
        return targetNode;
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
