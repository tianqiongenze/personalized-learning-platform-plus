package com.platform.tenant.interfaces.rest;

import com.platform.tenant.application.command.ApplyCreditTransferCommand;
import com.platform.tenant.application.command.CreateCreditTransferRuleCommand;
import com.platform.tenant.application.service.CreditTransferService;
import com.platform.tenant.domain.model.CreditTransferRule;
import com.platform.tenant.interfaces.rest.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学分互认控制器
 * 提供跨租户学分互认规则的创建、查询与申请 API
 */
@RestController
@RequestMapping("/credit-transfer")
@RequiredArgsConstructor
@Tag(name = "学分互认", description = "跨租户学分互认规则与申请 API")
public class CreditTransferController {

    private final CreditTransferService creditTransferService;

    // ========== 规则管理 ==========

    @PostMapping("/rules")
    @Operation(summary = "创建学分互认规则", description = "创建源租户课程到目标租户的学分折算规则")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<CreditTransferRuleResponse>> createRule(
            @Valid @RequestBody CreateCreditTransferRuleRequest request) {
        CreditTransferRule rule = creditTransferService.createRule(new CreateCreditTransferRuleCommand(
            request.sourceTenantId(), request.targetTenantId(),
            request.sourceCourseCode(), request.targetCourseCode(),
            request.creditRatio(), request.minScore()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("学分互认规则创建成功", toResponse(rule)));
    }

    @GetMapping("/rules")
    @Operation(summary = "查询学分互认规则", description = "按租户ID查询其作为源或目标的互认规则")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<List<CreditTransferRuleResponse>>> listRules(
            @Parameter(description = "租户ID") @RequestParam String tenantId) {
        List<CreditTransferRuleResponse> rules = creditTransferService.listRules(tenantId).stream()
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    // ========== 学分申请 ==========

    @PostMapping("/apply")
    @Operation(summary = "申请学分互认", description = "按 ACTIVE 规则匹配并折算学分，写入互认记录")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<BigDecimal>> apply(
            @Valid @RequestBody ApplyCreditTransferRequest request) {
        BigDecimal granted = creditTransferService.apply(new ApplyCreditTransferCommand(
            request.studentId(), request.sourceTenantId(),
            request.sourceCourseCode(), request.score()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("学分互认申请成功", granted));
    }

    private CreditTransferRuleResponse toResponse(CreditTransferRule rule) {
        return new CreditTransferRuleResponse(
            rule.getId(),
            rule.getSourceTenantId(),
            rule.getTargetTenantId(),
            rule.getSourceCourseCode(),
            rule.getTargetCourseCode(),
            rule.getCreditRatio(),
            rule.getMinScore(),
            rule.getStatus(),
            rule.getVersion()
        );
    }
}
