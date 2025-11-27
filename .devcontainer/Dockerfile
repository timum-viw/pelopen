FROM amazoncorretto:17

# Install system dependencies
RUN yum update -y && \
    yum install -y \
    wget \
    unzip \
    git \
    curl \
    tar \
    gzip \
    && yum clean all

# Install GitHub CLI (download binary directly from GitHub releases)
RUN cd /tmp && \
    GH_VERSION=$(curl -s https://api.github.com/repos/cli/cli/releases/latest | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/' | sed 's/v//') && \
    wget -q https://github.com/cli/cli/releases/download/v${GH_VERSION}/gh_${GH_VERSION}_linux_amd64.tar.gz && \
    tar -xzf gh_${GH_VERSION}_linux_amd64.tar.gz && \
    mv gh_${GH_VERSION}_linux_amd64/bin/gh /usr/local/bin/ && \
    rm -rf gh_${GH_VERSION}_linux_amd64* && \
    chmod +x /usr/local/bin/gh

# Install Gradle
ENV GRADLE_VERSION=7.5
ENV GRADLE_HOME=/opt/gradle
ENV PATH=${PATH}:${GRADLE_HOME}/bin
RUN mkdir -p ${GRADLE_HOME} && \
    cd /tmp && \
    wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip -q gradle-${GRADLE_VERSION}-bin.zip && \
    mv gradle-${GRADLE_VERSION}/* ${GRADLE_HOME}/ && \
    rm -rf gradle-${GRADLE_VERSION} gradle-${GRADLE_VERSION}-bin.zip

# Set up Android SDK
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/bin

# Create Android SDK directory and install command line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    cd ${ANDROID_HOME} && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip -q commandlinetools-linux-9477386_latest.zip && \
    mv cmdline-tools latest && \
    mv latest cmdline-tools/ && \
    rm commandlinetools-linux-9477386_latest.zip

# Accept licenses and install Android SDK components
RUN yes | ${ANDROID_HOME}/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} --licenses && \
    ${ANDROID_HOME}/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} \
    "platform-tools" \
    "platforms;android-30" \
    "platforms;android-33" \
    "platforms;android-34" \
    "build-tools;33.0.1"

# Set Java home
ENV JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto

WORKDIR /workspaces/pelopen


