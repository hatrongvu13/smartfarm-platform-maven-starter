#!/usr/bin/env python3
from __future__ import annotations
import argparse, sys
from pathlib import Path

IDENTITY=Path('services/smartfarm-identity-service')
SERVICE=IDENTITY/'src/main/java/com/htv/smartfarm/identity/tenant/application/TenantMembershipService.java'
COORD=IDENTITY/'src/main/java/com/htv/smartfarm/identity/authorization/version/AuthorizationMutationCoordinator.java'
DOC=Path('docs/security/dynamic-authorization-phase2a.md')
COORD_TEXT='''package com.htv.smartfarm.identity.authorization.version;

import org.springframework.stereotype.Service;

/** Coordinates version increment and outbox invalidation in the caller transaction. */
@Service
public class AuthorizationMutationCoordinator {
    private final AuthorizationVersionService versions;
    private final AuthorizationInvalidationRecorder invalidations;

    public AuthorizationMutationCoordinator(
            AuthorizationVersionService versions,
            AuthorizationInvalidationRecorder invalidations
    ) {
        this.versions = versions;
        this.invalidations = invalidations;
    }

    public AuthorizationVersionTuple subjectChanged(
            String tenantId, String subjectId, String actorId,
            String correlationId, String reasonCode
    ) {
        AuthorizationVersionTuple value = versions.incrementSubject(tenantId, subjectId);
        invalidations.recordSubjectInvalidation(
                tenantId, subjectId, actorId, correlationId, value, reasonCode);
        return value;
    }

    public long tenantChanged(
            String tenantId, String actorId, String correlationId, String reasonCode
    ) {
        long value = versions.incrementTenant(tenantId);
        invalidations.recordTenantInvalidation(
                tenantId, actorId, correlationId, value, reasonCode);
        return value;
    }
}
'''
DOC_TEXT='''# Dynamic Authorization Phase 2A

Connects effective membership creation and status transitions to subject-version increment
and `identity.authorization.invalidated` recording in the same transaction.

No invalidation is emitted for an idempotent status transition. Remaining Phase 2 work covers
role, role-permission, farm-scope, account-state and permission-catalog mutations.
'''

def parse():
    p=argparse.ArgumentParser(); p.add_argument('--root',type=Path); p.add_argument('--check',action='store_true'); return p.parse_args()
def find_root(given):
    for p in ([given] if given else [Path.cwd(),*Path.cwd().parents]):
        if p and (p/'pom.xml').is_file() and (p/IDENTITY).is_dir(): return p.resolve()
    raise SystemExit('ERROR: repository root not found')
def once(s,a,b,label):
    n=s.count(a)
    if n!=1: raise SystemExit(f'ERROR: expected one {label} anchor, found {n}')
    return s.replace(a,b,1)
def patch(s):
    if 'DYNAMIC_AUTH_PHASE2A' in s: return s
    s=once(s,
      'import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;',
      'import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;\nimport com.htv.smartfarm.identity.authorization.version.AuthorizationMutationCoordinator;',
      'import')
    s=once(s,'    private final IdentityIntegrationEventPublisher events;',
      '    private final IdentityIntegrationEventPublisher events;\n    private AuthorizationMutationCoordinator authorizationMutations;', 'field')
    s=once(s,'        this.events = events;\n    }\n',
      '''        this.events = events;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setAuthorizationMutationCoordinator(AuthorizationMutationCoordinator value) {
        this.authorizationMutations = value;
    }
''','constructor')
    anchor='''        );
        return membership.getId();
'''
    replacement='''        );
        authorizationChanged(tenantId, userId, actorId, correlationId, "MEMBERSHIP_CREATED");
        return membership.getId();
'''
    # This anchor occurs only in inviteUser's event block in the approved source.
    s=once(s,anchor,replacement,'invite tail')
    anchor='''                data
        );
    }

    private String limit(String value, int maximum) {
'''
    replacement='''                data
        );
        authorizationChanged(
                tenantId, userId, actorId, correlationId, "MEMBERSHIP_STATUS_CHANGED");
    }

    // DYNAMIC_AUTH_PHASE2A: null only in legacy unit tests using the old constructor directly.
    private void authorizationChanged(
            String tenantId, String subjectId, String actorId,
            String correlationId, String reasonCode
    ) {
        if (authorizationMutations != null) {
            authorizationMutations.subjectChanged(
                    tenantId, subjectId, actorId, correlationId, reasonCode);
        }
    }

    private String limit(String value, int maximum) {
'''
    return once(s,anchor,replacement,'transition tail')
def norm(s): return s.strip()+'\n'
def main():
    a=parse(); root=find_root(a.root)
    required=[root/SERVICE,root/IDENTITY/'src/main/java/com/htv/smartfarm/identity/authorization/version/AuthorizationVersionService.java',root/IDENTITY/'src/main/java/com/htv/smartfarm/identity/authorization/version/AuthorizationInvalidationRecorder.java']
    for p in required:
        if not p.is_file(): raise SystemExit(f'ERROR: missing {p.relative_to(root)}')
    old=(root/SERVICE).read_text(encoding='utf-8'); new=patch(old); changes=[]
    if new!=old: changes.append(('UPDATE',SERVICE,new))
    for rel,text in ((COORD,COORD_TEXT),(DOC,DOC_TEXT)):
        p=root/rel; desired=norm(text)
        if p.exists() and p.read_text(encoding='utf-8')!=desired: raise SystemExit(f'ERROR: divergent file {rel}')
        if not p.exists(): changes.append(('CREATE',rel,desired))
    print(f'Repository: {root}\nScope: Dynamic Authorization Phase 2A membership invalidation')
    for action,rel,_ in changes: print(f'{action:6} {rel}')
    if not changes: print('KEEP   all Phase 2A files are current')
    if a.check: print(f'CHECK OK: {len(changes)} change(s), no files written'); return 0
    for _,rel,text in changes:
        p=root/rel; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(norm(text),encoding='utf-8')
    print(f'APPLIED: {len(changes)} change(s)\nNOT RUN: Maven build and tests\nNEXT: Phase 2B role and farm-scope invalidation')
    return 0
if __name__=='__main__':
    try: raise SystemExit(main())
    except KeyboardInterrupt: print('Cancelled',file=sys.stderr); raise SystemExit(130)
