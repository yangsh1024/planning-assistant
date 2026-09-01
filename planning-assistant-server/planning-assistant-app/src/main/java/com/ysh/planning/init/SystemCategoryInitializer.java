package com.ysh.planning.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ysh.planning.plan.domain.Category;
import com.ysh.planning.plan.repository.CategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 维护所有用户共享的内置科目，保证新旧部署使用同一份标准分类。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemCategoryInitializer implements ApplicationRunner {

    private static final List<String> SYSTEM_CATEGORIES = List.of("饮食", "交通", "租房", "通信", "其他");

    private final CategoryMapper categoryMapper;

    /**
     * 在应用启动后对齐数据库中的内置分类。
     *
     * <ol>
     *     <li>软删除已废弃的分类。</li>
     *     <li>补齐当前标准分类中缺失的项。</li>
     * </ol>
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        List<Category> existing = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getUserId, 0L));

        // 软删除已废弃科目，避免历史账单失去原有分类。
        for (Category cat : existing) {
            boolean shouldExist = SYSTEM_CATEGORIES.contains(cat.getName());
            if (!shouldExist && !Boolean.TRUE.equals(cat.getIsDeleted())) {
                cat.setIsDeleted(true);
                categoryMapper.updateById(cat);
                log.info("Removed obsolete system category: {}", cat.getName());
            }
        }

        // 补齐缺失的标准科目，确保新用户无需手动创建基础分类。
        java.util.Set<String> existingNames = existing.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .map(Category::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (String name : SYSTEM_CATEGORIES) {
            if (!existingNames.contains(name)) {
                Category cat = new Category();
                cat.setUserId(0L);
                cat.setName(name);
                cat.setIsSystem(true);
                cat.setIsDeleted(false);
                cat.setCreatedAt(LocalDateTime.now());
                categoryMapper.insert(cat);
                log.info("Added system category: {}", name);
            }
        }
    }
}
