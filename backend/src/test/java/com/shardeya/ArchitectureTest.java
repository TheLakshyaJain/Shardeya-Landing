package com.shardeya;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the package-boundary rules in CLAUDE.md § "ArchUnit Rules (enforced in CI)".
 */
class ArchitectureTest {

    private static JavaClasses classes;

    // Tenant-root / platform / auth-plumbing tables legitimately have no org_id
    // column — see each table's own migration comment for why.
    // M2 additions: MeasurementUnit and PlanLimit are platform-managed
    // reference data (like Plan/Role's system rows) — no org_id, no RLS.
    // ImportRow is scoped transitively through import_job_id, the same
    // pattern role_permission already established for role_id.
    // M3: ReceiptSequence DOES have org_id and RLS (it's genuinely
    // tenant-scoped, one gapless counter per org per financial year) --
    // it's exempted only because org_id lives inside its @EmbeddedId
    // (composite PK org_id+fy) rather than as a flat top-level field, which
    // is all this particular check actually looks for. A real orgId-bearing
    // table, not a gap in tenant isolation.
    // M6: StampDutyRate is the same shape as MeasurementUnit -- a global,
    // read-mostly reference table (no org_id, no RLS), shared read access
    // across every tenant.
    // M7: ReportDefinition is the same shape again (M-10 §3 "a report
    // definition is data" -- a shared, org_id-less catalogue row, not
    // tenant-owned). DocumentNumberSequence is exempt for the same reason
    // ReceiptSequence already is -- org_id lives inside its @EmbeddedId
    // (composite PK org_id+doc_type+fy), not as a flat top-level field.
    // M7 second half: NotificationType is the same shape as MeasurementUnit/
    // StampDutyRate -- a global, org_id-less, no-RLS reference catalogue
    // (01-DATA-MODEL.md §8) shared read access across every tenant, not a
    // tenant-owned row.
    private static final Set<String> ENTITIES_WITHOUT_ORG_ID =
            Set.of("Organization", "RefreshToken", "Plan", "OutboxEvent", "MeasurementUnit", "PlanLimit", "ImportRow",
                    "ReceiptSequence", "StampDutyRate", "ReportDefinition", "DocumentNumberSequence", "NotificationType");

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.shardeya");
    }

    // allowEmptyShould(true): in early milestones there may be zero Service/Controller/
    // Entity classes yet. The rule must still be wired up and green, ready to bite the
    // moment a matching class is added — see CLAUDE.md "ArchUnit gotcha".

    @Test
    void builderAndBrokerMustNotDependOnEachOther() {
        // Anchored to the exact top-level "com.shardeya.broker.." package
        // (the Broker-persona side, BR-01..BR-08) rather than the bare
        // "..broker.." wildcard this rule originally used. M6 introduced
        // com.shardeya.builder.broker (B-14, Broker Management -- a
        // Builder-persona feature for managing brokers as a resource, not
        // the Broker persona itself; see CLAUDE.md's own package-structure
        // table, which lists it directly under builder/), and "..broker.."
        // matches ANY package with a "broker" segment -- so it falsely
        // flagged com.shardeya.builder.broker's every dependency on its own
        // sibling classes within the same module as a cross-persona
        // violation (557 false positives on first run). The exact anchor
        // means only the real top-level com.shardeya.broker package is
        // isolated; a Builder-side package that merely contains the word
        // "broker" in its own name is correctly left alone.
        ArchRule builderNotOnBroker = noClasses().that().resideInAPackage("..builder..")
                .should().dependOnClassesThat().resideInAPackage("com.shardeya.broker..")
                .allowEmptyShould(true);
        ArchRule brokerNotOnBuilder = noClasses().that().resideInAPackage("com.shardeya.broker..")
                .should().dependOnClassesThat().resideInAPackage("..builder..")
                .allowEmptyShould(true);

        builderNotOnBroker.check(classes);
        brokerNotOnBuilder.check(classes);
    }

    @Test
    void onlyPlatformMayReferenceTenantContext() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage("..platform..")
                .should().accessClassesThat().haveSimpleName("TenantContext")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void servicesMustNotDependOnControllers() {
        ArchRule rule = noClasses().that().haveSimpleNameEndingWith("Service")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void noEntityMayLackOrgIdExceptPlatformTables() {
        ArchRule rule = classes().that().areAnnotatedWith(Entity.class)
                .and().resideOutsideOfPackage("..platform..")
                .and(new com.tngtech.archunit.base.DescribedPredicate<JavaClass>("is not exempted") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return !ENTITIES_WITHOUT_ORG_ID.contains(javaClass.getSimpleName());
                    }
                })
                .should(haveAFieldNamedOrgId())
                .allowEmptyShould(true);

        rule.check(classes);
    }

    private static ArchCondition<JavaClass> haveAFieldNamedOrgId() {
        return new ArchCondition<>("have a field named 'orgId'") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean hasOrgId = javaClass.getFields().stream()
                        .anyMatch(field -> field.getName().equals("orgId"));
                if (!hasOrgId) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getFullName() + " is an @Entity outside platform.* with no 'orgId' field"));
                }
            }
        };
    }
}
