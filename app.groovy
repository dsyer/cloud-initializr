package app

import io.spring.initializr.generator.*
import io.spring.initializr.metadata.*
import io.spring.initializr.util.*
import io.spring.initializr.web.MainController
import groovy.util.logging.Slf4j

import org.springframework.core.io.Resource

@Grab('io.spring.initalizr:initializr:1.0.0.BUILD-SNAPSHOT')
@Slf4j
class InitializerService {

  @Autowired
  CloudProperties cloud

  @Autowired
  InitializrProperties properties

  @Bean
  InitializrMetadata initializrMetadata() {
    InitializrMetadataBuilder builder = InitializrMetadataBuilder.create()
    if (cloud.initialMetadata) {
        builder.withInitializrMetadata(cloud.initialMetadata)
    }
    builder.withInitializrProperties(properties)
    builder.withCustomizer { metadata ->
      metadata.bootVersions.content.clear()
      metadata.bootVersions.content.addAll(cloud.versions)

      metadata.dependencies.content.each { group ->
        group.content.each { dependency ->
          translateRange(dependency, cloud)
        }
      }
      def cloudGroup = metadata.dependencies.content.find { 'Cloud'.equals(it.name) }

      metadata.dependencies.content.remove(cloudGroup)
      metadata.dependencies.content.add(0, cloudGroup)
	}
    builder.build()
  }

  private void translateRange(Dependency dependency, CloudProperties cloud) {
    if (dependency.versionRange) {
      /*
       The logic here only tries to determine a lower bound for the
       version range, which is adequate in all cases we currently
       support, but might need to be more sophisticated later
      */
      String lowerVersion = defaultId(cloud.versions)
      VersionRange bootRange = VersionRange.parse(dependency.versionRange)
      cloud.bootVersions.each { bootVersion ->
        if (bootRange.match(Version.parse(bootVersion.id))) {
          VersionRange cloudRange = VersionRange.parse(bootVersion.cloudVersionRange)
          lowerVersion = cloudRange.lowerVersion
        }
      }
      dependency.versionRange = lowerVersion
    }
  }

  @Bean
  InitializrMetadataProvider initializrMetadataProvider(InitializrMetadata metadata) {
    new InitializrMetadataProvider() {
      @Override
      InitializrMetadata get() {
        metadata
      }
    }
  }

  @Bean
  ProjectGenerator projectGenerator(ProjectGenerationMetricsListener metricsListener) {
    def generator = new ProjectGenerator() {
      protected Map initializeModel(ProjectRequest request) {
        Map map = super.initializeModel(request)
        map.put('springCloudVersion', request.bootVersion ?: defaultId(cloud.versions))
        map.put('bootVersion', defaultId(cloud.bootVersions))
        map
      }
    }
    generator.listeners << metricsListener
    generator
  }

  private static String defaultId(elements) {
    elements.find{it.default}.id
  }

}

@ConfigurationProperties('cloud')
class CloudProperties {
  /**
   *  When specified, this resource is used to initialize the meta-data used by this instance.
   */
  Resource initialMetadata
  /**
   * The versions of Spring Cloud supported by this service
   */
  final List<DefaultMetadataElement> versions = []
  /**
   * The versions of Spring Boot supported by various versions of Spring Cloud
   */
  final List<CloudBootVersion> bootVersions = []
  /**
   * The group ID for Spring Cloud dependencies
   */
  String groupId
  /**
   * The default starter dependency if none are provided
   */
  String artifactId
}

class CloudBootVersion extends DefaultMetadataElement {
  /**
   * The range of versions of Spring Cloud supporting this Boot version
   */
  String cloudVersionRange
}

@Controller
class CloudController extends MainController {

  @Autowired
  CloudProperties cloud

  @ModelAttribute
  @Override
  ProjectRequest projectRequest() {
    def request = new ProjectRequest() {

      protected addDefaultDependency() {
        def root = new Dependency()
        root.id = cloud.artifactId
        root.groupId = cloud.groupId
        root.artifactId = cloud.artifactId
        resolvedDependencies << root
      }
    }
    request.initialize(metadataProvider.get())
    request
  }

}