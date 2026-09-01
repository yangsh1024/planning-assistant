package com.ysh.planning.user.domain;

import java.util.Set;

/**
 * 应用内置头像的唯一来源。数据库只保存其中的 key，客户端负责映射本地图片资源。
 */
public final class AvatarCatalog {

    public static final String DEFAULT_AVATAR_KEY = "cat-orange";

    private static final Set<String> SUPPORTED_AVATAR_KEYS = Set.of(
            DEFAULT_AVATAR_KEY,
            "cat-black",
            "cat-calico",
            "cat-gray",
            "cat-white",
            "cat-ragdoll",
            "cat-tuxedo",
            "cat-siamese",
            "cat-tabby"
    );

    private AvatarCatalog() {
    }

    /**
     * 判断头像键是否属于应用内置头像集合。
     *
     * @param avatarKey 待校验的头像键
     * @return 键存在于内置头像集合时为 {@code true}
     */
    public static boolean isSupported(String avatarKey) {
        return SUPPORTED_AVATAR_KEYS.contains(avatarKey);
    }

}
