package com.banryeokkurumi.architecture;

import com.banryeokkurumi.BanryeoKkurumiBackendApplication;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@AnalyzeClasses(packages = "com.banryeokkurumi")
class ArchitectureTest {

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");

    @ArchTest
    static final ArchRule controllersDoNotAccessRepositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat(
                    com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("com.banryeokkurumi..")
                            .and(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith("Repository"))
            );

    @ArchTest
    static final ArchRule domainDoesNotDependOnFrameworks = noClasses()
            .that(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("CatalogProduct")
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("Stock"))
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("CouponPolicy"))
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("CouponCampaign"))
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("OrderProcess"))
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("Shipment"))
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("Review"))
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("PopularityScore")))
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..");

    @Test
    void modules_승인된경계를지킨다() {
        ApplicationModules.of(BanryeoKkurumiBackendApplication.class).verify();
    }
}
