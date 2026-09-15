package com.aiot.auth.controller;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class SwaggerAnnotationRuleTest {
    private final JavaClasses classes = new ClassFileImporter().importPackages("com.aiot.auth.controller");

    @Test
    void everyRestControllerMappingMethodShouldBeDocumentedOrHidden() {
        ArchRule rule = methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().areDeclaredInClassesThat().areNotAnnotatedWith(Hidden.class)
                .and().areMetaAnnotatedWith(RequestMapping.class)
                .should().beAnnotatedWith(Operation.class)
                .allowEmptyShould(true);
        rule.check(classes);
    }
}
