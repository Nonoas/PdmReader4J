dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://mirrors.huaweicloud.com/repository/maven/")
        maven("https://maven.aliyun.com/repository/central")
    }
}
rootProject.name = "PDMReader4J"
