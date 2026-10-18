package com.platform.iam.application.service;

import com.platform.iam.application.command.*;
import com.platform.iam.application.query.*;
import com.platform.iam.domain.event.DomainEvent;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.domain.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platform.iam.application.service.exceptions.*;
import java.util.List;
import java.util.Set;
import com.platform.iam.infrastructure.event.DomainEventPublisher;

/**
 * 角色应用服务
 */
@Service
@Transactional
public class RoleApplicationService {

    private final RoleRepository roleRepository;
    private final RoleQueryService roleQueryService;
    private final DomainEventPublisher domainEventPublisher;

    public RoleApplicationService(RoleRepository roleRepository,
                                  RoleQueryService roleQueryService,
                                  DomainEventPublisher domainEventPublisher) {
        this.roleRepository = roleRepository;
        this.roleQueryService = roleQueryService;
        this.domainEventPublisher = domainEventPublisher;
    }

    public RoleId createRole(CreateRoleCommand cmd) {
        if (roleRepository.existsByCode(cmd.tenantId(), cmd.code())) {
            throw new RoleCodeAlreadyExistsException(cmd.code());
        }

        Role role = Role.create(cmd.tenantId(), cmd.code(), cmd.name(), cmd.description(),
                                cmd.isSystem(), "system");
        if (cmd.permissions() != null && !cmd.permissions().isEmpty()) {
            role.setPermissions(cmd.permissions(), "system");
        }

        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
        return saved.getId();
    }

    public void updateRoleInfo(UpdateRoleInfoCommand cmd) {
        Role role = getRoleOrThrow(cmd.roleId());
        role.updateInfo(cmd.name(), cmd.description(), cmd.updatedBy());
        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
    }

    public void updateRoleCode(UpdateRoleCodeCommand cmd) {
        Role role = getRoleOrThrow(cmd.roleId());
        
        if (roleRepository.existsByCode(role.getTenantId(), cmd.code())) {
            throw new RoleCodeAlreadyExistsException(cmd.code());
        }

        role.updateCode(cmd.code(), cmd.updatedBy());
        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
    }

    public void setRolePermissions(SetRolePermissionsCommand cmd) {
        Role role = getRoleOrThrow(cmd.roleId());
        role.setPermissions(cmd.permissions(), cmd.updatedBy());
        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
    }

    public void addRolePermission(AddRolePermissionCommand cmd) {
        Role role = getRoleOrThrow(cmd.roleId());
        role.addPermission(cmd.permission(), cmd.addedBy());
        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
    }

    public void removeRolePermission(RemoveRolePermissionCommand cmd) {
        Role role = getRoleOrThrow(cmd.roleId());
        role.removePermission(cmd.permission(), cmd.removedBy());
        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
    }

    public void deactivateRole(DeactivateRoleCommand cmd) {
        Role role = getRoleOrThrow(cmd.roleId());
        role.deactivate(cmd.deactivatedBy());
        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
    }

    public void activateRole(ActivateRoleCommand cmd) {
        Role role = getRoleOrThrow(cmd.roleId());
        role.activate(cmd.activatedBy());
        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
    }

    public void deleteRole(DeleteRoleCommand cmd) {
        Role role = getRoleOrThrow(cmd.roleId());
        role.delete(cmd.deletedBy(), cmd.reason());
        Role saved = roleRepository.save(role);
        publishDomainEvents(saved);
    }

    @Transactional(readOnly = true)
    public RoleDetailDto findById(RoleId id) {
        return roleQueryService.findById(id);
    }

    @Transactional(readOnly = true)
    public PageResult<RoleListDto> findPage(RolePageQuery query) {
        return roleQueryService.findPage(query);
    }

    @Transactional(readOnly = true)
    public List<RoleSimpleDto> findByUserId(UserId userId) {
        return roleQueryService.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<RoleSimpleDto> findSystemRoles() {
        return roleQueryService.findSystemRoles();
    }

    @Transactional(readOnly = true)
    public RoleStatisticsDto statistics(TenantId tenantId) {
        return roleQueryService.statistics(tenantId);
    }

    private Role getRoleOrThrow(RoleId id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new RoleNotFoundException(id));
    }

    private void publishDomainEvents(Role role) {
        List<DomainEvent> events = role.getDomainEvents();
        if (!events.isEmpty()) {
            domainEventPublisher.publishAll(events);
            role.clearDomainEvents();
        }
    }
}
