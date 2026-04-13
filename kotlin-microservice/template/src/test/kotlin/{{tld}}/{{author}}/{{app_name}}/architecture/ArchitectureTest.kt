package {{tld}}.{{author}}.{{app_name}}.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

@AnalyzeClasses(
    packages = ["{{tld}}.{{author}}.{{app_name}}"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureTest {

    @ArchTest
    val layers: ArchRule = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Commons").definedBy("..commons..")
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..application..")
        .layer("Infrastructure").definedBy("..infrastructure..")
        .layer("Main").definedBy("..main..")
        .whereLayer("Main").mayNotBeAccessedByAnyLayer()
        .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Main")
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Main", "Infrastructure")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Main", "Infrastructure", "Application")
        .whereLayer("Commons").mayOnlyBeAccessedByLayers("Main", "Infrastructure", "Application", "Domain")

    @ArchTest
    val commonsHasNoProjectInternalDependencies: ArchRule = noClasses()
        .that().resideInAPackage("..commons..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "..domain..",
            "..application..",
            "..infrastructure..",
            "..main..",
        )

    @ArchTest
    val domainIsFrameworkFree: ArchRule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "com.mongodb..",
            "org.springframework.data..",
            "org.bson..",
            "io.mongock..",
            "software.amazon.awssdk..",
        )

    @ArchTest
    val restControllersLiveInWebAdapter: ArchRule = classes()
        .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
        .should().resideInAPackage("..infrastructure.adapters.in.web..")
        .allowEmptyShould(true)

    @ArchTest
    val jpaEntitiesLiveInOutboundAdapter: ArchRule = classes()
        .that().areAnnotatedWith("jakarta.persistence.Entity")
        .should().resideInAPackage("..infrastructure.adapters.out..")
        .allowEmptyShould(true)

    @ArchTest
    val springRepositoriesLiveInOutboundAdapter: ArchRule = classes()
        .that().areAnnotatedWith("org.springframework.stereotype.Repository")
        .should().resideInAPackage("..infrastructure.adapters.out..")
        .allowEmptyShould(true)

    @ArchTest
    val outboundPortsLiveInDomain: ArchRule = classes()
        .that().areInterfaces()
        .and().haveSimpleNameEndingWith("Repository")
        .and().areNotAssignableTo("org.springframework.data.repository.Repository")
        .and().resideInAPackage("{{tld}}.{{author}}.{{app_name}}..")
        .should().resideInAPackage("..domain..")
        .allowEmptyShould(true)

    @ArchTest
    val noLayerCycles: ArchRule = slices()
        .matching("{{tld}}.{{author}}.{{app_name}}.(*)..")
        .should().beFreeOfCycles()
}
