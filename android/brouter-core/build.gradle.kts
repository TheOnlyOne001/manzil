plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
sourceSets.main {
    java.srcDirs(
        "../../third_party/brouter/brouter-core/src/main/java",
        "../../third_party/brouter/brouter-mapaccess/src/main/java",
        "../../third_party/brouter/brouter-expressions/src/main/java",
        "../../third_party/brouter/brouter-codec/src/main/java",
        "../../third_party/brouter/brouter-util/src/main/java"
    )
}
