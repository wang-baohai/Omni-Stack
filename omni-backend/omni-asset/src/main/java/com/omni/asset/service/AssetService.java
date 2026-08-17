package com.omni.asset.service;

import com.omni.asset.dto.AssetRequests;
import com.omni.asset.dto.AssetViews;
import com.omni.common.core.result.PageResult;

/**
 * 资产台账服务。
 *
 * @author Omni-Stack Team
 */
public interface AssetService {

    /**
     * 按管理归属分页查询资产。
     *
     * @param query 查询条件
     * @return 资产分页
     */
    PageResult<AssetViews.AssetVO> page(AssetRequests.AssetQuery query);

    /**
     * 查询管理范围内资产详情。
     *
     * @param id 资产 ID
     * @return 资产详情
     */
    AssetViews.AssetVO get(Long id);

    /**
     * 固定按当前使用人分页查询“我的资产”。
     *
     * @param query 查询条件
     * @return 当前用户资产分页
     */
    PageResult<AssetViews.AssetVO> pageMine(AssetRequests.MyAssetQuery query);

    /**
     * 手工创建在库资产。
     *
     * @param request 创建请求
     * @return 新资产
     */
    AssetViews.AssetVO create(AssetRequests.CreateAssetRequest request);

    /**
     * 更新资产基础资料。
     *
     * @param id 资产 ID
     * @param request 更新请求
     * @return 更新后资产
     */
    AssetViews.AssetVO update(Long id, AssetRequests.UpdateAssetRequest request);

    /**
     * 删除未发生业务动作的手工在库资产。
     *
     * @param id 资产 ID
     * @param version 乐观锁版本
     */
    void delete(Long id, Integer version);

    /**
     * 查询资产不可变历史。
     *
     * @param id 资产 ID
     * @param query 分页参数
     * @return 历史分页
     */
    PageResult<AssetViews.HistoryVO> history(Long id, AssetRequests.HistoryQuery query);

    /**
     * 将在库资产分配给员工。
     *
     * @param id 资产 ID
     * @param request 分配请求
     * @return 更新后资产
     */
    AssetViews.AssetVO allocate(Long id, AssetRequests.AllocateRequest request);

    /**
     * 当前使用人确认领用。
     *
     * @param id 资产 ID
     * @param request 版本命令
     * @return 更新后资产
     */
    AssetViews.AssetVO accept(Long id, AssetRequests.VersionCommandRequest request);

    /**
     * 当前使用人退还资产。
     *
     * @param id 资产 ID
     * @param request 版本命令
     * @return 更新后资产
     */
    AssetViews.AssetVO returnAsset(Long id, AssetRequests.VersionCommandRequest request);

    /**
     * 将使用中资产标记为维修中。
     *
     * @param id 资产 ID
     * @param request 版本命令
     * @return 更新后资产
     */
    AssetViews.AssetVO startMaintenance(Long id, AssetRequests.VersionCommandRequest request);

    /**
     * 完成维修并恢复使用中状态。
     *
     * @param id 资产 ID
     * @param request 版本命令
     * @return 更新后资产
     */
    AssetViews.AssetVO completeMaintenance(Long id, AssetRequests.VersionCommandRequest request);
}
