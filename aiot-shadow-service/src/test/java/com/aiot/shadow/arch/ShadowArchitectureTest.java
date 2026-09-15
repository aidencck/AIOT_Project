package com.aiot.shadow.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.CacheMode;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.aiot.shadow",
        importOptions = ImportOption.DoNotIncludeTests.class,
        cacheMode = CacheMode.PER_CLASS
)
class ShadowArchitectureTest {

    @ArchTest
    static final ArchRule listeners_should_not_depend_on_controller =
            noClasses().that().resideInAnyPackage("..listener..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controller..");

    @ArchTest
    static final ArchRule controllers_should_not_depend_on_persistence =
            noClasses().that().resideInAnyPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..mapper..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule shadow_should_not_depend_on_other_services =
            noClasses().should().dependOnClassesThat().resideInAnyPackage(
                    "com.aiot.auth..",
                    "com.aiot.device..",
                    "com.aiot.home..",
                    "com.aiot.rule..",
                    "com.aiot.mqtt..",
                    "com.aiot.gateway.."
            );
}
