package demo;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 测试用例：各种 Java 空指针场景。
 * 用于验证 java-null-fixer 插件的批量修复功能。
 *
 * 预期：IntelliJ 会在多处报 "may produce NullPointerException" 警告，
 * 插件应该能一键批量修复。
 */
public class UserService {

    // ========== 场景 1: @Nullable 返回值直接调用方法 ==========

    @Nullable
    public String findUserName(int id) {
        if (id == 1) return "zjarlin";
        return null;
    }

    public int getUserNameLength(int id) {
        // ⚠️ findUserName 可能返回 null，直接 .length() 会 NPE
        String name = findUserName(id);
        return name != null ? name.length() : 0;
    }

    // ========== 场景 2: Map.get() 返回 nullable ==========

    public String getConfigValue(Map<String, String> config, String key) {
        // ⚠️ Map.get() 返回 @Nullable，直接 .trim() 会 NPE
        String value = config.get(key);
        return value.trim();
    }

    // ========== 场景 3: 链式调用中的 null ==========

    @Nullable
    public List<String> getUserRoles(int userId) {
        if (userId > 0) return List.of("admin", "user");
        return null;
    }

    public int getRoleCount(int userId) {
        // ⚠️ getUserRoles 可能返回 null，直接 .size() 会 NPE
        return Objects.requireNonNull(getUserRoles(userId)).size();
    }

    // ========== 场景 4: 传递 null 给 @NonNull 参数 ==========

    public void processUser(String name) {
        // ⚠️ name.toUpperCase() 如果 name 是从 nullable 源获取的
        System.out.println(name.toUpperCase());
    }

    public void run() {
        String name = findUserName(999);
        // ⚠️ 传递可能为 null 的值给 processUser
        processUser(name != null ? name : null);
    }

    // ========== 场景 5: 数组/集合元素可能为 null ==========

    @Nullable
    public String findFirst(List<@Nullable String> items) {
        if (items.isEmpty()) return null;
        // ⚠️ items.get(0) 可能是 null
        return items.get(0) != null ? items.get(0).toLowerCase() : null;
    }

    // ========== 场景 6: 三元表达式中的 null ==========

    public String getDisplayName(int id) {
        String name = findUserName(id);
        // ⚠️ name 可能为 null，直接拼接虽然不会 NPE 但 .length() 会
        return (name != null ? name.isEmpty() : false) ? "Anonymous" : name;
    }
}
