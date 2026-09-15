package com.aiot.auth.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.CacheMode;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.aiot.auth",
        importOptions = ImportOption.DoNotIncludeTests.class,
        cacheMode = CacheMode.PER_CLASS
)
class AuthArchitectureTest {

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
                    .should().dependOnClassesThat().resideInAnyPackage("com.aiot.auth.mapper..");

    @ArchTest
    static final ArchRule auth_should_not_depend_on_other_services =
            noClasses().should().dependOnClassesThat().resideInAnyPackage(
                    "com.aiot.device..",
                    "com.aiot.home..",
                    "com.aiot.rule..",
                    "com.aiot.shadow..",
                    "com.aiot.mqtt..",
                    "com.aiot.gateway.."
            );
}
