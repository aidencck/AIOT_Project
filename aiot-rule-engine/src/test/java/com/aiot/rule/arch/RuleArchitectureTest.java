package com.aiot.rule.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.CacheMode;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.aiot.rule",
        importOptions = ImportOption.DoNotIncludeTests.class,
        cacheMode = CacheMode.PER_CLASS
)
class RuleArchitectureTest {

    @ArchTest
    static final ArchRule controllers_should_not_depend_on_persistence =
            noClasses().that().resideInAnyPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..mapper..");

    @ArchTest
    static final ArchRule services_should_not_depend_on_controller =
            noClasses().that().resideInAnyPackage("..service..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controller..");

    @ArchTest
    static final ArchRule only_repositories_should_depend_on_mapper =
            noClasses().that().resideOutsideOfPackage("..repository..")
                    .should().dependOnClassesThat().resideInAnyPackage("..mapper..");

    @ArchTest
    static final ArchRule rule_should_not_depend_on_other_services =
            noClasses().should().dependOnClassesThat().resideInAnyPackage(
                    "com.aiot.auth..",
                    "com.aiot.device..",
                    "com.aiot.home..",
                    "com.aiot.shadow..",
                    "com.aiot.mqtt..",
                    "com.aiot.gateway.."
            );
}
