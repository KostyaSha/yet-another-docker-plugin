package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad_docker_java.com.google.common.base.Joiner;
import com.github.kostyasha.yad_docker_java.com.google.common.base.Splitter;
import org.apache.commons.collections.CollectionUtils;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Kanstantsin Shautsou
 */
public final class BindUtils {
    private BindUtils() {
    }

    @Nonnull
    public static List<String> splitAndFilterEmpty(@Nonnull String s) {
        return splitAndFilterEmpty(s, "\n");
    }

    @Nonnull
    public static List<String> splitAndFilterEmpty(@Nonnull String s, @Nonnull String separator) {
        return Splitter.on(separator).omitEmptyStrings().splitToList(s);
    }

    /**
     * Helper for converting List<String> -> UI String
     */
    @Nonnull
    public static String joinToStr(List<String> joinList) {
        // with null check
        if (CollectionUtils.isEmpty(joinList)) {
            return "";
        }

        return Joiner.on("\n").join(joinList);
    }
}
