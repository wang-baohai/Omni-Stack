package com.omni.procurement.fixture;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 守卫测试子进程驱动（仅测试用）：以真实 OS 进程获取目标锁并保持，用于双进程互斥验证。
 *
 * <p>用法：{@code FixtureGuardDriver lock <stateRoot> <targetKey> <holdMillis>}。
 * 成功持锁并保持后退出 0；获取失败（互斥生效）退出 3；参数错误退出 2。</p>
 *
 * @author Omni-Stack WP-005 Repair
 */
public final class FixtureGuardDriver {

    private FixtureGuardDriver() {
    }

    /**
     * 入口：lock 模式获取 FixtureSupport.TargetLock 并保持指定毫秒后释放退出。
     *
     * @param args lock stateRoot targetKey holdMillis
     * @throws Exception 等待中断
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 4 || !"lock".equals(args[0])) {
            System.err.println("usage: FixtureGuardDriver lock <stateRoot> <targetKey> <holdMillis>");
            System.exit(2);
            return;
        }
        Path stateRoot = Path.of(args[1]);
        String targetKey = args[2];
        long holdMillis = Long.parseLong(args[3]);
        FixtureSupport.TargetLock lock = FixtureSupport.TargetLock.tryAcquire(stateRoot, targetKey);
        if (lock == null) {
            System.err.println("LOCK-REJECTED");
            System.exit(3);
            return;
        }
        System.out.println("LOCK-ACQUIRED");
        System.out.flush();
        TimeUnit.MILLISECONDS.sleep(holdMillis);
        lock.close();
        System.out.println("LOCK-RELEASED");
        System.exit(0);
    }
}
