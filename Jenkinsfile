#!groovy
package com.plangrid

/**
 * Devtools team created PlangridStage groovy function uses env.GIT_BRANCH and env.PROJECT_NAME
 * Need to set these variables prior to use PlangridStage
 * (https://github.com/plangrid/plangrid-build-tools/blob/dev/vars/PlangridStage.groovy)
 */
env.GIT_BRANCH = env.BRANCH_NAME
env.PROJECT_NAME = 'svgtools'

def nodes = [:]

nodes['Build Project'] = {
    node() {
        def buildType = getBuildTypeForBranchName(env.BRANCH_NAME)
        println("Derived buildType $buildType from branch name ${env.BRANCH_NAME}")

        try {
            PlangridStage("SCM") {
                // Cancel previous builds for this job
                if (buildType == BuildType.PR) {
                    cancelPreviousBuildsForJob()
                }

                checkout scm

                env.GIT_COMMIT = sh(returnStdout: true, script: "git rev-parse HEAD").trim()

                if (!env.DOCKER_IMAGE_BASE) {
                    env.DOCKER_IMAGE_BASE = "${env.PROJECT_NAME}:${env.GIT_COMMIT}"
                }
            }

            currentBuild.result = "SUCCESS"

            PlangridStage('Build Docker') {
                retry(2) {
                    sh 'VERBOSE=1 /opt/plangrid/build-tools/bin/build-docker'
                }
                CURRENT_DIR = sh(returnStdout: true, script: "pwd").trim()

                DOCKER_ID = sh(returnStdout: true, script: "docker run \
                    -e BRANCH_NAME=${env.BRANCH_NAME} \
                    -e BUILD_NUMBER=${env.BUILD_NUMBER} \
                    -e DANGER_GITHUB_API_TOKEN=${env.IOS_GITHUB_API_TOKEN} \
                    -e JENKINS_URL=${env.JENKINS_URL} \
                    -e CHANGE_URL=${env.CHANGE_URL} \
                    -e CHANGE_ID=${env.CHANGE_ID} \
                    -e ARTIFACTORY_USER \
                    -e ARTIFACTORY_PASSWORD \
                    -itd \
                    -v /var/lib/jenkins/gradlecache:/gradle_home/caches/ \
                    -v " + "${CURRENT_DIR}:/build " + "${env.PROJECT_NAME}:${env.GIT_COMMIT}").trim()
            }

            PlangridStage("Build") {
                // Sometimes the gradle build command is flaky so we retry
                retry(2) {
                    sh("docker exec ${DOCKER_ID} bash -c 'cd build ; ./gradlew build'")
                }
            }
            PlangridStage("Publish") {
                if (buildType == BuildType.SNAPSHOT) {
                    sh("docker exec ${DOCKER_ID} bash -c 'cd build ; ./gradlew publishSnapshot'")
                }
            }
        } finally {
            // Success or failure, always send notifications
            env.RESULT = currentBuild.result
            env.START_TIME = currentBuild.startTimeInMillis
            env.DESCRIPTION = currentBuild.description ?: ""

            dir("${env.WORKSPACE}@tmp") { // for some reason the WCP doesn't delete @tmp folders, do it manually
                deleteDir()
            }
        }

    } /* End node() */
}

throttle(['BigMem']) {
    parallel nodes
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/* This method cancels ongoing builds previous to a given job. */
@NonCPS
def cancelPreviousBuildsForJob() {
    def jobName = env.JOB_NAME
    def buildNumber = env.BUILD_NUMBER.toInteger()
    def currentJob = Jenkins.instance.getItemByFullName(jobName)

    for (def build : currentJob.builds) {
        if (build.isBuilding() && build.number.toInteger() != buildNumber) {
            build.doStop()
        }
    }
}

interface BuildType {
    public static final String SNAPSHOT = "SNAPSHOT"
    public static final String PR = "PR"
    public static final String RELEASE = "RELEASE"
    public static final String UNKNOWN = "UNKNOWN"
}

static getBuildTypeForBranchName(String branchName) {
    return BuildType.SNAPSHOT
    // We'll turn these back on once we get the default snapshot build working
    /*
    if (branchName == 'master') {
        return BuildType.SNAPSHOT
    } else if (branchName.startsWith('PR')) {
        return BuildType.PR
    } else if (branchName.startsWith('release')) {
        return BuildType.RELEASE
    } else {
        return BuildType.UNKNOWN
    }
    */
}

