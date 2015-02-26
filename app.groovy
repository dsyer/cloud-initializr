package app

import io.spring.initializr.*
import io.spring.initializr.web.*
import io.spring.initializr.support.*
import io.spring.initializr.InitializrMetadata.BootVersion
import io.spring.initializr.InitializrMetadata.DependencyGroup
import io.spring.initializr.InitializrMetadata.Dependency
import groovy.util.logging.Slf4j

@Grab('io.spring.initalizr:initializr:1.0.0.BUILD-SNAPSHOT')
@Slf4j
class InitializerService {

  @Autowired
  ProjectGenerationMetricsListener metricsListener

  @Autowired
  CloudProperties cloud

  @Autowired
  InitializrMetadata metadata

  @Bean
  InitializrMetadataCustomizer initializrMetadataCustomizer() {
    new InitializrMetadataCustomizer() {
      @Override
      void customize(InitializrMetadata metadata) {
        metadata.bootVersions.clear()
        metadata.bootVersions.addAll(cloud.versions)
        metadata.dependencies.each { group ->
          group.content.each { dependency ->
            translateRange(dependency, cloud)
          }
        }
        metadata.defaults.description = 'Demo project for Spring Cloud'
        log.info("Adding cloud dependencies")
        cloud.dependencies.reverse().each { group -> 
          metadata.dependencies.add(0, group)
        }
      }
    }
  }

  private void translateRange(Dependency dependency, CloudProperties cloud) {
    if (dependency.versionRange) {
      /*
       The logic here only tries to determine a lower bound for the
       version range, which is adequate in all cases we currently
       support, but might need to be more sophisticated later
      */
      String lowerVersion = InitializrMetadata.getDefault(cloud.versions)  
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
  ProjectGenerator projectGenerator() {
    def generator = new ProjectGenerator() {
      protected Map initializeModel(ProjectRequest request) {
        Map map = super.initializeModel(request)
        map.put('springCloudVersion', request.bootVersion ?: InitializrMetadata.getDefault(cloud.versions))
        map.put('bootVersion', InitializrMetadata.getDefault(cloud.bootVersions))
        map
      }
    }
    generator.listeners << metricsListener
    generator
  }
  
}

@ConfigurationProperties('cloud')
class CloudProperties {
  /**
   * The versions of Spring Cloud supported by this service
   */
  final List<BootVersion> versions = []
  /**
   * The versions of Spring Boot supported by various versions of Spring Cloud
   */
  final List<CloudBootVersion> bootVersions = []
  /**
   * The additional dependencies to be added
   */
  final List<DependencyGroup> dependencies = []
  /**
   * The group ID for Spring Cloud dependencies
   */
  String groupId
  /**
   * The default starter dependency if none are provided
   */
  String artifactId
}

class CloudBootVersion extends BootVersion {
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
      String springCloudVersion
      protected addDefaultDependency() {
		def root = new InitializrMetadata.Dependency()
		root.id = cloud.artifactId
        root.groupId = cloud.groupId
        root.artifactId = cloud.artifactId
		resolvedDependencies << root
      }
    }
    metadataProvider.get().initializeProjectRequest(request)
    request
  }

}