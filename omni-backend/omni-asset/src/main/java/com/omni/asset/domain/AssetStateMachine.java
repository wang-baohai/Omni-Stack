package com.omni.asset.domain;

import com.omni.common.core.result.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 资产生命周期状态机。
 *
 * @author Omni-Stack Team
 */
public final class AssetStateMachine {

    /** 在库。 */
    public static final String IN_STOCK = "IN_STOCK";

    /** 已分配待领用。 */
    public static final String ALLOCATED = "ALLOCATED";

    /** 使用中。 */
    public static final String IN_USE = "IN_USE";

    /** 维修中。 */
    public static final String MAINTENANCE = "MAINTENANCE";

    /** 调拨中。 */
    public static final String TRANSFER = "TRANSFER";

    /** 处置审批中。 */
    public static final String DISPOSAL_PENDING = "DISPOSAL_PENDING";

    /** 已丢弃。 */
    public static final String DISPOSED = "DISPOSED";

    /** 已报废。 */
    public static final String SCRAPPED = "SCRAPPED";

    private static final Set<String> STATUSES = Set.of(
            IN_STOCK, ALLOCATED, IN_USE, MAINTENANCE,
            TRANSFER, DISPOSAL_PENDING, DISPOSED, SCRAPPED);

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            IN_STOCK, Set.of(ALLOCATED, TRANSFER, DISPOSAL_PENDING),
            ALLOCATED, Set.of(IN_USE, IN_STOCK, TRANSFER, DISPOSAL_PENDING),
            IN_USE, Set.of(IN_STOCK, MAINTENANCE, TRANSFER, DISPOSAL_PENDING),
            MAINTENANCE, Set.of(IN_USE),
            TRANSFER, Set.of(IN_STOCK, ALLOCATED, IN_USE),
            DISPOSAL_PENDING, Set.of(IN_STOCK, ALLOCATED, IN_USE, DISPOSED, SCRAPPED),
            DISPOSED, Set.of(),
            SCRAPPED, Set.of());

    private AssetStateMachine() {
    }

    /**
     * 校验状态值属于受支持集合。
     *
     * @param status 状态
     */
    public static void requireKnown(String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException(409, "不支持的资产状态: " + status);
        }
    }

    /**
     * 校验生命周期迁移合法。
     *
     * @param fromStatus 变更前状态
     * @param toStatus 变更后状态
     */
    public static void requireTransition(String fromStatus, String toStatus) {
        requireKnown(fromStatus);
        requireKnown(toStatus);
        if (!TRANSITIONS.get(fromStatus).contains(toStatus)) {
            throw new BusinessException(409,
                    "资产状态不允许从 " + fromStatus + " 变更为 " + toStatus);
        }
    }

    /**
     * 判断状态是否为不可逆终态。
     *
     * @param status 状态
     * @return 是否终态
     */
    public static boolean isTerminal(String status) {
        requireKnown(status);
        return DISPOSED.equals(status) || SCRAPPED.equals(status);
    }
}
