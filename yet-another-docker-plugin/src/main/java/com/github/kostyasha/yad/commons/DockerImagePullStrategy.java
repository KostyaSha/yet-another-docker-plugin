package com.github.kostyasha.yad.commons;

/**
 * Pulling strategies
 *
 * @author Kanstantsin Shautsou
 */
public enum DockerImagePullStrategy {

    PULL_ALWAYS("Pull always") {
        @Override
        public boolean pullIfNotExists(String imageName) {
            return true;
        }

        @Override
        public boolean pullIfExists(String imageName) {
            return true;
        }
    },
    PULL_ONCE("Pull once") {
        @Override
        public boolean pullIfExists(String imageName) {
            return false;
        }

        @Override
        public boolean pullIfNotExists(String imageName) {
            return true;
        }
    },
    PULL_LATEST("Pull once but always update latest") {
        @Override
        public boolean pullIfNotExists(String imageName) {
            return true;
        }

        @Override
        public boolean pullIfExists(String imageName) {
            return imageName.endsWith(":latest");
        }
    },
    PULL_NEVER("Pull never") {
        @Override
        public boolean pullIfNotExists(String imageName) {
            return false;
        }

        @Override
        public boolean pullIfExists(String imageName) {
            return false;
        }
    };

    private final String description;

    DockerImagePullStrategy(String description) {
        this.description = description;
    }

    public String getDescription() {
        //TODO add {@link #Locale.class}?
        return description;
    }

    public abstract boolean pullIfNotExists(String imageName);

    public abstract boolean pullIfExists(String imageName);
}
