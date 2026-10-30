package com.banryeokkurumi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.banryeokkurumi.BanryeoKkurumiBackendApplication;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@AnalyzeClasses(packages = "com.banryeokkurumi")
class ArchitectureTest {

  @ArchTest
  static final ArchRule noFieldInjection =
      noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");

  @ArchTest
  static final ArchRule controllersDoNotAccessRepositories =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat(
              com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(
                      "com.banryeokkurumi..")
                  .and(
                      com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith(
                          "Repository")));

  // java.lang.SecurityException belongs to the JVM security manager, not to web authorization.
  @ArchTest
  static final ArchRule noJvmSecurityExceptionForAuthorization =
      noClasses()
          .that()
          .resideInAPackage("com.banryeokkurumi..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.lang.SecurityException");

  // Ownership is checked in Application Services so every caller gets the same rule.
  @ArchTest
  static final ArchRule controllersDoNotCheckOwnership =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.security.access.AccessDeniedException");

  @ArchTest
  static final ArchRule domainDoesNotDependOnFrameworks =
      noClasses()
          .that(
              com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("CatalogProduct")
                  .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("Stock"))
                  .or(
                      com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName(
                          "CouponPolicy"))
                  .or(
                      com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName(
                          "CouponCampaign"))
                  .or(
                      com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName(
                          "OrderProcess"))
                  .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("Shipment"))
                  .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName("Review"))
                  .or(
                      com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName(
                          "PopularityScore")))
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.persistence..");

  @Test
  void modules_승인된경계를지킨다() {
    ApplicationModules.of(BanryeoKkurumiBackendApplication.class).verify();
  }
}
